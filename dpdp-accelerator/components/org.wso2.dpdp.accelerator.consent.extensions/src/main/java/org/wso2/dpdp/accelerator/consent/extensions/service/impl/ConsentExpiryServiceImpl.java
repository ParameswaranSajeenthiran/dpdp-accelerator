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
import org.wso2.dpdp.accelerator.consent.extensions.dao.exceptions.ConsentExpiryDataAccessException;
import org.wso2.dpdp.accelerator.consent.extensions.dao.impl.ConsentExpiryTrackerDAOImpl;
import org.wso2.dpdp.accelerator.consent.extensions.dao.models.ConsentExpiryRecord;
import org.wso2.dpdp.accelerator.consent.extensions.service.ConsentExpiryService;

import java.sql.Connection;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

public class ConsentExpiryServiceImpl implements ConsentExpiryService {

    private static final Log LOG = LogFactory.getLog(ConsentExpiryServiceImpl.class);

    private final ConsentExpiryTrackerDAO consentExpiryTrackerDAO;
    private final Supplier<Connection> connectionSupplier;
    private final Consumer<Connection> commitAction;
    private final Consumer<Connection> rollbackAction;

    public ConsentExpiryServiceImpl() {

        this(new ConsentExpiryTrackerDAOImpl(), DatabaseUtils::getDBConnection, DatabaseUtils::commitTransaction,
                DatabaseUtils::rollbackTransaction);
    }

    /**
     * Lets tests substitute a fake {@link Connection} and no-op commit/rollback, without going
     * through the real, JNDI-backed {@link DatabaseUtils} - the DAO is mocked in those tests
     * anyway, so the connection object itself is never actually used for I/O.
     */
    ConsentExpiryServiceImpl(ConsentExpiryTrackerDAO consentExpiryTrackerDAO, Supplier<Connection> connectionSupplier,
            Consumer<Connection> commitAction, Consumer<Connection> rollbackAction) {

        this.consentExpiryTrackerDAO = consentExpiryTrackerDAO;
        this.connectionSupplier = connectionSupplier;
        this.commitAction = commitAction;
        this.rollbackAction = rollbackAction;
    }

    @Override
    public void trackExpiry(String orgId, String consentId, long expiryTimeMillis)
            throws ConsentExpiryDataAccessException {

        Connection connection = connectionSupplier.get();
        try {
            try {
                consentExpiryTrackerDAO.upsertExpiry(connection, orgId, consentId, expiryTimeMillis);
                commitAction.accept(connection);
                LOG.debug("Tracking expiry for consent: " + consentId);
            } catch (ConsentExpiryDataAccessException e) {
                rollbackAction.accept(connection);
                throw e;
            }
        } finally {
            DatabaseUtils.closeConnection(connection);
        }
    }

    @Override
    public void untrackExpiry(String orgId, String consentId) throws ConsentExpiryDataAccessException {

        Connection connection = connectionSupplier.get();
        try {
            try {
                consentExpiryTrackerDAO.deleteExpiry(connection, consentId);
                commitAction.accept(connection);
                LOG.debug("Untracking expiry for consent: " + consentId);
            } catch (ConsentExpiryDataAccessException e) {
                rollbackAction.accept(connection);
                throw e;
            }
        } finally {
            DatabaseUtils.closeConnection(connection);
        }
    }


    @Override
    public boolean claimExpiryIfDue(Connection connection, ConsentExpiryRecord candidate, long nowMillis)
            throws ConsentExpiryDataAccessException {

        return consentExpiryTrackerDAO.claimDueExpiry(connection, candidate, nowMillis);
    }

    @Override
    public ConsentExpiryRecord findExpiry(String orgId, String consentId) throws ConsentExpiryDataAccessException {

        Connection connection = connectionSupplier.get();
        try {
            return consentExpiryTrackerDAO.findExpiry(connection, orgId, consentId);
        } finally {
            DatabaseUtils.closeConnection(connection);
        }
    }

    @Override
    public List<ConsentExpiryRecord> findDueExpiries(long nowMillis, int batchSize, ConsentExpiryRecord cursor)
            throws ConsentExpiryDataAccessException {

        Connection connection = connectionSupplier.get();
        try {
            return consentExpiryTrackerDAO.findDueExpiries(connection, nowMillis, batchSize, cursor);
        } finally {
            DatabaseUtils.closeConnection(connection);
        }
    }
    @Override
    public boolean reconcileExpiry(Connection connection, ConsentExpiryRecord candidate, long expiryTimeMillis)
            throws ConsentExpiryDataAccessException {

        return consentExpiryTrackerDAO.reconcileExpiry(connection, candidate, expiryTimeMillis);
    }

}
