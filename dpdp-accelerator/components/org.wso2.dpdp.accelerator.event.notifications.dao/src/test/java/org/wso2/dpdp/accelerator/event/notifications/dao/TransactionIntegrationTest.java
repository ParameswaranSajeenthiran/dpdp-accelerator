/*
 * Copyright (c) 2026, WSO2 LLC. (https://www.wso2.com).
 *
 * Licensed under the Apache License, Version 2.0.
 */
package org.wso2.dpdp.accelerator.event.notifications.dao;

import org.h2.tools.RunScript;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.Test;
import org.wso2.dpdp.accelerator.event.notifications.common.enums.DeliveryMode;
import org.wso2.dpdp.accelerator.event.notifications.common.enums.PurposeFilterMode;
import org.wso2.dpdp.accelerator.event.notifications.common.enums.SubscriptionStatus;
import org.wso2.dpdp.accelerator.event.notifications.common.enums.TopicStatus;
import org.wso2.dpdp.accelerator.event.notifications.dao.impl.DeliveryDAOImpl;
import org.wso2.dpdp.accelerator.event.notifications.dao.impl.SubscriptionDAOImpl;
import org.wso2.dpdp.accelerator.event.notifications.dao.impl.TopicDAOImpl;
import org.wso2.dpdp.accelerator.event.notifications.dao.model.PollDelivery;
import org.wso2.dpdp.accelerator.event.notifications.dao.model.Subscription;
import org.wso2.dpdp.accelerator.event.notifications.dao.model.Topic;

import java.io.StringReader;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.util.Collections;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

/** Real H2 checks for the connection-aware persistence methods. */
public class TransactionIntegrationTest {

    private Connection connection;
    private String databaseName;

    @BeforeMethod
    public void setUp() throws Exception {
        databaseName = "enf_" + System.nanoTime();
        connection = DriverManager.getConnection("jdbc:h2:mem:" + databaseName + ";DB_CLOSE_DELAY=-1");
        RunScript.execute(connection, new StringReader(
                "CREATE TABLE TOPIC (TOPIC_ID VARCHAR(64) PRIMARY KEY, ORG_ID VARCHAR(128) NOT NULL, "
                        + "NAME VARCHAR(225) NOT NULL, DESCRIPTION VARCHAR(255), STATUS VARCHAR(32) NOT NULL, "
                        + "INITIATED_BY VARCHAR(32) NOT NULL);"
                        + "CREATE UNIQUE INDEX UQ_TOPIC_ORG_NAME_STATUS ON TOPIC(ORG_ID, NAME, STATUS);"
                        + "CREATE TABLE SUBSCRIPTION (SUBSCRIPTION_ID VARCHAR(64) PRIMARY KEY, ORG_ID VARCHAR(128) NOT NULL, "
                        + "GROUP_ID VARCHAR(128) NOT NULL, TOPIC_ID VARCHAR(64) NOT NULL, PURPOSE_FILTER_MODE VARCHAR(32) NOT NULL, "
                        + "PURPOSE_SET_HASH VARCHAR(64) NOT NULL, DELIVERY_MODE VARCHAR(32) NOT NULL, CALLBACK_URL VARCHAR(512), "
                        + "SHARED_SECRET VARCHAR(512), STATUS VARCHAR(32) NOT NULL, CREATED_AT TIMESTAMP NOT NULL, UPDATED_AT TIMESTAMP NOT NULL);"
                        + "CREATE TABLE SUBSCRIPTION_PURPOSE (SUBSCRIPTION_ID VARCHAR(64), PURPOSE_NAME VARCHAR(128), "
                        + "PRIMARY KEY(SUBSCRIPTION_ID, PURPOSE_NAME));"
                        + "CREATE TABLE POLL_DELIVERY (DELIVERY_ID VARCHAR(64) PRIMARY KEY, SUBSCRIPTION_ID VARCHAR(64), "
                        + "EVENT_ID VARCHAR(64), STATUS VARCHAR(32), CREATED_AT TIMESTAMP, COMPLETED_AT TIMESTAMP);"));
    }

    @AfterMethod
    public void tearDown() throws Exception {
        if (connection != null && !connection.isClosed()) {
            connection.close();
        }
    }

    @Test
    public void topicInsertCanBeRolledBackByCaller() throws Exception {
        TopicDAOImpl dao = new TopicDAOImpl();
        connection.setAutoCommit(false);
        assertTrue(dao.addTopic(connection, new Topic("topic-1", "org-1", "accounts", "", 
                TopicStatus.ACTIVE.getValue())));
        connection.rollback();

        try (PreparedStatement ps = connection.prepareStatement("SELECT COUNT(*) FROM TOPIC WHERE TOPIC_ID = ?")) {
            ps.setString(1, "topic-1");
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                assertEquals(rs.getInt(1), 0);
            }
        }
    }

    @Test
    public void subscriptionPersistsNormalizedEnumValuesAndPurposes() throws Exception {
        TopicDAOImpl topicDAO = new TopicDAOImpl();
        topicDAO.addTopic(connection, new Topic("topic-1", "org-1", "accounts", "", TopicStatus.ACTIVE.getValue()));
        Subscription subscription = new Subscription("sub-1", "org-1", "group-1", "topic-1", "ALL",
                Collections.singletonList("marketing"), "WEBHOOK", "https://example.com/callback", "secret",
                "PENDING", new Timestamp(System.currentTimeMillis()), new Timestamp(System.currentTimeMillis()));

        new SubscriptionDAOImpl().addSubscription(connection, subscription);
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT PURPOSE_FILTER_MODE, DELIVERY_MODE, STATUS FROM SUBSCRIPTION WHERE SUBSCRIPTION_ID = ?")) {
            ps.setString(1, "sub-1");
            try (ResultSet rs = ps.executeQuery()) {
                assertTrue(rs.next());
                assertEquals(rs.getString(1), PurposeFilterMode.ALL.getValue());
                assertEquals(rs.getString(2), DeliveryMode.WEBHOOK.getValue());
                assertEquals(rs.getString(3), SubscriptionStatus.PENDING.getValue());
            }
        }
    }

    @Test
    public void pollDeliveryInsertUsesCallerTransaction() throws Exception {
        connection.setAutoCommit(false);
        PollDelivery delivery = new PollDelivery("delivery-1", "sub-1", "event-1", "pending",
                new Timestamp(System.currentTimeMillis()), null);
        assertTrue(new DeliveryDAOImpl().addPollDelivery(connection, delivery));
        connection.rollback();

        try (PreparedStatement ps = connection.prepareStatement("SELECT COUNT(*) FROM POLL_DELIVERY")) {
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                assertEquals(rs.getInt(1), 0);
            }
        }
    }

    @Test
    public void duplicateTopicDoesNotCommitASecondRow() throws Exception {
        TopicDAOImpl dao = new TopicDAOImpl();
        dao.addTopic(connection, new Topic("topic-1", "org-1", "accounts", "", TopicStatus.ACTIVE.getValue()));
        try {
            dao.addTopic(connection, new Topic("topic-2", "org-1", "accounts", "", TopicStatus.ACTIVE.getValue()));
        } catch (RuntimeException expected) {
            // Duplicate detection happens before insertion; the original row remains the only row.
        }
        try (PreparedStatement ps = connection.prepareStatement("SELECT COUNT(*) FROM TOPIC")) {
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                assertEquals(rs.getInt(1), 1);
            }
        }
    }

    @Test
    public void topicCanBeUpdatedAndDeregisteredWithoutSubscriptions() throws Exception {
        TopicDAOImpl dao = new TopicDAOImpl();
        assertTrue(dao.addTopic(connection,
                new Topic("topic-1", "org-1", "accounts", "", TopicStatus.ACTIVE.getValue())));
        assertTrue(dao.updateTopicStatus(connection, "topic-1", "org-1", TopicStatus.ACTIVE));
        assertTrue(dao.deregisterTopicAtomic(connection, "topic-1", "org-1"));
        try (PreparedStatement ps = connection.prepareStatement("SELECT STATUS FROM TOPIC WHERE TOPIC_ID = ?")) {
            ps.setString(1, "topic-1");
            try (ResultSet rs = ps.executeQuery()) {
                assertTrue(rs.next());
                assertEquals(rs.getString(1), TopicStatus.DEREGISTERED.getValue());
            }
        }
    }

    @Test(timeOut = 10000)
    public void pollDeliveryClaimIsSerializedAcrossConnections() throws Exception {
        try (PreparedStatement ps = connection.prepareStatement(
                "INSERT INTO POLL_DELIVERY (DELIVERY_ID, SUBSCRIPTION_ID, EVENT_ID, STATUS, CREATED_AT) "
                        + "VALUES (?, ?, ?, ?, CURRENT_TIMESTAMP)")) {
            ps.setString(1, "delivery-1");
            ps.setString(2, "sub-1");
            ps.setString(3, "event-1");
            ps.setString(4, "pending");
            ps.executeUpdate();
        }

        try (Connection first = DriverManager.getConnection("jdbc:h2:mem:" + databaseName
                + ";DB_CLOSE_DELAY=-1");
                Connection second = DriverManager.getConnection("jdbc:h2:mem:" + databaseName
                        + ";DB_CLOSE_DELAY=-1")) {
            first.setAutoCommit(false);
            second.setAutoCommit(false);
            CountDownLatch start = new CountDownLatch(1);
            ExecutorService executor = Executors.newFixedThreadPool(2);
            Future<Boolean> firstClaim = executor.submit(() -> claimWhenReady(first, start));
            Future<Boolean> secondClaim = executor.submit(() -> claimWhenReady(second, start));
            start.countDown();
            boolean oneClaimed = firstClaim.get();
            boolean twoClaimed = secondClaim.get();
            executor.shutdownNow();
            assertTrue(oneClaimed ^ twoClaimed,
                    "Exactly one concurrent claimant must transition the pending delivery");
        }
    }

    private boolean claimWhenReady(Connection conn, CountDownLatch start) throws Exception {
        start.await();
        boolean claimed = new DeliveryDAOImpl().claimPollDelivery(conn, "delivery-1");
        conn.commit();
        return claimed;
    }

}
