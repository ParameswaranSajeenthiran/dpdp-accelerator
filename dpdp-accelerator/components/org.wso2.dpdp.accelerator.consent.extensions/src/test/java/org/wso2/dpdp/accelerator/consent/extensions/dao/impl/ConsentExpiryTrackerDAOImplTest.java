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

import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import org.wso2.dpdp.accelerator.consent.extensions.dao.ConsentExpiryTrackerDAO;
import org.wso2.dpdp.accelerator.consent.extensions.dao.models.ConsentExpiryRecord;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.UUID;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

/**
 * Exercises the real SQL against an in-memory H2 database - a plain interface/impl mock would
 * only prove the mock was called, not that the queries are actually correct.
 */
public class ConsentExpiryTrackerDAOImplTest {

    private static final String ORG_ID = "tenant-a.com";
    private static final String CONSENT_ID = "consent-1234";

    private Connection connection;
    private ConsentExpiryTrackerDAO consentExpiryTrackerDAO;

    @BeforeMethod
    public void setUp() throws SQLException {

        connection = DriverManager.getConnection("jdbc:h2:mem:" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1");
        try (Statement statement = connection.createStatement()) {
            statement.execute("CREATE TABLE DPDP_CONSENT_EXPIRY_TRACKER ("
                    + "CONSENT_ID VARCHAR(255) NOT NULL PRIMARY KEY,"
                    + "ORG_ID VARCHAR(255) NOT NULL,"
                    + "EXPIRY_TIME BIGINT NOT NULL)");
        }
        consentExpiryTrackerDAO = new ConsentExpiryTrackerDAOImpl();
    }

    @AfterMethod
    public void tearDown() throws SQLException {

        connection.close();
    }

    @Test
    public void upsertExpiryInsertsWhenNoRowExists() throws Exception {

        consentExpiryTrackerDAO.upsertExpiry(connection, ORG_ID, CONSENT_ID, 5000L);

        List<ConsentExpiryRecord> due = consentExpiryTrackerDAO.findDueExpiries(connection, 10_000L, 10);
        assertEquals(due.size(), 1);
        assertEquals(due.get(0).getConsentId(), CONSENT_ID);
        assertEquals(due.get(0).getOrgId(), ORG_ID);
        assertEquals(due.get(0).getExpiryTime(), 5000L);
    }

    @Test
    public void upsertExpiryReplacesAnExistingRow() throws Exception {

        consentExpiryTrackerDAO.upsertExpiry(connection, ORG_ID, CONSENT_ID, 5000L);
        consentExpiryTrackerDAO.upsertExpiry(connection, ORG_ID, CONSENT_ID, 9000L);

        List<ConsentExpiryRecord> due = consentExpiryTrackerDAO.findDueExpiries(connection, 10_000L, 10);
        assertEquals(due.size(), 1);
        assertEquals(due.get(0).getExpiryTime(), 9000L);
    }

    @Test
    public void deleteExpiryRemovesTheRow() throws Exception {

        consentExpiryTrackerDAO.upsertExpiry(connection, ORG_ID, CONSENT_ID, 5000L);
        consentExpiryTrackerDAO.deleteExpiry(connection, CONSENT_ID);

        List<ConsentExpiryRecord> due = consentExpiryTrackerDAO.findDueExpiries(connection, 10_000L, 10);
        assertTrue(due.isEmpty());
    }

    @Test
    public void claimDueExpirySucceedsOnlyWhenExpiryTimeHasPassed() throws Exception {

        consentExpiryTrackerDAO.upsertExpiry(connection, ORG_ID, CONSENT_ID, 5000L);

        assertFalse(consentExpiryTrackerDAO.claimDueExpiry(connection, CONSENT_ID, 4000L));
        assertTrue(consentExpiryTrackerDAO.claimDueExpiry(connection, CONSENT_ID, 5000L));
    }

    @Test
    public void claimDueExpiryIsAtomicAndOnlyEverClaimsOnce() throws Exception {

        consentExpiryTrackerDAO.upsertExpiry(connection, ORG_ID, CONSENT_ID, 5000L);

        assertTrue(consentExpiryTrackerDAO.claimDueExpiry(connection, CONSENT_ID, 10_000L));
        assertFalse(consentExpiryTrackerDAO.claimDueExpiry(connection, CONSENT_ID, 10_000L));
    }

    @Test
    public void findDueExpiriesOnlyReturnsRowsAtOrBeforeNowOrderedOldestFirst() throws Exception {

        consentExpiryTrackerDAO.upsertExpiry(connection, ORG_ID, "consent-future", 20_000L);
        consentExpiryTrackerDAO.upsertExpiry(connection, ORG_ID, "consent-old", 1000L);
        consentExpiryTrackerDAO.upsertExpiry(connection, ORG_ID, "consent-due", 5000L);

        List<ConsentExpiryRecord> due = consentExpiryTrackerDAO.findDueExpiries(connection, 10_000L, 10);

        assertEquals(due.size(), 2);
        assertEquals(due.get(0).getConsentId(), "consent-old");
        assertEquals(due.get(1).getConsentId(), "consent-due");
    }

    @Test
    public void findDueExpiriesRespectsBatchSize() throws Exception {

        consentExpiryTrackerDAO.upsertExpiry(connection, ORG_ID, "consent-1", 1000L);
        consentExpiryTrackerDAO.upsertExpiry(connection, ORG_ID, "consent-2", 2000L);

        List<ConsentExpiryRecord> due = consentExpiryTrackerDAO.findDueExpiries(connection, 10_000L, 1);

        assertEquals(due.size(), 1);
        assertEquals(due.get(0).getConsentId(), "consent-1");
    }
}
