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
import org.wso2.dpdp.accelerator.consent.extensions.dao.ConsentExpiryTrackerDAO;
import org.wso2.dpdp.accelerator.consent.extensions.dao.exceptions.ConsentExtensionsDaoException;
import org.wso2.dpdp.accelerator.consent.extensions.dao.impl.ConsentExpiryTrackerDAOImpl;
import org.wso2.dpdp.accelerator.consent.extensions.dao.models.ConsentExpiryRecord;
import org.wso2.dpdp.accelerator.consent.extensions.service.ConsentExpiryService;
import org.wso2.dpdp.accelerator.consent.extensions.service.exception.ConsentExtensionsServiceException;

import java.sql.Connection;
import java.util.List;

public class ConsentExpiryServiceImpl implements ConsentExpiryService {

    private static final Log LOG = LogFactory.getLog(ConsentExpiryServiceImpl.class);

    private final ConsentExpiryTrackerDAO consentExpiryTrackerDAO;

    public ConsentExpiryServiceImpl() {

        this(new ConsentExpiryTrackerDAOImpl());
    }

    /** Lets tests substitute a mocked DAO while still running through a real transaction. */
    ConsentExpiryServiceImpl(ConsentExpiryTrackerDAO consentExpiryTrackerDAO) {

        this.consentExpiryTrackerDAO = consentExpiryTrackerDAO;
    }

    @Override
    public void trackExpiry(String orgId, String consentId, long expiryTimeMillis) {

        try {
            DatabaseUtils.executeInTransaction(connection -> {
                consentExpiryTrackerDAO.upsertExpiry(connection, orgId, consentId, expiryTimeMillis);
                LOG.debug("Tracking expiry for consent: " + consentId);
                return null;
            });
        } catch (ConsentExtensionsDaoException e) {
            throw new ConsentExtensionsServiceException("Unable to track expiry for consent: " + consentId, e);
        }
    }

    @Override
    public void untrackExpiry(String orgId, String consentId) {

        try {
            DatabaseUtils.executeInTransaction(connection -> {
                consentExpiryTrackerDAO.deleteExpiry(connection, consentId);
                LOG.debug("Untracking expiry for consent: " + consentId);
                return null;
            });
        } catch (ConsentExtensionsDaoException e) {
            throw new ConsentExtensionsServiceException("Unable to untrack expiry for consent: " + consentId, e);
        }
    }

    @Override
    public boolean claimExpiryIfDue(Connection connection, ConsentExpiryRecord candidate, long nowMillis) {

        try {
            return consentExpiryTrackerDAO.claimDueExpiry(connection, candidate, nowMillis);
        } catch (ConsentExtensionsDaoException e) {
            throw new ConsentExtensionsServiceException(
                    "Unable to claim the observed expiry deadline for consent: " + candidate.getConsentId(), e);
        }
    }

    @Override
    public ConsentExpiryRecord findExpiry(String orgId, String consentId) {

        try {
            return DatabaseUtils.executeInTransaction(connection ->
                    consentExpiryTrackerDAO.findExpiry(connection, orgId, consentId));
        } catch (ConsentExtensionsDaoException e) {
            throw new ConsentExtensionsServiceException("Unable to look up expiry for consent: " + consentId, e);
        }
    }

    @Override
    public List<ConsentExpiryRecord> findDueExpiries(long nowMillis, int batchSize, ConsentExpiryRecord cursor) {

        try {
            return DatabaseUtils.executeInTransaction(connection ->
                    consentExpiryTrackerDAO.findDueExpiries(connection, nowMillis, batchSize, cursor));
        } catch (ConsentExtensionsDaoException e) {
            throw new ConsentExtensionsServiceException("Unable to find due consent expiries.", e);
        }
    }

    @Override
    public boolean reconcileExpiry(Connection connection, ConsentExpiryRecord candidate, long expiryTimeMillis) {

        try {
            return consentExpiryTrackerDAO.reconcileExpiry(connection, candidate, expiryTimeMillis);
        } catch (ConsentExtensionsDaoException e) {
            throw new ConsentExtensionsServiceException(
                    "Unable to reconcile the observed expiry deadline for consent: " + candidate.getConsentId(), e);
        }
    }
}
