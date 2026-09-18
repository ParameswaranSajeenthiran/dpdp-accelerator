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
import org.wso2.dpdp.accelerator.consent.extensions.dao.exceptions.ConsentExpiryDataAccessException;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.expectThrows;

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

    @Test
    public void cursorDoesNotSkipEqualDeadlinesAfterEarlierRowsAreDeleted() throws Exception {

        for (String id : new String[] { "a", "b", "c", "d", "e" }) {
            consentExpiryTrackerDAO.upsertExpiry(connection, ORG_ID, id, 1000);
        }
        List<ConsentExpiryRecord> first = consentExpiryTrackerDAO.findDueExpiries(connection, 2000, 2, null);
        assertEquals(first.get(0).getConsentId(), "a");
        assertEquals(first.get(1).getConsentId(), "b");
        assertTrue(consentExpiryTrackerDAO.claimDueExpiry(connection, first.get(0), 2000));
        // b represents a failed/rolled-back candidate; pagination must advance past it.
        List<ConsentExpiryRecord> second = consentExpiryTrackerDAO.findDueExpiries(connection, 2000, 2, first.get(1));
        assertEquals(second.get(0).getConsentId(), "c");
        assertEquals(second.get(1).getConsentId(), "d");
        assertEquals(consentExpiryTrackerDAO.findDueExpiries(connection, 2000, 2, second.get(1)).size(), 1);
    }

    @Test
    public void observedClaimChecksTenantDeadlineAndCutoff() throws Exception {

        consentExpiryTrackerDAO.upsertExpiry(connection, ORG_ID, CONSENT_ID, 1000);
        ConsentExpiryRecord candidate = consentExpiryTrackerDAO.findExpiry(connection, ORG_ID, CONSENT_ID);
        candidate.setOrgId("another-tenant");
        assertFalse(consentExpiryTrackerDAO.claimDueExpiry(connection, candidate, 2000));
        candidate.setOrgId(ORG_ID);
        assertFalse(consentExpiryTrackerDAO.claimDueExpiry(connection, candidate, 999));
        consentExpiryTrackerDAO.upsertExpiry(connection, ORG_ID, CONSENT_ID, 1500);
        assertFalse(consentExpiryTrackerDAO.claimDueExpiry(connection, candidate, 2000));
        candidate.setExpiryTime(1500);
        assertTrue(consentExpiryTrackerDAO.claimDueExpiry(connection, candidate, 2000));
    }

    @Test
    public void twoConnectionsOnlyOneWinsAfterCommit() throws Exception {

        concurrentClaim(false);
    }

    @Test
    public void twoConnectionsRollbackAllowsWaitingClaim() throws Exception {

        concurrentClaim(true);
    }

    @Test
    public void reconciliationChecksTenantAndObservedDeadlineAndCanRollback() throws Exception {

        consentExpiryTrackerDAO.upsertExpiry(connection, ORG_ID, CONSENT_ID, 1498);
        ConsentExpiryRecord candidate = consentExpiryTrackerDAO.findExpiry(connection, ORG_ID, CONSENT_ID);
        candidate.setOrgId("another-tenant");
        assertFalse(consentExpiryTrackerDAO.reconcileExpiry(connection, candidate, 1000));
        candidate.setOrgId(ORG_ID);
        connection.setAutoCommit(false);
        assertTrue(consentExpiryTrackerDAO.reconcileExpiry(connection, candidate, 1000));
        assertFalse(consentExpiryTrackerDAO.reconcileExpiry(connection, candidate, 3000));
        connection.rollback();
        assertEquals(consentExpiryTrackerDAO.findExpiry(connection, ORG_ID, CONSENT_ID).getExpiryTime(), 1498L);
        assertTrue(consentExpiryTrackerDAO.reconcileExpiry(connection, candidate, 1000));
        connection.commit();
        assertEquals(consentExpiryTrackerDAO.findExpiry(connection, ORG_ID, CONSENT_ID).getExpiryTime(), 1000L);
    }

    @Test
    public void sqlFailuresAreWrappedAndInvalidPageSizeIsRejected() throws Exception {

        ConsentExpiryRecord candidate = new ConsentExpiryRecord();
        expectThrows(IllegalArgumentException.class,
                () -> consentExpiryTrackerDAO.findDueExpiries(connection, 1000, 0, candidate));
        connection.close();
        Class<ConsentExpiryDataAccessException> type = ConsentExpiryDataAccessException.class;
        assertTrue(expectThrows(type, () -> consentExpiryTrackerDAO.reconcileExpiry(connection, candidate, 1000))
                .getCause() instanceof SQLException);
        expectThrows(type, () -> consentExpiryTrackerDAO.findExpiry(connection, ORG_ID, CONSENT_ID));
        expectThrows(type, () -> consentExpiryTrackerDAO.findDueExpiries(connection, 1000, 10, candidate));
        expectThrows(type, () -> consentExpiryTrackerDAO.claimDueExpiry(connection, candidate, 1000));
        expectThrows(type, () -> consentExpiryTrackerDAO.upsertExpiry(connection, ORG_ID, CONSENT_ID, 1000));
        expectThrows(type, () -> consentExpiryTrackerDAO.deleteExpiry(connection, CONSENT_ID));
        expectThrows(type, () -> consentExpiryTrackerDAO.findDueExpiries(connection, 1000, 10));
    }

    private void concurrentClaim(boolean rollback) throws Exception {

        consentExpiryTrackerDAO.upsertExpiry(connection, ORG_ID, CONSENT_ID, 1000);
        ConsentExpiryRecord candidate = consentExpiryTrackerDAO.findExpiry(connection, ORG_ID, CONSENT_ID);
        connection.setAutoCommit(false);
        assertTrue(consentExpiryTrackerDAO.claimDueExpiry(connection, candidate, 2000));
        ExecutorService executor = Executors.newSingleThreadExecutor();
        CountDownLatch started = new CountDownLatch(1);
        try (Connection competitor = DriverManager.getConnection(connection.getMetaData().getURL())) {
            competitor.setAutoCommit(false);
            Future<Boolean> claim = executor.submit(() -> {
                started.countDown();
                boolean won = consentExpiryTrackerDAO.claimDueExpiry(competitor, candidate, 2000);
                competitor.commit();
                return won;
            });
            assertTrue(started.await(5, TimeUnit.SECONDS));
            expectThrows(TimeoutException.class,
                    () -> claim.get(100, TimeUnit.MILLISECONDS));
            if (rollback) {
                connection.rollback();
            } else {
                connection.commit();
            }
            assertEquals(claim.get(5, TimeUnit.SECONDS).booleanValue(), rollback);
        } finally {
            connection.rollback();
            executor.shutdownNow();
        }
    }
}
