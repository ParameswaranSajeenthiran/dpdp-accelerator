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
import org.wso2.carbon.consent.mgt.core.model.ConsentAuthorization;
import org.wso2.carbon.consent.mgt.core.model.Receipt;
import org.wso2.carbon.context.PrivilegedCarbonContext;
import org.wso2.dpdp.accelerator.consent.extensions.dao.models.ConsentExpiryRecord;
import org.wso2.dpdp.accelerator.consent.extensions.dao.models.ConsentStatusAuditRecord;
import org.wso2.dpdp.accelerator.consent.extensions.service.ConsentHistoryService;
import org.wso2.dpdp.accelerator.consent.extensions.service.constants.ConsentHistoryServiceConstants;
import org.wso2.dpdp.accelerator.consent.extensions.service.constants.ConsentHistoryServiceConstants.ActionType;
import org.wso2.dpdp.accelerator.consent.extensions.service.models.PagedResult;
import org.wso2.dpdp.accelerator.identity.extensions.internal.DPDPIdentityExtensionDataHolder;

import java.util.List;

/**
 * Detects and records a consent's {@code EXPIRE} transition - called both from
 * {@link DPDPConsentHistoryListener}'s {@code pre*} hooks (a consent already lapsed being touched
 * again before the scheduled job catches it) and from the periodic
 * {@link org.wso2.dpdp.accelerator.identity.extensions.consent.scheduler.ConsentExpiryJob}. Both
 * paths funnel through {@link #expireConsentIfDue}, so the "claim, then record" logic exists in
 * exactly one place.
 *
 * <p>{@code carbon-consent-management} never persists {@code EXPIRED} - every read path resolves
 * it dynamically from {@code expiryTime}, so the receipt fetched here already reports
 * {@code EXPIRED} rather than the real prior status. The prior status is therefore read back from
 * this accelerator's own {@code DPDP_CONSENT_STATUS_AUDIT} - the most recent status-audit row's
 * {@code currentStatus} is exactly what a real CREATE/UPDATE/AUTHORIZE action last recorded,
 * before the consent lapsed.
 */
public final class DPDPConsentExpiryReconciler {

    private static final Log LOG = LogFactory.getLog(DPDPConsentExpiryReconciler.class);

    private DPDPConsentExpiryReconciler() {

    }

    /**
     * Claims and records this consent's expiry if its tracked expiry time has passed. A no-op if
     * nothing is due, if it was already claimed by another caller, or if consent expiry handling
     * is disabled.
     */
    public static void expireConsentIfDue(String orgId, String consentId) {

        if (!DPDPIdentityExtensionDataHolder.getInstance().getConfigurationService().isConsentExpiryEnabled()) {
            return;
        }
        try {
            boolean claimed = DPDPIdentityExtensionDataHolder.getInstance().getConsentExpiryService()
                    .claimExpiryIfDue(orgId, consentId, System.currentTimeMillis());
            if (!claimed) {
                return;
            }
            recordExpiry(orgId, consentId);
        } catch (Exception e) {
            LOG.error("Error expiring consent: " + sanitize(consentId), e);
        }
    }

    /**
     * Batch driver for the scheduled job: finds every tracker row already due and expires each,
     * one tenant flow per consent since they can belong to different tenants.
     */
    public static void expireDueConsents(int batchSize) {

        if (!DPDPIdentityExtensionDataHolder.getInstance().getConfigurationService().isConsentExpiryEnabled()) {
            return;
        }
        try {
            List<ConsentExpiryRecord> dueExpiries = DPDPIdentityExtensionDataHolder.getInstance()
                    .getConsentExpiryService().findDueExpiries(System.currentTimeMillis(), batchSize);
            if (!dueExpiries.isEmpty()) {
                LOG.info("Consent expiry job found " + dueExpiries.size() + " due consent(s) to expire.");
            } else {
                LOG.debug("Consent expiry job found no due consents.");
            }
            for (ConsentExpiryRecord dueExpiry : dueExpiries) {
                PrivilegedCarbonContext.startTenantFlow();
                try {
                    PrivilegedCarbonContext.getThreadLocalCarbonContext().setTenantDomain(dueExpiry.getOrgId());
                    expireConsentIfDue(dueExpiry.getOrgId(), dueExpiry.getConsentId());
                } finally {
                    PrivilegedCarbonContext.endTenantFlow();
                }
            }
        } catch (Exception e) {
            LOG.error("Error while processing due consent expiries.", e);
        }
    }

    private static void recordExpiry(String orgId, String consentId) throws Exception {

        ConsentHistoryService consentHistoryService = DPDPIdentityExtensionDataHolder.getInstance()
                .getConsentHistoryService();
        String previousStatus = getLastKnownStatus(orgId, consentId, consentHistoryService);

        consentHistoryService.recordStatusAudit(orgId, consentId, previousStatus, "EXPIRED", ActionType.EXPIRE,
                ConsentHistoryServiceConstants.SYSTEM_ACTOR_EXPIRY);

        PrivilegedConsentManager consentManager = DPDPIdentityExtensionDataHolder.getInstance()
                .getPrivilegedConsentManager();
        Receipt receipt = consentManager.getReceiptWithExtendedSchema(consentId);
        List<ConsentAuthorization> authorizations = consentManager.getConsentAuthorizations(consentId);
        String snapshotJson = DPDPConsentSnapshotBuilder.buildSnapshotJson(receipt, authorizations);
        consentHistoryService.recordHistorySnapshot(orgId, consentId, ActionType.EXPIRE, snapshotJson,
                ConsentHistoryServiceConstants.SYSTEM_ACTOR_EXPIRY);
    }

    private static String getLastKnownStatus(String orgId, String consentId,
            ConsentHistoryService consentHistoryService) throws Exception {

        PagedResult<ConsentStatusAuditRecord> latest = consentHistoryService.getStatusAuditHistory(orgId, consentId,
                1, 0);
        return latest.getRecords().isEmpty() ? null : latest.getRecords().get(0).getCurrentStatus();
    }

    private static String sanitize(String value) {

        return value == null ? null : value.replaceAll("[\r\n]", "");
    }
}
