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

import org.h2.jdbcx.JdbcDataSource;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import org.wso2.dpdp.accelerator.common.persistence.JDBCPersistenceManager;
import org.wso2.dpdp.accelerator.consent.extensions.dao.ConsentExpiryTrackerDAO;
import org.wso2.dpdp.accelerator.consent.extensions.dao.exceptions.ConsentExpiryDataAccessException;
import org.wso2.dpdp.accelerator.consent.extensions.service.exception.ConsentExtensionsServiceException;
import org.wso2.dpdp.accelerator.consent.extensions.dao.models.ConsentExpiryRecord;

import java.lang.reflect.Field;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertSame;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.expectThrows;

/**
 * Runs the real {@link org.wso2.dpdp.accelerator.common.util.DatabaseUtils#executeInTransaction}
 * against an in-memory H2 database with a mocked DAO underneath - same pattern as
 * {@code ComplaintServiceImplTest}. Commit/rollback/close mechanics themselves are proven once,
 * centrally, by {@code DatabaseUtilsTest}; these tests only need to check that this service wires
 * the DAO and propagates its exceptions correctly, plus that each call takes exactly one
 * connection from the pool.
 */
public class ConsentExpiryServiceImplTest {

    private static final String ORG_ID = "tenant-a.com";
    private static final String CONSENT_ID = "consent-1234";

    private static final AtomicInteger CONNECTION_COUNT = new AtomicInteger();

    @Mock
    private ConsentExpiryTrackerDAO consentExpiryTrackerDAO;

    private ConsentExpiryServiceImpl consentExpiryService;

    /** Delegates to a real H2 {@link JdbcDataSource}, counting every real connection handed out. */
    private static final class CountingDataSource implements javax.sql.DataSource {

        private final JdbcDataSource delegate = new JdbcDataSource();

        void setURL(String url) {
            delegate.setURL(url);
        }

        void setUser(String user) {
            delegate.setUser(user);
        }

        void setPassword(String password) {
            delegate.setPassword(password);
        }

        @Override
        public Connection getConnection() throws SQLException {
            CONNECTION_COUNT.incrementAndGet();
            return delegate.getConnection();
        }

        @Override
        public Connection getConnection(String username, String password) throws SQLException {
            CONNECTION_COUNT.incrementAndGet();
            return delegate.getConnection(username, password);
        }

        @Override
        public java.io.PrintWriter getLogWriter() throws SQLException {
            return delegate.getLogWriter();
        }

        @Override
        public void setLogWriter(java.io.PrintWriter out) throws SQLException {
            delegate.setLogWriter(out);
        }

        @Override
        public void setLoginTimeout(int seconds) throws SQLException {
            delegate.setLoginTimeout(seconds);
        }

        @Override
        public int getLoginTimeout() throws SQLException {
            return delegate.getLoginTimeout();
        }

        @Override
        public java.util.logging.Logger getParentLogger() throws java.sql.SQLFeatureNotSupportedException {
            return delegate.getParentLogger();
        }

        @Override
        public <T> T unwrap(Class<T> iface) throws SQLException {
            return delegate.unwrap(iface);
        }

        @Override
        public boolean isWrapperFor(Class<?> iface) throws SQLException {
            return delegate.isWrapperFor(iface);
        }
    }

    @BeforeClass
    static void pointPersistenceManagerAtAnInMemoryDatabase() throws Exception {
        CountingDataSource dataSource = new CountingDataSource();
        dataSource.setURL("jdbc:h2:mem:consent_expiry_service_test;DB_CLOSE_DELAY=-1");
        dataSource.setUser("sa");
        dataSource.setPassword("");
        setManagerDataSource(dataSource);
    }

    @AfterClass
    static void clearPersistenceManagerDataSource() throws Exception {
        setManagerDataSource(null);
    }

    private static void setManagerDataSource(Object dataSource) throws Exception {
        Field field = JDBCPersistenceManager.class.getDeclaredField("dataSource");
        field.setAccessible(true);
        field.set(null, dataSource);
    }

    @BeforeMethod
    public void setUp() {

        MockitoAnnotations.openMocks(this);
        consentExpiryService = new ConsentExpiryServiceImpl(consentExpiryTrackerDAO);
        CONNECTION_COUNT.set(0);
    }

    @Test
    public void trackExpiryUpsertsThroughOneConnection() {

        consentExpiryService.trackExpiry(ORG_ID, CONSENT_ID, 5000L);

        verify(consentExpiryTrackerDAO).upsertExpiry(any(Connection.class), eq(ORG_ID), eq(CONSENT_ID), eq(5000L));
        assertEquals(CONNECTION_COUNT.get(), 1);
    }

    @Test
    public void untrackExpiryDeletesThroughOneConnection() {

        consentExpiryService.untrackExpiry(ORG_ID, CONSENT_ID);

        verify(consentExpiryTrackerDAO).deleteExpiry(any(Connection.class), eq(CONSENT_ID));
        assertEquals(CONNECTION_COUNT.get(), 1);
    }

    @Test
    public void trackExpiryTranslatesDaoFailureIntoServiceException() {

        ConsentExpiryDataAccessException failure = new ConsentExpiryDataAccessException("boom", null);
        doThrow(failure).when(consentExpiryTrackerDAO).upsertExpiry(any(Connection.class), eq(ORG_ID),
                eq(CONSENT_ID), eq(5000L));

        ConsentExtensionsServiceException actual = expectThrows(ConsentExtensionsServiceException.class,
                () -> consentExpiryService.trackExpiry(ORG_ID, CONSENT_ID, 5000L));
        assertSame(actual.getCause(), failure);
    }

    @Test
    public void claimExpiryIfDueAndReconcileExpiryUseTheCallersConnectionDirectly() {

        Connection connection = mock(Connection.class);
        ConsentExpiryRecord candidate = new ConsentExpiryRecord();
        when(consentExpiryTrackerDAO.claimDueExpiry(connection, candidate, 1000)).thenReturn(true);
        when(consentExpiryTrackerDAO.reconcileExpiry(connection, candidate, 2000)).thenReturn(true);

        assertTrue(consentExpiryService.claimExpiryIfDue(connection, candidate, 1000));
        assertTrue(consentExpiryService.reconcileExpiry(connection, candidate, 2000));

        // Neither method opens its own connection - both participate in the caller's transaction.
        verifyNoInteractions(connection);
        assertEquals(CONNECTION_COUNT.get(), 0);
    }

    @Test
    public void findExpiryAndFindDueExpiriesReadThroughOneConnectionEach() {

        ConsentExpiryRecord candidate = new ConsentExpiryRecord();
        when(consentExpiryTrackerDAO.findExpiry(any(Connection.class), eq(ORG_ID), eq(CONSENT_ID)))
                .thenReturn(candidate);
        List<ConsentExpiryRecord> page = Collections.singletonList(candidate);
        when(consentExpiryTrackerDAO.findDueExpiries(any(Connection.class), eq(1000L), eq(10), eq(candidate)))
                .thenReturn(page);

        assertSame(consentExpiryService.findExpiry(ORG_ID, CONSENT_ID), candidate);
        assertEquals(consentExpiryService.findDueExpiries(1000, 10, candidate), page);
        assertEquals(CONNECTION_COUNT.get(), 2);
    }

    @Test
    public void findExpiryAndFindDueExpiriesTranslateDaoFailureIntoServiceException() {

        ConsentExpiryDataAccessException failure = new ConsentExpiryDataAccessException("failed", null);
        when(consentExpiryTrackerDAO.findExpiry(any(Connection.class), eq(ORG_ID), eq(CONSENT_ID)))
                .thenThrow(failure);
        when(consentExpiryTrackerDAO.findDueExpiries(any(Connection.class), eq(1000L), eq(10), any()))
                .thenThrow(failure);

        ConsentExtensionsServiceException findExpiryFailure = expectThrows(ConsentExtensionsServiceException.class,
                () -> consentExpiryService.findExpiry(ORG_ID, CONSENT_ID));
        assertSame(findExpiryFailure.getCause(), failure);
        ConsentExtensionsServiceException findDueFailure = expectThrows(ConsentExtensionsServiceException.class,
                () -> consentExpiryService.findDueExpiries(1000, 10, new ConsentExpiryRecord()));
        assertSame(findDueFailure.getCause(), failure);
    }
}
