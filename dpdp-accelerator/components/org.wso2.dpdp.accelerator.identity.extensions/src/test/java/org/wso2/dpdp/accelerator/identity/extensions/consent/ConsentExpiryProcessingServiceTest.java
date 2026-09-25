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

package org.wso2.dpdp.accelerator.identity.extensions.consent;

import org.h2.jdbcx.JdbcDataSource;
import org.h2.tools.RunScript;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;
import org.wso2.carbon.consent.mgt.core.PrivilegedConsentManager;
import org.wso2.carbon.consent.mgt.core.model.ConsentPurpose;
import org.wso2.carbon.consent.mgt.core.model.Receipt;
import org.wso2.carbon.consent.mgt.core.model.ReceiptService;
import org.wso2.dpdp.accelerator.common.config.DPDPConfigurationService;
import org.wso2.dpdp.accelerator.common.persistence.JDBCPersistenceManager;
import org.wso2.dpdp.accelerator.consent.extensions.dao.exceptions.ConsentExpiryDataAccessException;
import org.wso2.dpdp.accelerator.consent.extensions.dao.exceptions.ConsentHistoryDataRetrievalException;
import org.wso2.dpdp.accelerator.consent.extensions.dao.impl.ConsentExpiryTrackerDAOImpl;
import org.wso2.dpdp.accelerator.consent.extensions.dao.models.ConsentExpiryRecord;
import org.wso2.dpdp.accelerator.consent.extensions.internal.DPDPConsentExtensionDataHolder;
import org.wso2.dpdp.accelerator.consent.extensions.service.ConsentExpiryService;
import org.wso2.dpdp.accelerator.consent.extensions.service.ConsentHistoryService;
import org.wso2.dpdp.accelerator.consent.extensions.service.exception.ConsentExtensionsServiceException;
import org.wso2.dpdp.accelerator.consent.extensions.service.impl.ConsentExpiryServiceImpl;
import org.wso2.dpdp.accelerator.consent.extensions.service.impl.ConsentHistoryServiceImpl;
import org.wso2.dpdp.accelerator.event.notifications.common.listener.DPDPLifecycleEventListener;
import org.wso2.dpdp.accelerator.event.notifications.dao.impl.DeliveryDAOImpl;
import org.wso2.dpdp.accelerator.event.notifications.dao.impl.EventDAOImpl;
import org.wso2.dpdp.accelerator.event.notifications.dao.impl.SubscriptionDAOImpl;
import org.wso2.dpdp.accelerator.event.notifications.dao.impl.TopicDAOImpl;
import org.wso2.dpdp.accelerator.event.notifications.service.impl.EventPublishServiceImpl;
import org.wso2.dpdp.accelerator.event.notifications.service.listener.DPDPLifecycleEventPublisher;
import org.wso2.dpdp.accelerator.identity.extensions.internal.DPDPIdentityExtensionDataHolder;

import java.io.InputStreamReader;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import javax.sql.DataSource;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertSame;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.expectThrows;

/** Runs the actual service/DAO chain against transactional tables, including real event fan-out. */
public class ConsentExpiryProcessingServiceTest {

    private DataSource dataSource;
    private String mysqlSchema;
    private Connection schemaConnection;
    private Object previousDataSource;
    private Object previousManager;
    private DPDPConfigurationService previousConfiguration;
    private DPDPConfigurationService configuration;
    private ConsentHistoryServiceImpl history;
    private ConsentExpiryServiceImpl expiry;
    private EventDAOImpl events;
    private DeliveryDAOImpl deliveries;
    private ConsentExpiryProcessingService processing;
    private DPDPLifecycleEventPublisher publisher;
    private PrivilegedConsentManager consentManager;
    private ConsentExpiryRecord candidate;
    private Receipt receipt;

    @BeforeMethod
    public void setUp() throws Exception {

        String mysqlUrl = System.getProperty("consentExpiryTestMysqlUrl");
        if (mysqlUrl == null) {
            JdbcDataSource h2 = new JdbcDataSource();
            h2.setURL("jdbc:h2:mem:" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=2000");
            dataSource = h2;
            mysqlSchema = null;
        } else {
            // The explicit opt-in URL must target a disposable test server. Only our random schema is dropped.
            mysqlSchema = "expiry_test_" + UUID.randomUUID().toString().replace("-", "");
            String user = System.getProperty("consentExpiryTestMysqlUser", "root");
            String password = System.getProperty("consentExpiryTestMysqlPassword", "");
            try (Connection connection = DriverManager.getConnection(mysqlUrl, user, password);
                    Statement statement = connection.createStatement()) {
                statement.execute("CREATE DATABASE " + mysqlSchema);
            }
            dataSource = mock(DataSource.class);
            when(dataSource.getConnection()).thenAnswer(invocation -> {
                Connection connection = DriverManager.getConnection(mysqlUrl, user, password);
                connection.setCatalog(mysqlSchema);
                try (Statement statement = connection.createStatement()) {
                    statement.execute("SET SESSION innodb_lock_wait_timeout = 5");
                }
                return connection;
            });
        }
        previousDataSource = staticField("dataSource").get(null);
        previousManager = staticField("instance").get(null);
        staticField("dataSource").set(null, dataSource);
        staticField("instance").set(null, null);
        configuration = mock(DPDPConfigurationService.class);
        when(configuration.isConsentHistorySnapshotEnabled()).thenReturn(true);
        when(configuration.isEventNotificationLifecycleEventsPublishingEnabled()).thenReturn(true);
        previousConfiguration = DPDPConsentExtensionDataHolder.getInstance().getConfigurationService();
        DPDPConsentExtensionDataHolder.getInstance().setConfigurationService(configuration);
        schemaConnection = dataSource.getConnection();
        initializeSchema();
        expiry = new ConsentExpiryServiceImpl();
        history = spy(new ConsentHistoryServiceImpl());
        events = spy(new EventDAOImpl());
        deliveries = spy(new DeliveryDAOImpl());
        publisher = new DPDPLifecycleEventPublisher(new EventPublishServiceImpl(events, new TopicDAOImpl(),
                deliveries, null, new SubscriptionDAOImpl()));
        consentManager = mock(PrivilegedConsentManager.class);
        receipt = new Receipt();
        receipt.setState("EXPIRED");
        receipt.setExpiryTime(new Timestamp(1000));
        ConsentPurpose purpose = new ConsentPurpose();
        purpose.setPurpose("marketing");
        ReceiptService service = new ReceiptService();
        service.setPurposes(Collections.singletonList(purpose));
        receipt.setServices(Collections.singletonList(service));
        when(consentManager.getReceiptWithExtendedSchema("consent")).thenReturn(receipt);
        when(consentManager.getConsentAuthorizations("consent")).thenReturn(Collections.emptyList());
        candidate = expiry.findExpiry("carbon.super", "consent");
        processing = new ConsentExpiryProcessingService(expiry, history, consentManager, configuration,
                () -> publisher);
    }

    @AfterMethod(alwaysRun = true)
    public void tearDown() throws Exception {

        staticField("dataSource").set(null, previousDataSource);
        staticField("instance").set(null, previousManager);
        DPDPConsentExtensionDataHolder.getInstance().setConfigurationService(previousConfiguration);
        if (dataSource != null) {
            try (Connection connection = dataSource.getConnection();
                    Statement statement = connection.createStatement()) {
                statement.execute(mysqlSchema == null ? "DROP ALL OBJECTS" : "DROP DATABASE " + mysqlSchema);
            } finally {
                if (schemaConnection != null) {
                    schemaConnection.close();
                }
            }
        }
    }

    @Test
    public void commitsTrackerAuditSnapshotEventPurposesAndBothDeliveryTypesTogether() throws Exception {

        assertTrue(processing.process(candidate, 2000));
        assertEquals(count("DPDP_CONSENT_EXPIRY_TRACKER"), 0);
        assertPersisted(1);
        assertFalse(processing.process(candidate, 2000));
        assertPersisted(1);
    }

    @DataProvider
    public Object[][] failureStages() {

        return new Object[][] { { "audit" }, { "snapshot" }, { "event" }, { "purposes" },
                { "webhook" }, { "poll" } };
    }

    @Test(dataProvider = "failureStages")
    public void failureAtAnyPersistenceStageRollsBackEverythingAndAllowsRetry(String stage) throws Exception {

        if ("audit".equals(stage)) {
            doAnswer(call -> {
                call.callRealMethod();
                throw new IllegalStateException("audit failure");
            })
                    .when(history).recordStatusAudit(any(Connection.class), anyString(), anyString(), any(),
                            anyString(), any(), anyString());
        } else if ("snapshot".equals(stage)) {
            doAnswer(call -> {
                call.callRealMethod();
                throw new IllegalStateException("snapshot failure");
            })
                    .when(history).recordHistorySnapshot(any(Connection.class), anyString(), anyString(), any(),
                            anyString(), anyString());
        } else if ("event".equals(stage)) {
            doAnswer(call -> {
                call.callRealMethod();
                throw new IllegalStateException("event failure");
            })
                    .when(events).addEvent(any(Connection.class), any());
        } else if ("purposes".equals(stage)) {
            doAnswer(call -> {
                call.callRealMethod();
                throw new IllegalStateException("purposes failure");
            })
                    .when(events).addEventPurposes(any(Connection.class), anyString(), any());
        } else if ("webhook".equals(stage)) {
            doAnswer(call -> {
                call.callRealMethod();
                throw new IllegalStateException("webhook failure");
            })
                    .when(deliveries).addWebhookDelivery(any(Connection.class), any());
        } else {
            doAnswer(call -> {
                call.callRealMethod();
                throw new IllegalStateException("poll failure");
            })
                    .when(deliveries).addPollDelivery(any(Connection.class), any());
        }
        expectThrows(RuntimeException.class, () -> processing.process(candidate, 2000));
        assertEquals(count("DPDP_CONSENT_EXPIRY_TRACKER"), 1);
        assertPersisted(0);
        reset(history, events, deliveries);
        assertTrue(processing.process(candidate, 2000));
        assertPersisted(1);
    }

    @Test
    public void disabledFeaturesDoNotRequirePublisherOrSnapshot() throws Exception {

        when(configuration.isConsentHistorySnapshotEnabled()).thenReturn(false);
        when(configuration.isEventNotificationLifecycleEventsPublishingEnabled()).thenReturn(false);
        publisher = null;
        assertTrue(processing.process(candidate, 2000));
        assertEquals(count("DPDP_CONSENT_STATUS_AUDIT"), 1);
        assertEquals(count("DPDP_CONSENT_HISTORY"), 0);
        assertEquals(count("EVENT"), 0);
        assertEquals(count("DPDP_CONSENT_EXPIRY_TRACKER"), 0);
    }

    @Test
    public void missingPublisherAndPreparationFailureLeaveTrackerPending() throws Exception {

        publisher = null;
        expectThrows(IllegalStateException.class, () -> processing.process(candidate, 2000));
        assertEquals(count("DPDP_CONSENT_EXPIRY_TRACKER"), 1);
        when(consentManager.getReceiptWithExtendedSchema("consent"))
                .thenThrow(new IllegalStateException("source unavailable"));
        expectThrows(IllegalStateException.class, () -> processing.process(candidate, 2000));
        assertPersisted(0);
    }

    @Test
    public void staleNotDueAndAbsentCandidatesAreSkipped() throws Exception {

        assertFalse(processing.process(null, 2000));
        assertFalse(processing.process(candidate, 999));
        receipt.setExpiryTime(new Timestamp(3000));
        assertFalse(processing.process(candidate, 2000));
        receipt.setExpiryTime(null);
        assertFalse(processing.process(candidate, 2000));
        receipt.setExpiryTime(new Timestamp(1000));
        receipt.setState("REVOKED");
        assertFalse(processing.process(candidate, 2000));
        assertEquals(count("DPDP_CONSENT_EXPIRY_TRACKER"), 1);
        assertPersisted(0);
    }

    @Test
    public void precisionMismatchIsRepairedThenExpiredExactlyOnce() throws Exception {

        expiry.trackExpiry("carbon.super", "consent", 1498);
        ConsentExpiryRecord original = expiry.findExpiry("carbon.super", "consent");
        assertFalse(processing.process(original, 2000));
        assertPersisted(0);
        ConsentExpiryRecord corrected = expiry.findExpiry("carbon.super", "consent");
        assertEquals(corrected.getExpiryTime(), 1000L);
        assertTrue(processing.process(corrected, 2000));
        assertFalse(processing.process(corrected, 2000));
        assertPersisted(1);
        assertEquals(count("DPDP_CONSENT_EXPIRY_TRACKER"), 0);
    }

    @Test
    public void renewedDeadlineIsRepairedWithoutExpiringEarly() throws Exception {

        receipt.setExpiryTime(new Timestamp(3000));
        receipt.setState("ACTIVE");
        assertFalse(processing.process(candidate, 2000));
        ConsentExpiryRecord corrected = expiry.findExpiry("carbon.super", "consent");
        assertEquals(corrected.getExpiryTime(), 3000L);
        assertFalse(processing.process(corrected, 2000));
        assertPersisted(0);
        receipt.setState("EXPIRED");
        assertTrue(processing.process(corrected, 4000));
        assertPersisted(1);
    }

    @Test
    public void reconciliationDoesNotOverwriteChangedTrackerOrRecreateClaimedRow() throws Exception {

        receipt.setExpiryTime(new Timestamp(1500));
        expiry.trackExpiry("carbon.super", "consent", 3000);
        assertFalse(processing.process(candidate, 2000));
        assertEquals(expiry.findExpiry("carbon.super", "consent").getExpiryTime(), 3000L);
        expiry.untrackExpiry("carbon.super", "consent");
        assertFalse(processing.process(candidate, 2000));
        assertEquals(count("DPDP_CONSENT_EXPIRY_TRACKER"), 0);
        assertPersisted(0);
    }

    private void assertPersisted(int expected) throws Exception {

        for (String table : new String[] { "DPDP_CONSENT_STATUS_AUDIT", "DPDP_CONSENT_HISTORY", "EVENT",
                "EVENT_PURPOSE", "WEBHOOK_DELIVERY", "POLL_DELIVERY" }) {
            assertEquals(count(table), expected, table);
        }
    }

    private int count(String table) throws Exception {

        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement();
                ResultSet result = statement.executeQuery("SELECT COUNT(*) FROM " + table)) {
            result.next();
            return result.getInt(1);
        }
    }

    private Field staticField(String name) throws Exception {

        Field field = JDBCPersistenceManager.class.getDeclaredField(name);
        field.setAccessible(true);
        return field;
    }

    @Test
    public void commitResponseLossDoesNotDuplicatePersistedExpiry() throws Exception {

        DataSource uncertainSource = mock(DataSource.class);
        when(uncertainSource.getConnection()).thenAnswer(invocation -> {
            Connection connection = spy(dataSource.getConnection());
            doAnswer(commit -> {
                commit.callRealMethod();
                throw new SQLException("Commit response lost");
            }).when(connection).commit();
            return connection;
        });
        staticField("dataSource").set(null, uncertainSource);
        expectThrows(RuntimeException.class, () -> processing.process(candidate, 2000));
        assertEquals(count("DPDP_CONSENT_EXPIRY_TRACKER"), 0);
        assertPersisted(1);
        staticField("dataSource").set(null, dataSource);
        assertFalse(processing.process(candidate, 2000));
        assertPersisted(1);
    }

    @Test
    public void failedCommitRollsBackAndDaoFailurePropagatesDirectly() throws Exception {

        DataSource failingSource = mock(DataSource.class);
        when(failingSource.getConnection()).thenAnswer(invocation -> {
            Connection connection = spy(dataSource.getConnection());
            doThrow(new SQLException("Commit failed")).when(connection).commit();
            return connection;
        });
        staticField("dataSource").set(null, failingSource);
        expectThrows(RuntimeException.class, () -> processing.process(candidate, 2000));
        assertEquals(count("DPDP_CONSENT_EXPIRY_TRACKER"), 1);
        assertPersisted(0);
        staticField("dataSource").set(null, dataSource);
        ConsentExtensionsServiceException failure =
                new ConsentExtensionsServiceException("Audit lookup failed",
                        new ConsentHistoryDataRetrievalException("Audit lookup failed", new SQLException("DB failure")));
        doThrow(failure).when(history).getLastKnownStatus(any(Connection.class),
                anyString(), anyString());
        RuntimeException actual = expectThrows(RuntimeException.class, () -> processing.process(candidate, 2000));
        assertSame(actual, failure);
        assertEquals(count("DPDP_CONSENT_EXPIRY_TRACKER"), 1);
        assertPersisted(0);
    }

    @Test
    public void listenerEntryUsesTheSameAtomicProcessingAndHonoursDisable() throws Exception {

        DPDPIdentityExtensionDataHolder holder =
                DPDPIdentityExtensionDataHolder.getInstance();
        DPDPConfigurationService oldConfig = holder.getConfigurationService();
        ConsentExpiryService oldExpiry =
                holder.getConsentExpiryService();
        ConsentHistoryService oldHistory =
                holder.getConsentHistoryService();
        PrivilegedConsentManager oldManager = holder.getPrivilegedConsentManager();
        DPDPLifecycleEventListener oldPublisher =
                holder.getLifecycleEventListener();
        try {
            holder.setConfigurationService(configuration);
            holder.setConsentExpiryService(expiry);
            holder.setConsentHistoryService(history);
            holder.setPrivilegedConsentManager(consentManager);
            holder.setLifecycleEventListener(publisher);
            DPDPConsentExpiryReconciler.expireConsentIfDue("carbon.super", "consent");
            assertEquals(count("DPDP_CONSENT_EXPIRY_TRACKER"), 1);
            when(configuration.isConsentExpiryEnabled()).thenReturn(true);
            DPDPConsentExpiryReconciler.expireConsentIfDue("carbon.super", "consent");
            assertEquals(count("DPDP_CONSENT_EXPIRY_TRACKER"), 0);
            assertPersisted(1);
        } finally {
            holder.setConfigurationService(oldConfig);
            holder.setConsentExpiryService(oldExpiry);
            holder.setConsentHistoryService(oldHistory);
            holder.setPrivilegedConsentManager(oldManager);
            holder.setLifecycleEventListener(oldPublisher);
        }
    }

    @Test
    public void trackerWritesUsePersistedPrecision() throws Exception {

        DPDPIdentityExtensionDataHolder holder = DPDPIdentityExtensionDataHolder.getInstance();
        DPDPConfigurationService oldConfig = holder.getConfigurationService();
        ConsentExpiryService oldExpiry = holder.getConsentExpiryService();
        ConsentHistoryService oldHistory = holder.getConsentHistoryService();
        PrivilegedConsentManager oldManager = holder.getPrivilegedConsentManager();
        try {
            holder.setConfigurationService(configuration);
            holder.setConsentExpiryService(expiry);
            holder.setConsentHistoryService(mock(ConsentHistoryService.class));
            holder.setPrivilegedConsentManager(consentManager);
            DPDPConsentManagementListener listener = new DPDPConsentManagementListener();
            // Exercise the shared tracker helper without the hooks' unrelated Carbon user context.
            java.lang.reflect.Method track = DPDPConsentManagementListener.class.getDeclaredMethod(
                    "trackExpiry", String.class, String.class, Timestamp.class);
            track.setAccessible(true);
            track.invoke(listener, "consent", "carbon.super", new Timestamp(1498));
            assertEquals(expiry.findExpiry("carbon.super", "consent").getExpiryTime(), 1000L);

            receipt.setExpiryTime(new Timestamp(3000));
            track.invoke(listener, "consent", "carbon.super", new Timestamp(3498));
            assertEquals(expiry.findExpiry("carbon.super", "consent").getExpiryTime(), 3000L);
        } finally {
            holder.setConfigurationService(oldConfig);
            holder.setConsentExpiryService(oldExpiry);
            holder.setConsentHistoryService(oldHistory);
            holder.setPrivilegedConsentManager(oldManager);
        }
    }

    private void initializeSchema() throws Exception {

        Connection connection = schemaConnection;
        String dialect = mysqlSchema == null ? "h2" : "mysql";
        for (String resource : new String[] { "consent-history/" + dialect + ".sql",
                "event-notification/" + dialect + ".sql" }) {
            try (InputStreamReader reader = new InputStreamReader(getClass().getClassLoader()
                    .getResourceAsStream(resource), StandardCharsets.UTF_8)) {
                RunScript.execute(connection, reader);
            }
        }
        try (Statement statement = connection.createStatement()) {
            statement.execute("INSERT INTO TOPIC (TOPIC_ID, ORG_ID, NAME, STATUS) "
                    + "VALUES ('topic', 'carbon.super', 'consent.expire', 'active')");
            for (String mode : new String[] { "webhook", "poll" }) {
                statement.execute("INSERT INTO SUBSCRIPTION (SUBSCRIPTION_ID, ORG_ID, NAME, GROUP_ID, "
                        + "STATUS, PURPOSE_FILTER_MODE, DELIVERY_MODE) VALUES ('" + mode
                        + "', 'carbon.super', '" + mode + "-sub', 'carbon.super', 'active', 'ALL', '" + mode + "')");
                statement.execute("INSERT INTO SUBSCRIPTION_TOPIC (ORG_ID, SUBSCRIPTION_ID, TOPIC_ID) "
                        + "VALUES ('carbon.super', '" + mode + "', 'topic')");
            }
        }
        new ConsentExpiryTrackerDAOImpl().upsertExpiry(connection, "carbon.super", "consent", 1000);
    }

    @Test
    public void competingTransactionsWaitThenObserveCommitOrRollback() throws Exception {

        for (boolean rollback : new boolean[] { false, true }) {
            ConsentExpiryTrackerDAOImpl dao = new ConsentExpiryTrackerDAOImpl();
            try (Connection seed = dataSource.getConnection()) {
                dao.upsertExpiry(seed, candidate.getOrgId(), candidate.getConsentId(), candidate.getExpiryTime());
            }
            ExecutorService executor = Executors.newSingleThreadExecutor();
            CountDownLatch started = new CountDownLatch(1);
            try (Connection winner = dataSource.getConnection(); Connection waiter = dataSource.getConnection()) {
                winner.setAutoCommit(false);
                waiter.setAutoCommit(false);
                assertTrue(dao.claimDueExpiry(winner, candidate, 2000));
                Future<Boolean> claim = executor.submit(() -> {
                    started.countDown();
                    boolean claimed = dao.claimDueExpiry(waiter, candidate, 2000);
                    waiter.commit();
                    return claimed;
                });
                assertTrue(started.await(5, TimeUnit.SECONDS));
                expectThrows(TimeoutException.class,
                        () -> claim.get(100, TimeUnit.MILLISECONDS));
                if (rollback) {
                    winner.rollback();
                } else {
                    winner.commit();
                }
                assertEquals(claim.get(5, TimeUnit.SECONDS).booleanValue(), rollback);
            } finally {
                executor.shutdownNow();
            }
        }
    }

    @Test
    public void cursorFetches250EqualDeadlinesDespiteDeletesAndOneRetainedFailure() throws Exception {

        ConsentExpiryTrackerDAOImpl dao = new ConsentExpiryTrackerDAOImpl();
        try (Connection connection = dataSource.getConnection()) {
            dao.deleteExpiry(connection, candidate.getConsentId());
            for (int i = 0; i < 250; i++) {
                dao.upsertExpiry(connection, "carbon.super", String.format("consent-%03d", i), 1000);
            }
        }
        ConsentExpiryRecord cursor = null;
        int fetched = 0;
        int batches = 0;
        while (true) {
            List<ConsentExpiryRecord> page = expiry.findDueExpiries(2000, 100, cursor);
            batches++;
            fetched += page.size();
            for (ConsentExpiryRecord record : page) {
                if (!"consent-000".equals(record.getConsentId())) {
                    try (Connection connection = dataSource.getConnection()) {
                        connection.setAutoCommit(false);
                        assertTrue(expiry.claimExpiryIfDue(connection, record, 2000));
                        connection.commit();
                    }
                }
                cursor = record;
            }
            if (page.size() < 100) {
                break;
            }
        }
        assertEquals(batches, 3);
        assertEquals(fetched, 250);
        assertEquals(count("DPDP_CONSENT_EXPIRY_TRACKER"), 1);
    }

    @Test
    public void lockTimeoutLeavesTheTrackerAvailableAfterWinnerRollsBack() throws Exception {

        ConsentExpiryTrackerDAOImpl dao = new ConsentExpiryTrackerDAOImpl();
        try (Connection winner = dataSource.getConnection(); Connection waiter = dataSource.getConnection()) {
            winner.setAutoCommit(false);
            waiter.setAutoCommit(false);
            try (Statement statement = waiter.createStatement()) {
                statement.execute(mysqlSchema == null ? "SET LOCK_TIMEOUT 100"
                        : "SET SESSION innodb_lock_wait_timeout = 1");
            }
            assertTrue(dao.claimDueExpiry(winner, candidate, 2000));
            expectThrows(ConsentExpiryDataAccessException.class, () -> dao.claimDueExpiry(waiter, candidate, 2000));
            waiter.rollback();
            winner.rollback();
        }
        assertTrue(processing.process(candidate, 2000));
        assertPersisted(1);
    }
}
