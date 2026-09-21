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
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import org.wso2.dpdp.accelerator.common.config.DPDPConfigurationService;
import org.wso2.dpdp.accelerator.common.persistence.JDBCPersistenceManager;
import org.wso2.dpdp.accelerator.consent.extensions.dao.ConsentHistoryDAO;
import org.wso2.dpdp.accelerator.consent.extensions.dao.exceptions.ConsentHistoryDataInsertionException;
import org.wso2.dpdp.accelerator.consent.extensions.dao.exceptions.ConsentHistoryDataRetrievalException;
import org.wso2.dpdp.accelerator.consent.extensions.service.exception.ConsentExtensionsServiceException;
import org.wso2.dpdp.accelerator.consent.extensions.dao.models.ConsentHistoryRecord;
import org.wso2.dpdp.accelerator.consent.extensions.dao.models.ConsentStatusAuditRecord;
import org.wso2.dpdp.accelerator.consent.extensions.internal.DPDPConsentExtensionDataHolder;
import org.wso2.dpdp.accelerator.consent.extensions.service.constants.ConsentHistoryServiceConstants.ActionType;
import org.wso2.dpdp.accelerator.consent.extensions.service.models.PagedResult;

import java.lang.reflect.Field;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertSame;
import static org.testng.Assert.expectThrows;

/**
 * Runs the real {@link org.wso2.dpdp.accelerator.common.util.DatabaseUtils#executeInTransaction}
 * against an in-memory H2 database with a mocked DAO underneath - same pattern as
 * {@code ComplaintServiceImplTest}. Commit/rollback/close mechanics themselves are proven once,
 * centrally, by {@code DatabaseUtilsTest}.
 */
public class ConsentHistoryServiceImplTest {

    private static final String TENANT_DOMAIN = "tenant-a.com";
    private static final String CONSENT_ID = "consent-1234";

    private static final AtomicInteger CONNECTION_COUNT = new AtomicInteger();

    @Mock
    private ConsentHistoryDAO consentHistoryDAO;

    @Mock
    private DPDPConfigurationService configurationService;

    private ConsentHistoryServiceImpl consentHistoryService;

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
        dataSource.setURL("jdbc:h2:mem:consent_history_service_test;DB_CLOSE_DELAY=-1");
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
        DPDPConsentExtensionDataHolder.getInstance().setConfigurationService(configurationService);
        when(configurationService.isConsentHistorySnapshotEnabled()).thenReturn(true);
        consentHistoryService = new ConsentHistoryServiceImpl(consentHistoryDAO);
        CONNECTION_COUNT.set(0);
    }

    @Test
    public void recordStatusAuditInsertsThroughOneConnection() {

        consentHistoryService.recordStatusAudit(TENANT_DOMAIN, CONSENT_ID, "PENDING", "ACTIVE",
                ActionType.AUTHORIZE_APPROVE, "jdoe@carbon.super");

        ArgumentCaptor<ConsentStatusAuditRecord> captor = ArgumentCaptor.forClass(ConsentStatusAuditRecord.class);
        verify(consentHistoryDAO).insertStatusAudit(any(Connection.class), captor.capture());
        ConsentStatusAuditRecord record = captor.getValue();
        assertEquals(record.getConsentId(), CONSENT_ID);
        assertEquals(record.getOrgId(), TENANT_DOMAIN);
        assertEquals(record.getPreviousStatus(), "PENDING");
        assertEquals(record.getCurrentStatus(), "ACTIVE");
        assertEquals(record.getActionType(), "AUTHORIZE_APPROVE");
        assertEquals(record.getActionBy(), "jdoe@carbon.super");
        assertEquals(CONNECTION_COUNT.get(), 1);
    }

    @Test
    public void recordStatusAuditSkipsWhenStatusIsUnchanged() {

        consentHistoryService.recordStatusAudit(TENANT_DOMAIN, CONSENT_ID, "ACTIVE", "ACTIVE", ActionType.UPDATE,
                "jdoe@carbon.super");

        verify(consentHistoryDAO, never()).insertStatusAudit(any(Connection.class), any());
        // The skip happens before a connection is even acquired.
        assertEquals(CONNECTION_COUNT.get(), 0);
    }

    @Test
    public void recordStatusAuditInsertsWhenPreviousStatusIsNull() {

        consentHistoryService.recordStatusAudit(TENANT_DOMAIN, CONSENT_ID, null, "ACTIVE", ActionType.CREATE,
                "jdoe@carbon.super");

        ArgumentCaptor<ConsentStatusAuditRecord> captor = ArgumentCaptor.forClass(ConsentStatusAuditRecord.class);
        verify(consentHistoryDAO).insertStatusAudit(any(Connection.class), captor.capture());
        assertEquals(captor.getValue().getPreviousStatus(), null);
        assertEquals(captor.getValue().getCurrentStatus(), "ACTIVE");
    }

    @Test
    public void recordHistorySnapshotInsertsWhenEnabled() {

        consentHistoryService.recordHistorySnapshot(TENANT_DOMAIN, CONSENT_ID, ActionType.REVOKE,
                "{\"state\":\"ACTIVE\"}", "jdoe@carbon.super");

        ArgumentCaptor<ConsentHistoryRecord> captor = ArgumentCaptor.forClass(ConsentHistoryRecord.class);
        verify(consentHistoryDAO).insertHistorySnapshot(any(Connection.class), captor.capture());
        assertEquals(captor.getValue().getSnapshot(), "{\"state\":\"ACTIVE\"}");
        assertEquals(captor.getValue().getActionType(), "REVOKE");
    }

    @Test
    public void recordHistorySnapshotSkipsWhenDisabled() {

        when(configurationService.isConsentHistorySnapshotEnabled()).thenReturn(false);

        consentHistoryService.recordHistorySnapshot(TENANT_DOMAIN, CONSENT_ID, ActionType.REVOKE,
                "{\"state\":\"ACTIVE\"}", "jdoe@carbon.super");

        verify(consentHistoryDAO, never()).insertHistorySnapshot(any(Connection.class), any());
        assertEquals(CONNECTION_COUNT.get(), 0);
    }

    @Test
    public void getStatusAuditHistoryReturnsRecordsAndTotalCountThroughOneConnection() {

        List<ConsentStatusAuditRecord> records = Collections.singletonList(new ConsentStatusAuditRecord());
        when(consentHistoryDAO.getStatusAuditHistory(any(Connection.class), eq(TENANT_DOMAIN), eq(CONSENT_ID),
                eq(20), eq(0))).thenReturn(records);
        when(consentHistoryDAO.getStatusAuditHistoryCount(any(Connection.class), eq(TENANT_DOMAIN), eq(CONSENT_ID)))
                .thenReturn(1);

        PagedResult<ConsentStatusAuditRecord> result = consentHistoryService.getStatusAuditHistory(TENANT_DOMAIN,
                CONSENT_ID, 20, 0);

        assertEquals(result.getRecords(), records);
        assertEquals(result.getTotalCount(), 1);
        assertEquals(CONNECTION_COUNT.get(), 1);
    }

    @Test
    public void getConsentHistoryReturnsRecordsAndTotalCount() {

        List<ConsentHistoryRecord> records = Collections.singletonList(new ConsentHistoryRecord());
        when(consentHistoryDAO.getConsentHistory(any(Connection.class), eq(TENANT_DOMAIN), eq(CONSENT_ID), anyInt(),
                anyInt())).thenReturn(records);
        when(consentHistoryDAO.getConsentHistoryCount(any(Connection.class), eq(TENANT_DOMAIN), eq(CONSENT_ID)))
                .thenReturn(1);

        PagedResult<ConsentHistoryRecord> result = consentHistoryService.getConsentHistory(TENANT_DOMAIN, CONSENT_ID,
                20, 0);

        assertEquals(result.getRecords(), records);
        assertEquals(result.getTotalCount(), 1);
    }

    @Test
    public void resolveOrgIdDefaultsWhenTenantDomainIsNull() {

        when(consentHistoryDAO.getStatusAuditHistory(any(Connection.class), eq("carbon.super"), eq(CONSENT_ID),
                anyInt(), anyInt())).thenReturn(Collections.emptyList());

        consentHistoryService.getStatusAuditHistory(null, CONSENT_ID, 20, 0);

        verify(consentHistoryDAO).getStatusAuditHistory(any(Connection.class), eq("carbon.super"), eq(CONSENT_ID),
                eq(20), eq(0));
    }

    @Test
    public void recordStatusAuditTranslatesDaoFailureIntoServiceException() {

        ConsentHistoryDataInsertionException failure = new ConsentHistoryDataInsertionException("boom", null);
        Mockito.doThrow(failure).when(consentHistoryDAO)
                .insertStatusAudit(any(Connection.class), any(ConsentStatusAuditRecord.class));

        ConsentExtensionsServiceException actual = expectThrows(ConsentExtensionsServiceException.class,
                () -> consentHistoryService.recordStatusAudit(TENANT_DOMAIN, CONSENT_ID, "PENDING", "ACTIVE",
                        ActionType.AUTHORIZE_APPROVE, "jdoe@carbon.super"));
        assertSame(actual.getCause(), failure);
    }

    @Test
    public void getStatusAuditHistoryTranslatesDaoFailureWithTheEndpointsOwnErrorCode() {

        ConsentHistoryDataRetrievalException failure = new ConsentHistoryDataRetrievalException("boom", null);
        when(consentHistoryDAO.getStatusAuditHistory(any(Connection.class), eq(TENANT_DOMAIN), eq(CONSENT_ID),
                eq(20), eq(0))).thenThrow(failure);

        ConsentExtensionsServiceException actual = expectThrows(ConsentExtensionsServiceException.class,
                () -> consentHistoryService.getStatusAuditHistory(TENANT_DOMAIN, CONSENT_ID, 20, 0));

        // CH-00004 matches ConsentHistoryErrorCodes.SERVER_ERROR - the endpoint's mapper reads this
        // exception directly, so its code/status/description must already be exactly what the API
        // response should carry.
        assertEquals(actual.getErrorCode(), "CH-00004");
        assertEquals(actual.getHttpStatus(), 500);
        assertEquals(actual.getDescription(), "Could not retrieve the status-audit history.");
        assertSame(actual.getCause(), failure);
    }

    @Test
    public void getConsentHistoryTranslatesDaoFailureWithTheEndpointsOwnErrorCode() {

        ConsentHistoryDataRetrievalException failure = new ConsentHistoryDataRetrievalException("boom", null);
        when(consentHistoryDAO.getConsentHistory(any(Connection.class), eq(TENANT_DOMAIN), eq(CONSENT_ID), anyInt(),
                anyInt())).thenThrow(failure);

        ConsentExtensionsServiceException actual = expectThrows(ConsentExtensionsServiceException.class,
                () -> consentHistoryService.getConsentHistory(TENANT_DOMAIN, CONSENT_ID, 20, 0));

        assertEquals(actual.getErrorCode(), "CH-00004");
        assertEquals(actual.getHttpStatus(), 500);
        assertEquals(actual.getDescription(), "Could not retrieve the history.");
        assertSame(actual.getCause(), failure);
    }
}
