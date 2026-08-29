/*
 * Copyright (c) 2026, WSO2 LLC. (https://www.wso2.com).
 *
 * WSO2 LLC. licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.wso2.dpdp.accelerator.identity.extensions.consent;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.wso2.carbon.consent.mgt.core.PrivilegedConsentManager;
import org.wso2.carbon.consent.mgt.core.listener.AbstractConsentManagementListener;
import org.wso2.carbon.consent.mgt.core.model.ConsentAuthorization;
import org.wso2.carbon.consent.mgt.core.model.Receipt;
import org.wso2.carbon.consent.mgt.core.model.ReceiptInput;
import org.wso2.carbon.consent.mgt.core.model.ReceiptUpdateInput;
import org.wso2.carbon.context.PrivilegedCarbonContext;
import org.wso2.dpdp.accelerator.consent.extensions.service.constants.ConsentHistoryServiceConstants.ActionType;
import org.wso2.dpdp.accelerator.identity.extensions.internal.DPDPIdentityExtensionDataHolder;

import java.sql.Timestamp;
import java.util.List;

/**
 * Captures consent lifecycle events into {@code DPDP_CONSENT_STATUS_AUDIT}/{@code DPDP_CONSENT_HISTORY}.
 * Snapshots are captured on the {@code post*} hooks, tagged directly with the action that just ran -
 * each one means exactly what its label says, since it shows the state that action actually
 * produced.
 *
 * <p>Also maintains {@code DPDP_CONSENT_EXPIRY_TRACKER} (tracked/untracked alongside every
 * status-audit write below) and, on every {@code pre*} hook, checks whether this consent already
 * lapsed before the scheduled {@link org.wso2.dpdp.accelerator.identity.extensions.consent.scheduler.ConsentExpiryJob}
 * caught it - see {@link DPDPConsentExpiryReconciler}.
 *
 * <p>A capture failure must never block the consent mutation itself, so every hook swallows and
 * logs its own exceptions rather than propagating them - propagating would abort the real
 * business operation the hook fired for.
 */
public class DPDPConsentHistoryListener extends AbstractConsentManagementListener {

    private static final Log LOG = LogFactory.getLog(DPDPConsentHistoryListener.class);
    private static final int LISTENER_ORDER_ID = 100;
    private static final String ACTIVE_STATUS = "ACTIVE";
    private static final String PENDING_STATUS = "PENDING";
    private static final String REVOKED_STATUS = "REVOKED";
    private static final String DELETED_STATUS = "DELETED";

    private static final ThreadLocal<String> PREVIOUS_STATUS = new ThreadLocal<>();

    @Override
    public int getDefaultOrderId() {

        return LISTENER_ORDER_ID;
    }

    @Override
    public boolean isEnable() {

        return DPDPIdentityExtensionDataHolder.getInstance().getConfigurationService().isConsentHistoryEnabled();
    }

    @Override
    public void postAddConsent(ReceiptInput receiptInput, String tenantDomain) {

        // receiptInput.getState() is null whenever the caller's request omits "state" - the REST
        // layer's documented ACTIVE default is applied at the DB/schema level, never written back
        // onto this object. Resolved from the request's own shape rather than a live read: a live
        // read (getReceiptWithExtendedSchema) applies resolveConsentState()'s dynamic expiry check,
        // which would misreport a consent created with an already-past expiryTime as EXPIRED
        // instead of its true, as-persisted ACTIVE/PENDING creation state. REJECTED never hits this
        // fallback - it requires an explicit, non-null "state" in the request.
        String currentStatus = receiptInput.getState();
        if (currentStatus == null) {
            boolean hasAuthorizations = receiptInput.getAuthorizations() != null
                    && !receiptInput.getAuthorizations().isEmpty();
            currentStatus = hasAuthorizations ? PENDING_STATUS : ACTIVE_STATUS;
        }
        recordStatusAudit(receiptInput.getConsentReceiptId(), tenantDomain, null, currentStatus, ActionType.CREATE);
        captureSnapshot(receiptInput.getConsentReceiptId(), tenantDomain, ActionType.CREATE);
        trackExpiry(receiptInput.getConsentReceiptId(), tenantDomain, receiptInput.getExpiryTime());
    }

    @Override
    public void preUpdateConsent(ReceiptUpdateInput updateInput, String tenantDomain) {

        DPDPConsentExpiryReconciler.expireConsentIfDue(tenantDomain, updateInput.getConsentReceiptId());
        capturePreviousStatus(updateInput.getConsentReceiptId());
    }

    @Override
    public void postUpdateConsent(ReceiptUpdateInput updateInput, String tenantDomain) {

        // ReceiptUpdateInput carries no state field - update never changes the consent's
        // lifecycle status, so previous and current are the same captured pre-mutation value.
        String previousStatus = takePreviousStatus();
        recordStatusAudit(updateInput.getConsentReceiptId(), tenantDomain, previousStatus, previousStatus,
                ActionType.UPDATE);
        captureSnapshot(updateInput.getConsentReceiptId(), tenantDomain, ActionType.UPDATE);

        // isClearExpiry() and getExpiryTime() are the two distinct signals an update can carry -
        // neither set means this update didn't touch expiry at all, so the tracker is left alone.
        if (updateInput.isClearExpiry()) {
            untrackExpiry(updateInput.getConsentReceiptId(), tenantDomain);
        } else if (updateInput.getExpiryTime() != null) {
            trackExpiry(updateInput.getConsentReceiptId(), tenantDomain, updateInput.getExpiryTime());
        }
    }

    @Override
    public void preRevokeConsent(String receiptId, String tenantDomain) {

        DPDPConsentExpiryReconciler.expireConsentIfDue(tenantDomain, receiptId);
        capturePreviousStatus(receiptId);
    }

    @Override
    public void postRevokeConsent(String receiptId, String tenantDomain) {

        recordStatusAudit(receiptId, tenantDomain, takePreviousStatus(), REVOKED_STATUS, ActionType.REVOKE);
        captureSnapshot(receiptId, tenantDomain, ActionType.REVOKE);
        untrackExpiry(receiptId, tenantDomain);
    }

    @Override
    public void preDeleteConsent(String receiptId, String tenantDomain) {

        DPDPConsentExpiryReconciler.expireConsentIfDue(tenantDomain, receiptId);
        capturePreDeleteSnapshot(receiptId, tenantDomain);
    }

    @Override
    public void postDeleteConsent(String receiptId, String tenantDomain) {

        // The receipt row is gone by now - DELETED is an accelerator-defined status, not one
        // carbon-consent-management itself ever stores.
        recordStatusAudit(receiptId, tenantDomain, takePreviousStatus(), DELETED_STATUS, ActionType.DELETE);
        // Kept for symmetry - delete has no reachable path through any REST API IS ships today,
        // so postRevokeConsent above is the cleanup path this actually depends on in practice.
        untrackExpiry(receiptId, tenantDomain);
    }

    @Override
    public void preAuthorizeConsent(String consentId, String userId, String authStatus, String tenantDomain) {

        DPDPConsentExpiryReconciler.expireConsentIfDue(tenantDomain, consentId);
        capturePreviousStatus(consentId);
    }

    @Override
    public void postAuthorizeConsent(String consentId, String userId, String authStatus, String tenantDomain) {

        // authStatus is the per-authorization status (e.g. APPROVED/REJECTED), not necessarily
        // the consent's own recomputed overall state, so the current status is re-read live.
        String previousStatus = takePreviousStatus();
        try {
            PrivilegedConsentManager consentManager = DPDPIdentityExtensionDataHolder.getInstance()
                    .getPrivilegedConsentManager();
            Receipt receipt = consentManager.getReceiptWithExtendedSchema(consentId);
            String currentStatus = receipt.getState();
            ActionType actionType = mapAuthorizeActionType(authStatus);
            recordStatusAudit(consentId, tenantDomain, previousStatus, currentStatus, actionType);
            captureAuthorizeSnapshot(tenantDomain, consentId, actionType, consentManager, receipt);

            // REJECTED/REVOKED can never resolve to EXPIRED (see DPDPConsentExpiryReconciler) -
            // only ACTIVE/PENDING are worth tracking. Re-checked on every call, so a consent that
            // moves back to ACTIVE/PENDING later (e.g. a rejected authorization gets re-approved)
            // becomes trackable again at that point.
            if (ACTIVE_STATUS.equals(currentStatus) || PENDING_STATUS.equals(currentStatus)) {
                trackExpiry(consentId, tenantDomain, receipt.getExpiryTime());
            } else {
                untrackExpiry(consentId, tenantDomain);
            }
        } catch (Exception e) {
            LOG.error("Error reading the post-authorize state for consent: " + sanitize(consentId), e);
        }
    }

    /**
     * {@code authStatus} matches {@link ConsentAuthorization.AuthorizationStatus}'s names
     * (APPROVED/REJECTED/REVOKED) - anything else defaults to APPROVE rather than throwing, since
     * this is invoked from a listener hook that must never let a mutation-blocking exception
     * escape.
     */
    private static ActionType mapAuthorizeActionType(String authStatus) {

        if ("REJECTED".equals(authStatus)) {
            return ActionType.AUTHORIZE_REJECT;
        }
        if ("REVOKED".equals(authStatus)) {
            return ActionType.AUTHORIZE_REVOKE;
        }
        return ActionType.AUTHORIZE_APPROVE;
    }

    /**
     * Reads and remembers the consent's status before a mutation that changes it, purely so the
     * corresponding {@code post*} hook can report the correct {@code previousStatus} on its
     * status-audit row - the snapshot itself is captured after the mutation completes (see
     * {@link #captureSnapshot}), not here.
     */
    private void capturePreviousStatus(String consentId) {

        try {
            Receipt receipt = DPDPIdentityExtensionDataHolder.getInstance().getPrivilegedConsentManager()
                    .getReceiptWithExtendedSchema(consentId);
            PREVIOUS_STATUS.set(receipt.getState());
        } catch (Exception e) {
            LOG.error("Error reading the pre-mutation status for consent: " + sanitize(consentId), e);
        }
    }

    /**
     * Captures a fresh, post-mutation snapshot tagged with the action that just produced it.
     */
    private void captureSnapshot(String consentId, String tenantDomain, ActionType actionType) {

        try {
            PrivilegedConsentManager consentManager = DPDPIdentityExtensionDataHolder.getInstance()
                    .getPrivilegedConsentManager();
            Receipt receipt = consentManager.getReceiptWithExtendedSchema(consentId);
            List<ConsentAuthorization> authorizations = consentManager.getConsentAuthorizations(consentId);
            storeSnapshot(tenantDomain, consentId, actionType, receipt, authorizations);
        } catch (Exception e) {
            LOG.error("Error capturing a '" + actionType + "' consent history snapshot for consent: "
                    + sanitize(consentId), e);
        }
    }

    /**
     * {@code postAuthorizeConsent} already has the receipt in hand from resolving the current
     * status - reused here instead of a second, redundant fetch. Caught separately from that
     * caller's own try block so a snapshot failure can't also skip the expiry tracking that
     * follows it.
     */
    private void captureAuthorizeSnapshot(String tenantDomain, String consentId, ActionType actionType,
            PrivilegedConsentManager consentManager, Receipt receipt) {

        try {
            List<ConsentAuthorization> authorizations = consentManager.getConsentAuthorizations(consentId);
            storeSnapshot(tenantDomain, consentId, actionType, receipt, authorizations);
        } catch (Exception e) {
            LOG.error("Error capturing a '" + actionType + "' consent history snapshot for consent: "
                    + sanitize(consentId), e);
        }
    }

    /**
     * Delete is the one action where the receipt row is gone immediately afterward, so this is
     * the only chance to capture anything - previous-status tracking and the snapshot itself have
     * to share this single pre-mutation read.
     */
    private void capturePreDeleteSnapshot(String consentId, String tenantDomain) {

        try {
            PrivilegedConsentManager consentManager = DPDPIdentityExtensionDataHolder.getInstance()
                    .getPrivilegedConsentManager();
            Receipt receipt = consentManager.getReceiptWithExtendedSchema(consentId);
            List<ConsentAuthorization> authorizations = consentManager.getConsentAuthorizations(consentId);
            PREVIOUS_STATUS.set(receipt.getState());
            storeSnapshot(tenantDomain, consentId, ActionType.DELETE, receipt, authorizations);
        } catch (Exception e) {
            LOG.error("Error capturing the pre-delete consent history snapshot for consent: " + sanitize(consentId),
                    e);
        }
    }

    private void storeSnapshot(String tenantDomain, String consentId, ActionType actionType, Receipt receipt,
            List<ConsentAuthorization> authorizations) throws Exception {

        String snapshotJson = DPDPConsentSnapshotBuilder.buildSnapshotJson(receipt, authorizations);
        DPDPIdentityExtensionDataHolder.getInstance().getConsentHistoryService()
                .recordHistorySnapshot(tenantDomain, consentId, actionType, snapshotJson, getActionBy());
    }

    private void recordStatusAudit(String consentId, String tenantDomain, String previousStatus,
            String currentStatus, ActionType actionType) {

        try {
            DPDPIdentityExtensionDataHolder.getInstance().getConsentHistoryService()
                    .recordStatusAudit(tenantDomain, consentId, previousStatus, currentStatus, actionType,
                            getActionBy());
        } catch (Exception e) {
            LOG.error("Error recording a '" + actionType + "' status-audit row for consent: " + sanitize(consentId),
                    e);
        }
    }

    private void trackExpiry(String consentId, String tenantDomain, Timestamp expiryTime) {

        if (expiryTime == null) {
            return;
        }
        try {
            DPDPIdentityExtensionDataHolder.getInstance().getConsentExpiryService()
                    .trackExpiry(tenantDomain, consentId, expiryTime.getTime());
        } catch (Exception e) {
            LOG.error("Error tracking expiry for consent: " + sanitize(consentId), e);
        }
    }

    private void untrackExpiry(String consentId, String tenantDomain) {

        try {
            DPDPIdentityExtensionDataHolder.getInstance().getConsentExpiryService()
                    .untrackExpiry(tenantDomain, consentId);
        } catch (Exception e) {
            LOG.error("Error untracking expiry for consent: " + sanitize(consentId), e);
        }
    }

    private static String takePreviousStatus() {

        String previousStatus = PREVIOUS_STATUS.get();
        PREVIOUS_STATUS.remove();
        return previousStatus;
    }

    private static String getActionBy() {

        return PrivilegedCarbonContext.getThreadLocalCarbonContext().getUsername();
    }

    private static String sanitize(String value) {

        return value == null ? null : value.replaceAll("[\r\n]", "");
    }
}
