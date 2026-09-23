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

import org.wso2.carbon.consent.mgt.core.PrivilegedConsentManager;
import org.wso2.carbon.consent.mgt.core.exception.ConsentManagementException;
import org.wso2.carbon.consent.mgt.core.model.Receipt;
import org.wso2.dpdp.accelerator.common.config.DPDPConfigurationService;
import org.wso2.dpdp.accelerator.common.util.DatabaseUtils;
import org.wso2.dpdp.accelerator.consent.extensions.dao.models.ConsentExpiryRecord;
import org.wso2.dpdp.accelerator.consent.extensions.service.ConsentExpiryService;
import org.wso2.dpdp.accelerator.consent.extensions.service.ConsentHistoryService;
import org.wso2.dpdp.accelerator.consent.extensions.service.constants.ConsentHistoryServiceConstants;
import org.wso2.dpdp.accelerator.consent.extensions.service.constants.ConsentHistoryServiceConstants.ActionType;
import org.wso2.dpdp.accelerator.event.notifications.common.listener.DPDPLifecycleEventListener;

import java.util.List;
import java.util.function.Supplier;

/** Owns the DPDP transaction for one observed consent expiry. Source consent reads happen before it. */
public class ConsentExpiryProcessingService {

    private final ConsentExpiryService expiryService;
    private final ConsentHistoryService historyService;
    private final PrivilegedConsentManager consentManager;
    private final DPDPConfigurationService configuration;
    private final Supplier<DPDPLifecycleEventListener> publisherSupplier;

    public ConsentExpiryProcessingService(ConsentExpiryService expiryService, ConsentHistoryService historyService,
            PrivilegedConsentManager consentManager, DPDPConfigurationService configuration,
            Supplier<DPDPLifecycleEventListener> publisherSupplier) {

        this.expiryService = expiryService;
        this.historyService = historyService;
        this.consentManager = consentManager;
        this.configuration = configuration;
        this.publisherSupplier = publisherSupplier;
    }

    /** Returns false when the candidate is no longer eligible or another transaction won the claim. */
    public boolean process(ConsentExpiryRecord candidate, long cutoff) throws ConsentManagementException {

        if (candidate == null || candidate.getExpiryTime() > cutoff) {
            return false;
        }
        String consentId = candidate.getConsentId();
        String orgId = candidate.getOrgId();
        Receipt receipt = consentManager.getReceiptWithExtendedSchema(consentId);
        if (receipt.getExpiryTime() == null) {
            return false;
        }
        if (receipt.getExpiryTime().getTime() != candidate.getExpiryTime()) {
            // Use the persisted deadline, including its database precision. Never overwrite a tracker
            // changed since this candidate was fetched. A later sweep processes the corrected row.
            DatabaseUtils.executeInTransaction(connection ->
                    expiryService.reconcileExpiry(connection, candidate, receipt.getExpiryTime().getTime()));
            return false;
        }
        if (!"EXPIRED".equals(receipt.getState())) {
            return false;
        }
        boolean snapshotEnabled = configuration.isConsentHistorySnapshotEnabled();
        boolean publicationEnabled = configuration.isEventNotificationLifecycleEventsPublishingEnabled();
        String snapshot = snapshotEnabled ? DPDPConsentSnapshotBuilder.buildSnapshotJson(receipt,
                consentManager.getConsentAuthorizations(consentId)) : null;
        List<String> purposes = publicationEnabled ? DPDPConsentSnapshotBuilder.resolvePurposes(receipt) : null;
        DPDPLifecycleEventListener publisher = publicationEnabled ? publisherSupplier.get() : null;
        if (publicationEnabled && publisher == null) {
            throw new IllegalStateException("Consent expiry event publication is enabled but its publisher is absent.");
        }
        return DatabaseUtils.executeInTransaction(connection -> {
            if (!expiryService.claimExpiryIfDue(connection, candidate, cutoff)) {
                return false;
            }
            String previousStatus = historyService.getLastKnownStatus(connection, orgId, consentId);
            historyService.recordStatusAudit(connection, orgId, consentId, previousStatus, "EXPIRED",
                    ActionType.EXPIRE, ConsentHistoryServiceConstants.SYSTEM_ACTOR_EXPIRY);
            if (snapshotEnabled) {
                historyService.recordHistorySnapshot(connection, orgId, consentId, ActionType.EXPIRE, snapshot,
                        ConsentHistoryServiceConstants.SYSTEM_ACTOR_EXPIRY);
            }
            if (publicationEnabled) {
                publisher.onConsentExpired(connection, orgId, consentId, previousStatus, purposes);
            }
            return true;
        });
    }
}
