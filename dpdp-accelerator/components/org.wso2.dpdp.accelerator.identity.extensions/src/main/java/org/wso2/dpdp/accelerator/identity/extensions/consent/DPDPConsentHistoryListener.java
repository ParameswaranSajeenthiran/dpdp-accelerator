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
import org.wso2.dpdp.accelerator.consent.extensions.service.ConsentHistoryService;
import org.wso2.dpdp.accelerator.consent.extensions.service.constants.ConsentHistoryServiceConstants.ActionType;
import org.wso2.dpdp.accelerator.identity.extensions.internal.DPDPIdentityExtensionDataHolder;

import java.sql.Timestamp;
import java.util.List;

/**
 * Captures consent lifecycle events into {@code DPDP_CONSENT_STATUS_AUDIT}/{@code DPDP_CONSENT_HISTORY}.
 * Snapshots are captured on the {@code pre*} hooks, not {@code post*} - a post-revoke snapshot
 * just re-states "revoked" (already visible on the live record), while a pre-revoke snapshot
 * captures what's about to be lost, which is the actual point of an audit trail.
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

        // Nothing to snapshot pre-creation; the created state would already be on hand here,
        // except receiptInput.getState() is null whenever the caller's request omits "state" -
        // the REST layer's documented ACTIVE default is applied at the DB/schema level, never
        // written back onto this object, so a null here must be resolved with a live read
        // instead of being passed straight through to a NOT NULL column.
        String currentStatus = receiptInput.getState();
        if (currentStatus == null) {
            currentStatus = resolveCurrentStatus(receiptInput.getConsentReceiptId());
        }
        if (currentStatus != null) {
            recordStatusAudit(receiptInput.getConsentReceiptId(), tenantDomain, null, currentStatus,
                    ActionType.CREATE);
        }
        trackExpiry(receiptInput.getConsentReceiptId(), tenantDomain, receiptInput.getExpiryTime());
    }

    private String resolveCurrentStatus(String consentId) {

        try {
            return DPDPIdentityExtensionDataHolder.getInstance().getPrivilegedConsentManager()
                    .getReceiptWithExtendedSchema(consentId).getState();
        } catch (Exception e) {
            LOG.error("Error resolving the current status for consent: " + sanitize(consentId), e);
            return null;
        }
    }

    @Override
    public void preUpdateConsent(ReceiptUpdateInput updateInput, String tenantDomain) {

        DPDPConsentExpiryReconciler.expireConsentIfDue(tenantDomain, updateInput.getConsentReceiptId());
        captureSnapshot(updateInput.getConsentReceiptId(), tenantDomain, ActionType.UPDATE);
    }

    @Override
    public void postUpdateConsent(ReceiptUpdateInput updateInput, String tenantDomain) {

        // ReceiptUpdateInput carries no state field - update never changes the consent's
        // lifecycle status, so previous and current are the same captured pre-mutation value.
        String previousStatus = takePreviousStatus();
        recordStatusAudit(updateInput.getConsentReceiptId(), tenantDomain, previousStatus, previousStatus,
                ActionType.UPDATE);

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
        captureSnapshot(receiptId, tenantDomain, ActionType.REVOKE);
    }

    @Override
    public void postRevokeConsent(String receiptId, String tenantDomain) {

        recordStatusAudit(receiptId, tenantDomain, takePreviousStatus(), REVOKED_STATUS, ActionType.REVOKE);
        untrackExpiry(receiptId, tenantDomain);
    }

    @Override
    public void preDeleteConsent(String receiptId, String tenantDomain) {

        DPDPConsentExpiryReconciler.expireConsentIfDue(tenantDomain, receiptId);
        captureSnapshot(receiptId, tenantDomain, ActionType.DELETE);
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
        captureSnapshot(consentId, tenantDomain, ActionType.AUTHORIZE);
    }

    @Override
    public void postAuthorizeConsent(String consentId, String userId, String authStatus, String tenantDomain) {

        // authStatus is the per-authorization status (e.g. APPROVED/REJECTED), not necessarily
        // the consent's own recomputed overall state, so the current status is re-read live.
        String previousStatus = takePreviousStatus();
        try {
            Receipt receipt = DPDPIdentityExtensionDataHolder.getInstance().getPrivilegedConsentManager()
                    .getReceiptWithExtendedSchema(consentId);
            String currentStatus = receipt.getState();
            recordStatusAudit(consentId, tenantDomain, previousStatus, currentStatus, ActionType.AUTHORIZE);

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

    private void captureSnapshot(String consentId, String tenantDomain, ActionType actionType) {

        try {
            PrivilegedConsentManager consentManager = DPDPIdentityExtensionDataHolder.getInstance()
                    .getPrivilegedConsentManager();
            Receipt receipt = consentManager.getReceiptWithExtendedSchema(consentId);
            List<ConsentAuthorization> authorizations = consentManager.getConsentAuthorizations(consentId);
            PREVIOUS_STATUS.set(receipt.getState());

            String snapshotJson = DPDPConsentSnapshotBuilder.buildSnapshotJson(receipt, authorizations);
            DPDPIdentityExtensionDataHolder.getInstance().getConsentHistoryService()
                    .recordHistorySnapshot(tenantDomain, consentId, actionType, snapshotJson, getActionBy());
        } catch (Exception e) {
            LOG.error("Error capturing a consent history snapshot for a '" + actionType + "' action on consent: "
                    + sanitize(consentId), e);
        }
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
