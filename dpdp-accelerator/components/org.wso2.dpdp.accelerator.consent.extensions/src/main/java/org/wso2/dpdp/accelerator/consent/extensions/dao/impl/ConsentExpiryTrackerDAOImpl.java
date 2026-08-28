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

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class ConsentExpiryTrackerDAOImpl implements ConsentExpiryTrackerDAO {

    private final ConsentExpiryDBQueries queries;

    public ConsentExpiryTrackerDAOImpl() {

        this(new ConsentExpiryDBQueries());
    }

    ConsentExpiryTrackerDAOImpl(ConsentExpiryDBQueries queries) {

        this.queries = queries;
    }

    @Override
    public void upsertExpiry(Connection connection, String orgId, String consentId, long expiryTime)
            throws ConsentExpiryDataAccessException {

        try {
            try (PreparedStatement delete = connection.prepareStatement(queries.getUpsertExpiryDeleteQuery())) {
                delete.setString(1, consentId);
                delete.executeUpdate();
            }
            try (PreparedStatement insert = connection.prepareStatement(queries.getUpsertExpiryInsertQuery())) {
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

        try (PreparedStatement statement = connection.prepareStatement(queries.getDeleteExpiryQuery())) {
            statement.setString(1, consentId);
            statement.executeUpdate();
        } catch (SQLException e) {
            throw new ConsentExpiryDataAccessException(
                    "Error while deleting the expiry tracker row for consent: " + consentId, e);
        }
    }

    @Override
    public boolean claimDueExpiry(Connection connection, String consentId, long nowMillis)
            throws ConsentExpiryDataAccessException {

        try (PreparedStatement statement = connection.prepareStatement(queries.getClaimDueExpiryQuery())) {
            statement.setString(1, consentId);
            statement.setLong(2, nowMillis);
            return statement.executeUpdate() == 1;
        } catch (SQLException e) {
            throw new ConsentExpiryDataAccessException(
                    "Error while claiming the expiry tracker row for consent: " + consentId, e);
        }
    }

    @Override
    public List<ConsentExpiryRecord> findDueExpiries(Connection connection, long nowMillis, int batchSize)
            throws ConsentExpiryDataAccessException {

        List<ConsentExpiryRecord> records = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(queries.getFindDueExpiriesQuery())) {
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
}
