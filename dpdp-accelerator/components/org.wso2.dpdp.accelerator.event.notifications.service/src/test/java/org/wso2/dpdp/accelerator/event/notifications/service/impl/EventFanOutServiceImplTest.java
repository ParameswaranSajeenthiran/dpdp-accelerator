/**
 * Copyright (c) 2026, WSO2 LLC. (https://www.wso2.com).
 * <p>
 * WSO2 LLC. licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 *     http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.wso2.dpdp.accelerator.event.notifications.service.impl;

import org.mockito.ArgumentCaptor;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import org.wso2.dpdp.accelerator.event.notifications.common.enums.PurposeFilterMode;
import org.wso2.dpdp.accelerator.event.notifications.dao.DeliveryDAO;
import org.wso2.dpdp.accelerator.event.notifications.dao.SubscriptionDAO;
import org.wso2.dpdp.accelerator.event.notifications.dao.model.Event;
import org.wso2.dpdp.accelerator.event.notifications.dao.model.Subscription;
import org.wso2.dpdp.accelerator.event.notifications.dao.model.WebhookDelivery;

import java.sql.Connection;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertEquals;

/**
 * Verifies that {@link EventFanOutServiceImpl} only enqueues one
 * {@code WEBHOOK_DELIVERY} row per matching active subscription, and that
 * non-matching candidates (wrong group, wrong purpose, poll mode) are
 * silently filtered out.
 */
public class EventFanOutServiceImplTest {

    private SubscriptionDAO subscriptionDAO;
    private DeliveryDAO deliveryDAO;
    private EventFanOutServiceImpl fanOutService;
    private Connection connection;

    @BeforeMethod
    public void setUp() {
        subscriptionDAO = mock(SubscriptionDAO.class);
        deliveryDAO = mock(DeliveryDAO.class);
        connection = mock(Connection.class);
        fanOutService = new EventFanOutServiceImpl(subscriptionDAO, deliveryDAO);
        when(deliveryDAO.addWebhookDelivery(any(Connection.class), any())).thenReturn(true);
    }

    @Test
    public void fanOutEvent_nullEvent_isNoOp() {
        fanOutService.fanOutEvent(connection, null, Arrays.asList("marketing"));
        verify(subscriptionDAO, never()).getActiveSubscriptionsForFanOut(any(Connection.class), any(), any());
        verify(deliveryDAO, never()).addWebhookDelivery(any(Connection.class), any());
    }

    @Test(expectedExceptions = IllegalArgumentException.class)
    public void fanOutEvent_requiresTransactionConnection() {
        fanOutService.fanOutEvent(null, sampleEvent("org1", "g1", "topic1"),
                Arrays.asList("marketing"));
    }

    @Test
    public void fanOutEvent_queuesDeliveryForMatchingWebhookSubscription() {
        Event event = sampleEvent("org1", "g1", "topic1");
        Subscription webhookMatch = subscription("sub-1", "org1", "g1", "topic1",
                PurposeFilterMode.SPECIFIC.getValue(), Arrays.asList("marketing"), "webhook");
        when(subscriptionDAO.getActiveSubscriptionsForFanOut(connection, "org1", "topic1"))
                .thenReturn(Collections.singletonList(webhookMatch));

        fanOutService.fanOutEvent(connection, event, Arrays.asList("marketing"));

        ArgumentCaptor<WebhookDelivery> captor = ArgumentCaptor.forClass(WebhookDelivery.class);
        verify(deliveryDAO, times(1)).addWebhookDelivery(any(Connection.class), captor.capture());
        WebhookDelivery saved = captor.getValue();
        assertEquals(saved.getSubscriptionId(), "sub-1");
        assertEquals(saved.getEventId(), event.getEventId());
        assertEquals(saved.getStatus(), "pending");
        assertEquals(saved.getAttemptCount(), 0);
    }

    @Test
    public void fanOutEvent_groupMismatch_isFiltered() {
        Event event = sampleEvent("org1", "g1", "topic1");
        Subscription wrongGroup = subscription("sub-1", "org1", "g9", "topic1",
                PurposeFilterMode.ALL.getValue(), null, "webhook");
        when(subscriptionDAO.getActiveSubscriptionsForFanOut(connection, "org1", "topic1"))
                .thenReturn(Collections.singletonList(wrongGroup));

        fanOutService.fanOutEvent(connection, event, Arrays.asList("marketing"));

        verify(deliveryDAO, never()).addWebhookDelivery(any(Connection.class), any());
    }

    @Test
    public void fanOutEvent_nullSubscriptionGroup_matchesAnyEventGroup() {
        Event event = sampleEvent("org1", "g9", "topic1");
        Subscription anyGroup = subscription("sub-1", "org1", null, "topic1",
                PurposeFilterMode.ALL.getValue(), null, "webhook");
        when(subscriptionDAO.getActiveSubscriptionsForFanOut(connection, "org1", "topic1"))
                .thenReturn(Collections.singletonList(anyGroup));

        fanOutService.fanOutEvent(connection, event, Arrays.asList("marketing"));

        verify(deliveryDAO, times(1)).addWebhookDelivery(any(Connection.class), any());
    }

    @Test
    public void fanOutEvent_purposeMismatch_isFiltered() {
        Event event = sampleEvent("org1", "g1", "topic1");
        Subscription wrongPurpose = subscription("sub-1", "org1", "g1", "topic1",
                PurposeFilterMode.SPECIFIC.getValue(), Arrays.asList("billing"), "webhook");
        when(subscriptionDAO.getActiveSubscriptionsForFanOut(connection, "org1", "topic1"))
                .thenReturn(Collections.singletonList(wrongPurpose));

        fanOutService.fanOutEvent(connection, event, Arrays.asList("marketing"));

        verify(deliveryDAO, never()).addWebhookDelivery(any(Connection.class), any());
    }

    @Test
    public void fanOutEvent_mixedCandidates_queuesMatchingWebhookAndPollDeliveries() {
        Event event = sampleEvent("org1", "g1", "topic1");
        List<Subscription> candidates = new ArrayList<>();
        candidates.add(subscription("sub-webhook-match", "org1", "g1", "topic1",
                PurposeFilterMode.SPECIFIC.getValue(), Arrays.asList("marketing"), "webhook"));
        candidates.add(subscription("sub-group-mismatch", "org1", "g9", "topic1",
                PurposeFilterMode.ALL.getValue(), null, "webhook"));
        candidates.add(subscription("sub-purpose-mismatch", "org1", "g1", "topic1",
                PurposeFilterMode.SPECIFIC.getValue(), Arrays.asList("billing"), "webhook"));
        candidates.add(subscription("sub-poll-deferred", "org1", "g1", "topic1",
                PurposeFilterMode.ALL.getValue(), null, "poll"));
        when(subscriptionDAO.getActiveSubscriptionsForFanOut(connection, "org1", "topic1"))
                .thenReturn(candidates);
        when(deliveryDAO.addPollDelivery(any(Connection.class), any())).thenReturn(true);

        fanOutService.fanOutEvent(connection, event, Arrays.asList("marketing"));

        ArgumentCaptor<WebhookDelivery> captor = ArgumentCaptor.forClass(WebhookDelivery.class);
        verify(deliveryDAO, times(1)).addWebhookDelivery(any(Connection.class), captor.capture());
        assertEquals(captor.getValue().getSubscriptionId(), "sub-webhook-match");
        verify(deliveryDAO, times(1)).addPollDelivery(any(Connection.class), any());
    }

    @Test
    public void fanOutEvent_pendingWebhookSubscriptionIsIgnoredDefensively() {
        Event event = sampleEvent("org1", "g1", "topic1");
        Subscription pendingSub = subscription("sub-pending", "org1", "g1", "topic1",
                PurposeFilterMode.ALL.getValue(), null, "webhook");
        pendingSub.setStatus("pending");
        when(subscriptionDAO.getActiveSubscriptionsForFanOut(connection, "org1", "topic1"))
                .thenReturn(Collections.singletonList(pendingSub));

        fanOutService.fanOutEvent(connection, event, Arrays.asList("marketing"));

        verify(deliveryDAO, never()).addWebhookDelivery(any(Connection.class), any());
    }

    @Test(expectedExceptions = org.wso2.dpdp.accelerator.event.notifications.common.exception.EventNotificationDataAccessException.class)
    public void fanOutEvent_daoReturnsFalse_throwsException() {
        Event event = sampleEvent("org1", "g1", "topic1");
        Subscription webhookMatch = subscription("sub-1", "org1", "g1", "topic1",
                PurposeFilterMode.ALL.getValue(), null, "webhook");
        when(subscriptionDAO.getActiveSubscriptionsForFanOut(connection, "org1", "topic1"))
                .thenReturn(Collections.singletonList(webhookMatch));
        when(deliveryDAO.addWebhookDelivery(any(Connection.class), any())).thenReturn(false);

        fanOutService.fanOutEvent(connection, event, Arrays.asList("marketing"));
    }

    private static Event sampleEvent(String orgId, String groupId, String topicId) {
        Event event = new Event("event-" + UUID.randomUUID(), orgId, groupId, topicId, "{\"k\":\"v\"}",
                new Timestamp(System.currentTimeMillis()));
        return event;
    }

    private static Subscription subscription(String id, String orgId, String groupId, String topicId,
            String purposeFilterMode, List<String> purposes, String deliveryMode) {
        return new Subscription(id, orgId, groupId, topicId, purposeFilterMode, purposes, "", deliveryMode,
                "https://example.test/callback", "shared-secret", "active",
                new Timestamp(System.currentTimeMillis()), new Timestamp(System.currentTimeMillis()));
    }
}
