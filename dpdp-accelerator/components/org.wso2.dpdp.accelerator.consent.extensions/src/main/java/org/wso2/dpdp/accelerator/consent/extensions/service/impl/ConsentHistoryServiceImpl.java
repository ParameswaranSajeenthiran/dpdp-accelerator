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

package org.wso2.dpdp.accelerator.consent.extensions.service.impl;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.wso2.dpdp.accelerator.common.util.DatabaseUtils;
import org.wso2.dpdp.accelerator.common.util.LogSanitizer;
import org.wso2.dpdp.accelerator.consent.extensions.dao.ConsentHistoryDAO;
import org.wso2.dpdp.accelerator.consent.extensions.dao.constants.ConsentHistoryDAOConstants;
import org.wso2.dpdp.accelerator.consent.extensions.dao.exceptions.ConsentExtensionsDaoException;
import org.wso2.dpdp.accelerator.consent.extensions.dao.impl.ConsentHistoryDAOImpl;
import org.wso2.dpdp.accelerator.consent.extensions.dao.models.ConsentHistoryRecord;
import org.wso2.dpdp.accelerator.consent.extensions.dao.models.ConsentStatusAuditRecord;
import org.wso2.dpdp.accelerator.consent.extensions.internal.DPDPConsentExtensionDataHolder;
import org.wso2.dpdp.accelerator.consent.extensions.service.ConsentHistoryService;
import org.wso2.dpdp.accelerator.consent.extensions.service.constants.ConsentHistoryServiceConstants.ActionType;
import org.wso2.dpdp.accelerator.consent.extensions.service.exception.ConsentExtensionsServiceException;
import org.wso2.dpdp.accelerator.consent.extensions.service.models.PagedResult;

import java.sql.Connection;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public class ConsentHistoryServiceImpl implements ConsentHistoryService {

    private static final Log LOG = LogFactory.getLog(ConsentHistoryServiceImpl.class);

    /** Matches ConsentHistoryErrorCodes.SERVER_ERROR in the endpoint module - duplicated by value
     * rather than imported, since this module can't depend on its own consumer's error codes. */
    private static final String SERVER_ERROR_CODE = "CH-00004";

    private final ConsentHistoryDAO consentHistoryDAO;

    public ConsentHistoryServiceImpl() {

        this(new ConsentHistoryDAOImpl());
    }

    /** Lets tests substitute a mocked DAO while still running through a real transaction. */
    ConsentHistoryServiceImpl(ConsentHistoryDAO consentHistoryDAO) {

        this.consentHistoryDAO = consentHistoryDAO;
    }

    @Override
    public void recordStatusAudit(String tenantDomain, String consentId, String previousStatus,
            String currentStatus, ActionType actionType, String actionBy) {

        // Checked before executeInTransaction so a call that has nothing to record doesn't take a
        // connection from the pool. A status-audit row means "the status changed here" -
        // previousStatus and currentStatus being equal (e.g. an UPDATE, which never touches
        // lifecycle status; or one authorizer's approval when others are still pending) isn't a
        // transition, so there is nothing to record. DPDP_CONSENT_HISTORY already captures every
        // action regardless, with full detail, so nothing is lost by skipping a no-op row here.
        if (Objects.equals(previousStatus, currentStatus)) {
            LOG.debug("Skipping a '" + actionType + "' status-audit row for consent: "
                    + LogSanitizer.sanitize(consentId) + " - status did not change ("
                    + LogSanitizer.sanitize(currentStatus) + ").");
            return;
        }

        try {
            DatabaseUtils.executeInTransaction(connection -> {
                recordStatusAudit(connection, tenantDomain, consentId, previousStatus, currentStatus, actionType,
                        actionBy);
                LOG.debug("Recorded a '" + actionType + "' status-audit row for consent: "
                        + LogSanitizer.sanitize(consentId));
                return null;
            });
        } catch (ConsentExtensionsDaoException e) {
            throw new ConsentExtensionsServiceException(
                    "Unable to record a status-audit entry for consent: " + consentId, e);
        }
    }

    @Override
    public void recordHistorySnapshot(String tenantDomain, String consentId, ActionType actionType,
            String snapshotJson, String actionBy) {

        if (!DPDPConsentExtensionDataHolder.getInstance().getConfigurationService().isConsentHistorySnapshotEnabled()) {
            LOG.debug("Consent history snapshot recording is disabled; skipping consent: "
                    + LogSanitizer.sanitize(consentId));
            return;
        }

        try {
            DatabaseUtils.executeInTransaction(connection -> {
                recordHistorySnapshot(connection, tenantDomain, consentId, actionType, snapshotJson, actionBy);
                LOG.debug("Recorded a '" + actionType + "' history snapshot for consent: "
                        + LogSanitizer.sanitize(consentId));
                return null;
            });
        } catch (ConsentExtensionsDaoException e) {
            throw new ConsentExtensionsServiceException(
                    "Unable to record a history snapshot for consent: " + consentId, e);
        }
    }

    @Override
    public PagedResult<ConsentStatusAuditRecord> getStatusAuditHistory(String tenantDomain, String consentId,
            int limit, int offset) {

        String orgId = resolveOrgId(tenantDomain);
        try {
            return DatabaseUtils.executeInTransaction(connection -> {
                List<ConsentStatusAuditRecord> records = consentHistoryDAO.getStatusAuditHistory(connection, orgId,
                        consentId, limit, offset);
                int totalCount = consentHistoryDAO.getStatusAuditHistoryCount(connection, orgId, consentId);
                return new PagedResult<>(records, totalCount);
            });
        } catch (ConsentExtensionsDaoException e) {
            LOG.error("Error retrieving status-audit history for consent: " + LogSanitizer.sanitize(consentId), e);
            throw new ConsentExtensionsServiceException(SERVER_ERROR_CODE,
                    "Could not retrieve the status-audit history.", 500, e);
        }
    }

    @Override
    public PagedResult<ConsentHistoryRecord> getConsentHistory(String tenantDomain, String consentId, int limit,
            int offset) {

        String orgId = resolveOrgId(tenantDomain);
        try {
            return DatabaseUtils.executeInTransaction(connection -> {
                List<ConsentHistoryRecord> records = consentHistoryDAO.getConsentHistory(connection, orgId,
                        consentId, limit, offset);
                int totalCount = consentHistoryDAO.getConsentHistoryCount(connection, orgId, consentId);
                return new PagedResult<>(records, totalCount);
            });
        } catch (ConsentExtensionsDaoException e) {
            LOG.error("Error retrieving history for consent: " + LogSanitizer.sanitize(consentId), e);
            throw new ConsentExtensionsServiceException(SERVER_ERROR_CODE, "Could not retrieve the history.", 500,
                    e);
        }
    }

    @Override
    public void recordStatusAudit(Connection connection, String tenantDomain, String consentId,
            String previousStatus, String currentStatus, ActionType actionType, String actionBy) {

        // Skip status-audit if status did not change; DPDP_CONSENT_HISTORY already captures all actions.
        if (Objects.equals(previousStatus, currentStatus)) {
            LOG.debug("Skipping a '" + actionType + "' status-audit row for consent: "
                    + LogSanitizer.sanitize(consentId) + " - status did not change ("
                    + LogSanitizer.sanitize(currentStatus) + ").");
            return;
        }

        ConsentStatusAuditRecord record = new ConsentStatusAuditRecord();
        record.setAuditId(UUID.randomUUID().toString());
        record.setConsentId(consentId);
        record.setOrgId(resolveOrgId(tenantDomain));
        record.setPreviousStatus(previousStatus);
        record.setCurrentStatus(currentStatus);
        record.setActionType(actionType.name());
        record.setActionBy(actionBy);
        record.setActionTime(System.currentTimeMillis());

        try {
            consentHistoryDAO.insertStatusAudit(connection, record);
        } catch (ConsentExtensionsDaoException e) {
            throw new ConsentExtensionsServiceException(
                    "Unable to record a status-audit entry for consent: " + consentId, e);
        }
    }

    @Override
    public void recordHistorySnapshot(Connection connection, String tenantDomain, String consentId,
            ActionType actionType, String snapshotJson, String actionBy) {

        if (!DPDPConsentExtensionDataHolder.getInstance().getConfigurationService().isConsentHistorySnapshotEnabled()) {
            LOG.debug("Consent history snapshot recording is disabled; skipping consent: "
                    + LogSanitizer.sanitize(consentId));
            return;
        }

        ConsentHistoryRecord record = new ConsentHistoryRecord();
        record.setHistoryId(UUID.randomUUID().toString());
        record.setConsentId(consentId);
        record.setOrgId(resolveOrgId(tenantDomain));
        record.setActionType(actionType.name());
        record.setSnapshot(snapshotJson);
        record.setActionBy(actionBy);
        record.setActionTime(System.currentTimeMillis());

        try {
            consentHistoryDAO.insertHistorySnapshot(connection, record);
        } catch (ConsentExtensionsDaoException e) {
            throw new ConsentExtensionsServiceException(
                    "Unable to record a history snapshot for consent: " + consentId, e);
        }
    }

    @Override
    public String getLastKnownStatus(Connection connection, String tenantDomain, String consentId) {

        try {
            List<ConsentStatusAuditRecord> records = consentHistoryDAO.getStatusAuditHistory(connection,
                    resolveOrgId(tenantDomain), consentId, 1, 0);
            return records.isEmpty() ? null : records.get(0).getCurrentStatus();
        } catch (ConsentExtensionsDaoException e) {
            throw new ConsentExtensionsServiceException(
                    "Unable to look up the last known status for consent: " + consentId, e);
        }
    }

    private String resolveOrgId(String tenantDomain) {

        return tenantDomain == null ? ConsentHistoryDAOConstants.DEFAULT_ORG_ID : tenantDomain;
    }
}
