package org.wso2.dpdp.accelerator.event.notifications.dao.impl;

import org.mockito.Mockito;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import org.wso2.dpdp.accelerator.common.config.DPDPConfigurationService;
import org.wso2.dpdp.accelerator.common.persistence.JDBCPersistenceManager;

import javax.sql.DataSource;
import java.lang.reflect.Field;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.util.Collections;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.testng.Assert.expectThrows;

/** Exercises empty-result and validation paths across all read-only DAO entry points. */
public class DaoReadPathCoverageTest {

    private DataSource dataSource;
    private Connection connection;
    private PreparedStatement statement;
    private ResultSet resultSet;

    @BeforeMethod
    public void setUp() throws Exception {
        dataSource = Mockito.mock(DataSource.class);
        connection = Mockito.mock(Connection.class);
        statement = Mockito.mock(PreparedStatement.class);
        resultSet = Mockito.mock(ResultSet.class);
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.prepareStatement(anyString())).thenReturn(statement);
        when(statement.executeQuery()).thenReturn(resultSet);
        when(resultSet.next()).thenReturn(true, false);
        when(connection.getAutoCommit()).thenReturn(true);
        setManagerDataSource(dataSource);
    }

    @AfterMethod
    public void tearDown() throws Exception {
        setManagerDataSource(null);
    }

    @Test
    public void exercisesEmptyReadResultsAcrossDaos() throws Exception {
        SubscriptionDAOImpl subscriptions = new SubscriptionDAOImpl();
        subscriptions.getSubscriptionById("missing", "org");
        subscriptions.listSubscriptions("org", null, null, null, 20, 0, null);
        subscriptions.getSubscriptionsByOrgAndTopic("org", "topic", null);
        subscriptions.getPurposesBySubscriptionId("sub", "org");
        subscriptions.countActiveSubscriptionsForTopic("org", "topic");
        subscriptions.getPurposesBySubscriptionIds(java.util.Collections.singletonList("sub"));
        subscriptions.hasPendingOrInFlightDeliveries("sub", "org");
        subscriptions.getPendingSubscriptionsForRecovery(new Timestamp(System.currentTimeMillis()), 10);

        TopicDAOImpl topics = new TopicDAOImpl();
        topics.getTopicById("missing", "org");
        topics.getTopicByOrgAndName("org", "missing");
        topics.listTopics("org", null, null, 20, 0, null);

        EventDAOImpl events = new EventDAOImpl();
        events.getEventById("missing", "org");
        events.getEventPurposes("event");
        events.hasActiveEventsForTopic("topic");
        events.searchEvents("org", null, null, null, null, null, null, 20, 0);

        DeliveryDAOImpl deliveries = new DeliveryDAOImpl();
        setConfiguration(deliveries);
        deliveries.getWebhookDeliveryById("delivery", "org");
        deliveries.getPendingWebhookDeliveries(10);
        deliveries.getPendingWebhookDispatchContexts(10);
        deliveries.getStuckInFlightWebhookDispatchContexts(10, null);
        deliveries.getStuckInFlightWebhookDeliveries(10, null);
        deliveries.getPendingPollDeliveries("org", "group", 10);
        deliveries.getWebhookDeliveryAudits("delivery", "org");
        deliveries.getPollDeliveryById("delivery", "org");
        deliveries.getEventPayload("event");
        deliveries.getOrgDeliveryById("org", "delivery");
        deliveries.getSubscriptionDeliveryById("org", "sub", "delivery");
        deliveries.listSubscriptionDeliveries("org", "sub", 10, 0, new int[1]);
        deliveries.listOrgDeliveries("org", null, null, null, null, null, 10, 0, new int[1]);
        deliveries.listEventDeliveries("org", "event", 10, 0, new int[1]);
        deliveries.listOrgDeliveries("org", " delivered ", " sub ", " group ", "one, ,TWO", "a_%",
                10, 0, new int[1]);
        deliveries.listEventDeliveries("org", "event", 10, 0, null);
    }

    @Test
    public void translatesJdbcFailuresAcrossDaoReadPaths() throws Exception {
        when(statement.executeQuery()).thenThrow(new java.sql.SQLException("expected"));
        SubscriptionDAOImpl subscriptions = new SubscriptionDAOImpl();
        expectThrows(RuntimeException.class, () -> subscriptions.getSubscriptionById("sub", "org"));
        expectThrows(RuntimeException.class, () -> subscriptions.listSubscriptions("org", null, null, null, 20, 0, null));
        expectThrows(RuntimeException.class, () -> subscriptions.getSubscriptionsByOrgAndTopic("org", "topic", null));
        expectThrows(RuntimeException.class, () -> subscriptions.getPurposesBySubscriptionId("sub", "org"));
        expectThrows(RuntimeException.class, () -> subscriptions.countActiveSubscriptionsForTopic("org", "topic"));
        expectThrows(RuntimeException.class, () -> subscriptions.hasPendingOrInFlightDeliveries("sub", "org"));
        expectThrows(RuntimeException.class, () -> subscriptions.getPendingSubscriptionsForRecovery(new Timestamp(1), 10));

        TopicDAOImpl topics = new TopicDAOImpl();
        expectThrows(RuntimeException.class, () -> topics.getTopicById("topic", "org"));
        expectThrows(RuntimeException.class, () -> topics.getTopicByOrgAndName("org", "name"));
        expectThrows(RuntimeException.class, () -> topics.listTopics("org", null, null, 20, 0, null));

        EventDAOImpl events = new EventDAOImpl();
        expectThrows(RuntimeException.class, () -> events.getEventById("event", "org"));
        expectThrows(RuntimeException.class, () -> events.getEventPurposes("event"));
        expectThrows(RuntimeException.class, () -> events.hasActiveEventsForTopic("topic"));
        expectThrows(RuntimeException.class, () -> events.searchEvents("org", null, null, null, null, null, null, 20, 0));

        DeliveryDAOImpl deliveries = new DeliveryDAOImpl();
        setConfiguration(deliveries);
        expectThrows(RuntimeException.class, () -> deliveries.getWebhookDeliveryById("delivery", "org"));
        expectThrows(RuntimeException.class, () -> deliveries.getPendingWebhookDeliveries(10));
        expectThrows(RuntimeException.class, () -> deliveries.getPendingWebhookDispatchContexts(10));
        expectThrows(RuntimeException.class, () -> deliveries.getStuckInFlightWebhookDeliveries(10, new Timestamp(1)));
        expectThrows(RuntimeException.class, () -> deliveries.getPendingPollDeliveries("org", "group", 10));
        expectThrows(RuntimeException.class, () -> deliveries.getWebhookDeliveryAudits("delivery", "org"));
        expectThrows(RuntimeException.class, () -> deliveries.getPollDeliveryById("delivery", "org"));
        expectThrows(RuntimeException.class, () -> deliveries.getEventPayload("event"));
        expectThrows(RuntimeException.class, () -> deliveries.getOrgDeliveryById("org", "delivery"));
    }

    @Test
    public void coversEmptyInputReadGuards() throws Exception {
        DeliveryDAOImpl deliveries = new DeliveryDAOImpl();
        setConfiguration(deliveries);
        org.testng.Assert.assertFalse(deliveries.getEventPayload(null).isPresent());
        org.testng.Assert.assertFalse(deliveries.getEventPayload(" ").isPresent());
        org.testng.Assert.assertTrue(deliveries.listEventDeliveries(null, "event", 10, 0, null).isEmpty());
        org.testng.Assert.assertTrue(deliveries.listEventDeliveries("org", "", 10, 0, null).isEmpty());
        org.testng.Assert.assertTrue(new SubscriptionDAOImpl()
                .getPurposesBySubscriptionIds(Collections.emptyList()).isEmpty());
    }

    private void setConfiguration(DeliveryDAOImpl dao) throws Exception {
        Field field = DeliveryDAOImpl.class.getDeclaredField("configurationService");
        field.setAccessible(true);
        DPDPConfigurationService config = Mockito.mock(DPDPConfigurationService.class);
        when(config.getEventNotificationStuckInFlightThresholdSeconds()).thenReturn(10);
        field.set(dao, config);
    }

    private void setManagerDataSource(Object value) throws Exception {
        Field field = JDBCPersistenceManager.class.getDeclaredField("dataSource");
        field.setAccessible(true);
        field.set(null, value);
    }
}
