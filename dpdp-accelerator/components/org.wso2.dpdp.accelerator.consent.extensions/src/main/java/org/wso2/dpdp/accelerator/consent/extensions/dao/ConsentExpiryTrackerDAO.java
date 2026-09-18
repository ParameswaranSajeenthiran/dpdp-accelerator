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

package org.wso2.dpdp.accelerator.consent.extensions.dao;

import org.wso2.dpdp.accelerator.consent.extensions.dao.exceptions.ConsentExpiryDataAccessException;
import org.wso2.dpdp.accelerator.consent.extensions.dao.models.ConsentExpiryRecord;

import java.sql.Connection;
import java.util.List;

/**
 * Every method takes the {@link Connection} as its first parameter - this DAO never opens or
 * manages its own connection, the service layer owns the transaction.
 */
public interface ConsentExpiryTrackerDAO {

    /**
     * Replaces any existing tracker row for this consent with one carrying {@code expiryTime} -
     * a plain delete-then-insert, not a vendor-specific {@code MERGE}/{@code ON DUPLICATE KEY}.
     */
    void upsertExpiry(Connection connection, String orgId, String consentId, long expiryTime)
            throws ConsentExpiryDataAccessException;

    void deleteExpiry(Connection connection, String consentId) throws ConsentExpiryDataAccessException;

    List<ConsentExpiryRecord> findDueExpiries(Connection connection, long nowMillis, int batchSize)
            throws ConsentExpiryDataAccessException;

    boolean claimDueExpiry(Connection connection, ConsentExpiryRecord candidate, long nowMillis)
            throws ConsentExpiryDataAccessException;

    ConsentExpiryRecord findExpiry(Connection connection, String orgId, String consentId)
            throws ConsentExpiryDataAccessException;

    List<ConsentExpiryRecord> findDueExpiries(Connection connection, long nowMillis, int batchSize,
            ConsentExpiryRecord cursor) throws ConsentExpiryDataAccessException;

    /** Updates only the observed tracker deadline, without owning the caller's transaction. */
    boolean reconcileExpiry(Connection connection, ConsentExpiryRecord candidate, long expiryTimeMillis)
            throws ConsentExpiryDataAccessException;
}
