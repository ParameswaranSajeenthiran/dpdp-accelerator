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

package org.wso2.dpdp.accelerator.consent.extensions.dao.impl;

import org.wso2.dpdp.accelerator.consent.extensions.dao.ConsentExpiryTrackerDAO;
import org.wso2.dpdp.accelerator.consent.extensions.dao.constants.ConsentExpiryDAOConstants;
import org.wso2.dpdp.accelerator.consent.extensions.dao.exceptions.ConsentExpiryDataAccessException;
import org.wso2.dpdp.accelerator.consent.extensions.dao.models.ConsentExpiryRecord;
import org.wso2.dpdp.accelerator.consent.extensions.dao.queries.ConsentExpiryDBQueries;
import org.wso2.dpdp.accelerator.consent.extensions.dao.queries.ConsentExpiryQueryFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class ConsentExpiryTrackerDAOImpl implements ConsentExpiryTrackerDAO {

    @Override
    public void upsertExpiry(Connection connection, String orgId, String consentId, long expiryTime)
            throws ConsentExpiryDataAccessException {

        try {
            try (PreparedStatement delete = connection.prepareStatement(
                    getQueries(connection).getUpsertExpiryDeleteQuery())) {
                delete.setString(1, consentId);
                delete.executeUpdate();
            }
            try (PreparedStatement insert = connection.prepareStatement(
                    getQueries(connection).getUpsertExpiryInsertQuery())) {
                insert.setString(1, consentId);
                insert.setString(2, orgId);
                insert.setLong(3, expiryTime);
                insert.executeUpdate();
            }
        } catch (SQLException e) {
            throw new ConsentExpiryDataAccessException(
                    "Error while upserting the expiry tracker row for consent: " + consentId, e);
        }
    }

    @Override
    public void deleteExpiry(Connection connection, String consentId) throws ConsentExpiryDataAccessException {

        try (PreparedStatement statement = connection.prepareStatement(
                getQueries(connection).getDeleteExpiryQuery())) {
            statement.setString(1, consentId);
            statement.executeUpdate();
        } catch (SQLException e) {
            throw new ConsentExpiryDataAccessException(
                    "Error while deleting the expiry tracker row for consent: " + consentId, e);
        }
    }

    @Override
    public List<ConsentExpiryRecord> findDueExpiries(Connection connection, long nowMillis, int batchSize)
            throws ConsentExpiryDataAccessException {

        List<ConsentExpiryRecord> records = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(
                getQueries(connection).getFindDueExpiriesQuery())) {
            statement.setLong(1, nowMillis);
            statement.setInt(2, batchSize);
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    records.add(mapRecord(resultSet));
                }
            }
        } catch (SQLException e) {
            throw new ConsentExpiryDataAccessException("Error while finding due expiry tracker rows.", e);
        }
        return records;
    }

    private ConsentExpiryRecord mapRecord(ResultSet resultSet) throws SQLException {

        ConsentExpiryRecord record = new ConsentExpiryRecord();
        record.setConsentId(resultSet.getString(ConsentExpiryDAOConstants.COLUMN_CONSENT_ID));
        record.setOrgId(resultSet.getString(ConsentExpiryDAOConstants.COLUMN_ORG_ID));
        record.setExpiryTime(resultSet.getLong(ConsentExpiryDAOConstants.COLUMN_EXPIRY_TIME));
        return record;
    }

    @Override
    public boolean claimDueExpiry(Connection connection, ConsentExpiryRecord candidate, long nowMillis)
            throws ConsentExpiryDataAccessException {

        try (PreparedStatement statement = connection.prepareStatement(
                getQueries(connection).getClaimObservedExpiryQuery())) {
            statement.setString(1, candidate.getConsentId());
            statement.setString(2, candidate.getOrgId());
            statement.setLong(3, candidate.getExpiryTime());
            statement.setLong(4, nowMillis);
            return statement.executeUpdate() == 1;
        } catch (SQLException e) {
            throw new ConsentExpiryDataAccessException("Error claiming the observed expiry deadline.", e);
        }
    }

    @Override
    public ConsentExpiryRecord findExpiry(Connection connection, String orgId, String consentId)
            throws ConsentExpiryDataAccessException {

        try (PreparedStatement statement = connection.prepareStatement(
                getQueries(connection).getFindExpiryQuery())) {
            statement.setString(1, orgId);
            statement.setString(2, consentId);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() ? mapRecord(resultSet) : null;
            }
        } catch (SQLException e) {
            throw new ConsentExpiryDataAccessException("Error reading the expiry tracker row.", e);
        }
    }

    @Override
    public List<ConsentExpiryRecord> findDueExpiries(Connection connection, long nowMillis, int batchSize,
            ConsentExpiryRecord cursor) throws ConsentExpiryDataAccessException {

        if (batchSize <= 0) {
            throw new IllegalArgumentException("Consent expiry batch size must be positive.");
        }
        if (cursor == null) {
            return findDueExpiries(connection, nowMillis, batchSize);
        }
        List<ConsentExpiryRecord> records = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(
                getQueries(connection).getFindDueExpiriesAfterQuery())) {
            statement.setLong(1, nowMillis);
            statement.setLong(2, cursor.getExpiryTime());
            statement.setLong(3, cursor.getExpiryTime());
            statement.setString(4, cursor.getConsentId());
            statement.setInt(5, batchSize);
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    records.add(mapRecord(resultSet));
                }
            }
        } catch (SQLException e) {
            throw new ConsentExpiryDataAccessException("Error reading the next expiry tracker page.", e);
        }
        return records;
    }
    @Override
    public boolean reconcileExpiry(Connection connection, ConsentExpiryRecord candidate, long expiryTimeMillis)
            throws ConsentExpiryDataAccessException {

        try (PreparedStatement statement = connection.prepareStatement(
                getQueries(connection).getReconcileExpiryQuery())) {
            statement.setLong(1, expiryTimeMillis);
            statement.setString(2, candidate.getConsentId());
            statement.setString(3, candidate.getOrgId());
            statement.setLong(4, candidate.getExpiryTime());
            return statement.executeUpdate() == 1;
        } catch (SQLException e) {
            throw new ConsentExpiryDataAccessException("Error reconciling the observed expiry deadline.", e);
        }
    }

    private ConsentExpiryDBQueries getQueries(Connection connection) {

        return ConsentExpiryQueryFactory.getQueryProvider(connection);
    }

}
