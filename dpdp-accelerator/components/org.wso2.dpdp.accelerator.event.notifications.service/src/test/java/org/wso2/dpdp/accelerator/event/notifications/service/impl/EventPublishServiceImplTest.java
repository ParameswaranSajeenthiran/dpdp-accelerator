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
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import org.wso2.dpdp.accelerator.event.notifications.common.constants.EventNotificationCommonConstants;
import org.wso2.dpdp.accelerator.common.persistence.JDBCPersistenceManager;
import org.wso2.dpdp.accelerator.event.notifications.dao.EventDAO;
import org.wso2.dpdp.accelerator.event.notifications.dao.PaginatedDAOResult;
import org.wso2.dpdp.accelerator.event.notifications.dao.TopicDAO;
import org.wso2.dpdp.accelerator.event.notifications.dao.model.Event;
import org.wso2.dpdp.accelerator.event.notifications.dao.model.Topic;
import org.wso2.dpdp.accelerator.event.notifications.service.EventFanOutService;
import org.wso2.dpdp.accelerator.event.notifications.service.constants.EventNotificationServiceConstants;
import org.wso2.dpdp.accelerator.event.notifications.service.dto.EventDTO;
import org.wso2.dpdp.accelerator.event.notifications.service.dto.SubscriptionDeliveryDTO;
import org.wso2.dpdp.accelerator.event.notifications.service.exception.EventNotificationException;
import org.wso2.dpdp.accelerator.event.notifications.service.model.PaginatedResult;

import javax.sql.DataSource;
import java.lang.reflect.Field;
import java.sql.Timestamp;
import java.sql.Connection;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.fail;

/**
 * Covers the four branches of {@link EventPublishServiceImpl#publishEvent}:
 * happy path, missing orgId, missing topic, and inactive topic. Also asserts
 * that fan-out is invoked synchronously after the event is persisted.
 */
public class EventPublishServiceImplTest {

    private EventDAO eventDAO;
    private TopicDAO topicDAO;
    private EventFanOutService fanOutService;
    private org.wso2.dpdp.accelerator.event.notifications.dao.DeliveryDAO deliveryDAO;
    private org.wso2.dpdp.accelerator.event.notifications.dao.DeliveryAckDAO deliveryAckDAO;
    private EventPublishServiceImpl publishService;
    private Connection connection;

    @BeforeMethod
    public void setUp() throws Exception {
        eventDAO = mock(EventDAO.class);
        topicDAO = mock(TopicDAO.class);
        fanOutService = mock(EventFanOutService.class);
        deliveryDAO = mock(org.wso2.dpdp.accelerator.event.notifications.dao.DeliveryDAO.class);
        deliveryAckDAO = mock(org.wso2.dpdp.accelerator.event.notifications.dao.DeliveryAckDAO.class);
        connection = mock(Connection.class);
        DataSource dataSource = mock(DataSource.class);
        when(dataSource.getConnection()).thenReturn(connection);
        setStaticInstance(null);
        setStaticDataSource(dataSource);
        when(eventDAO.addEvent(any(Connection.class), any())).thenReturn(true);
        publishService = new EventPublishServiceImpl(eventDAO, topicDAO, fanOutService, deliveryDAO, deliveryAckDAO);
    }

    @AfterMethod
    public void tearDown() throws Exception {
        setStaticDataSource(null);
        setStaticInstance(null);
    }

    private static void setStaticDataSource(DataSource dataSource) throws Exception {
        Field field = JDBCPersistenceManager.class.getDeclaredField("dataSource");
        field.setAccessible(true);
        field.set(null, dataSource);
    }

    private static void setStaticInstance(JDBCPersistenceManager instance) throws Exception {
        Field field = JDBCPersistenceManager.class.getDeclaredField("instance");
        field.setAccessible(true);
        field.set(null, instance);
    }

    @Test
    public void publishEvent_happyPath_persistsAndFansOut() {
        when(topicDAO.getActiveTopicByOrgAndNameForUpdate(any(Connection.class), eq("org1"), eq("topic-a")))
                .thenReturn(Optional.of(new Topic("topic-id-1", "org1", "topic-a", "desc", "active")));
        Map<String, Object> payload = new HashMap<>();
        payload.put("k", "v");

        EventDTO dto = publishService.publishEvent("org1", "g1", "topic-a",
                Arrays.asList("marketing"), payload);

        assertNotNull(dto.getEventId());
        assertEquals(dto.getOrgId(), "org1");
        assertEquals(dto.getGroupId(), "g1");
        assertEquals(dto.getTopicId(), "topic-id-1");
        assertEquals(dto.getPayload(), "{\"k\":\"v\"}");
        assertEquals(dto.getPurposes(), Arrays.asList("marketing"));
        assertNotNull(dto.getOccurredAt());
        assertNotNull(dto.getCreatedAt());

        ArgumentCaptor<Event> eventCaptor = ArgumentCaptor.forClass(Event.class);
        verify(eventDAO, times(1)).addEvent(eq(connection), eventCaptor.capture());
        Event persisted = eventCaptor.getValue();
        assertEquals(persisted.getEventId(), dto.getEventId());
        assertEquals(persisted.getOrgId(), "org1");
        assertEquals(persisted.getTopicId(), "topic-id-1");

        verify(eventDAO, times(1)).addEventPurposes(eq(connection), eq(dto.getEventId()), eq(Arrays.asList("marketing")));
        verify(fanOutService, times(1)).fanOutEvent(eq(connection), any(Event.class), eq(Arrays.asList("marketing")));
    }

    @Test
    public void publishEvent_nullPayload_isRejected() {
        try {
            publishService.publishEvent("org1", "g1", "topic-a", Collections.emptyList(), null);
            fail("Expected EventNotificationException");
        } catch (EventNotificationException e) {
            assertEquals(e.getStatusCode(), 422);
            assertEquals(e.getCode(), EventNotificationServiceConstants.ERROR_CODE_MISSING_REQUIRED_PARAM);
            assertEquals(e.getDescription(), EventNotificationServiceConstants.EVENT_PAYLOAD_REQUIRED_ERROR_MSG);
        }
        verify(topicDAO, never()).getActiveTopicByOrgAndNameForUpdate(any(Connection.class), anyString(), anyString());
    }

    @Test
    public void publishEvent_missingOrgId_throws400() {
        try {
            publishService.publishEvent(null, "g1", "topic-a", null, null);
            fail("Expected EventNotificationException");
        } catch (EventNotificationException e) {
            assertEquals(e.getStatusCode(), 400);
            assertEquals(e.getCode(), EventNotificationServiceConstants.ERROR_CODE_INVALID_REQUEST);
        }
        verify(topicDAO, never()).getActiveTopicByOrgAndNameForUpdate(any(Connection.class), anyString(), anyString());
        verify(eventDAO, never()).addEvent(any());
        verify(fanOutService, never()).fanOutEvent(any(Connection.class), any(), any());
    }

    @Test
    public void publishEvent_missingTopicName_throws400() {
        try {
            publishService.publishEvent("org1", "g1", null, null, null);
            fail("Expected EventNotificationException");
        } catch (EventNotificationException e) {
            assertEquals(e.getStatusCode(), 400);
        }
        verify(eventDAO, never()).addEvent(any());
        verify(fanOutService, never()).fanOutEvent(any(Connection.class), any(), any());
    }

    @Test
    public void publishEvent_nullGroupId_throws400() {
        try {
            publishService.publishEvent("org1", null, "topic-a", null, null);
            fail("Expected EventNotificationException");
        } catch (EventNotificationException e) {
            assertEquals(e.getStatusCode(), 400);
            assertEquals(e.getCode(), EventNotificationServiceConstants.ERROR_CODE_INVALID_REQUEST);
            assertEquals(e.getDescription(),
                    EventNotificationServiceConstants.GROUP_ID_MISSING_ERROR_MSG);
        }
        verify(topicDAO, never()).getActiveTopicByOrgAndNameForUpdate(any(Connection.class), anyString(), anyString());
        verify(eventDAO, never()).addEvent(any());
        verify(fanOutService, never()).fanOutEvent(any(Connection.class), any(), any());
    }

    @Test
    public void publishEvent_blankGroupId_throws400() {
        try {
            publishService.publishEvent("org1", "   ", "topic-a", null, null);
            fail("Expected EventNotificationException");
        } catch (EventNotificationException e) {
            assertEquals(e.getStatusCode(), 400);
            assertEquals(e.getCode(), EventNotificationServiceConstants.ERROR_CODE_INVALID_REQUEST);
            assertEquals(e.getDescription(),
                    EventNotificationServiceConstants.GROUP_ID_MISSING_ERROR_MSG);
        }
        verify(topicDAO, never()).getActiveTopicByOrgAndNameForUpdate(any(Connection.class), anyString(), anyString());
        verify(eventDAO, never()).addEvent(any());
        verify(fanOutService, never()).fanOutEvent(any(Connection.class), any(), any());
    }

    @Test
    public void publishEvent_groupIdGuardFiresBeforeTopicLookup() {
        // orgId is blank but groupId is also blank. The orgId guard must fire first since it's checked first.
        try {
            publishService.publishEvent(null, null, "topic-a", null, null);
            fail("Expected EventNotificationException");
        } catch (EventNotificationException e) {
            assertEquals(e.getStatusCode(), 400);
            assertEquals(e.getDescription(),
                    EventNotificationServiceConstants.ORG_ID_MISSING_ERROR_MSG);
        }
    }

    @Test
    public void publishEvent_groupIdGuardFiresBeforeTopicName() {
        // orgId is present, groupId is blank, topicName is also blank.
        // The groupId guard must fire before the topicName guard.
        try {
            publishService.publishEvent("org1", null, null, null, null);
            fail("Expected EventNotificationException");
        } catch (EventNotificationException e) {
            assertEquals(e.getStatusCode(), 400);
            assertEquals(e.getDescription(),
                    EventNotificationServiceConstants.GROUP_ID_MISSING_ERROR_MSG);
        }
    }

    @Test
    public void publishEvent_topicNotFound_throws404() {
        when(topicDAO.getActiveTopicByOrgAndNameForUpdate(any(Connection.class), eq("org1"), eq("missing-topic")))
                .thenReturn(Optional.empty());

        try {
            publishService.publishEvent("org1", "g1", "missing-topic", null, Collections.emptyMap());
            fail("Expected EventNotificationException");
        } catch (EventNotificationException e) {
            assertEquals(e.getStatusCode(), 404);
            assertEquals(e.getCode(), EventNotificationServiceConstants.ERROR_CODE_TOPIC_NOT_FOUND);
            assertTrue(e.getDescription().contains("missing-topic"));
        }
        verify(eventDAO, never()).addEvent(any());
        verify(fanOutService, never()).fanOutEvent(any(Connection.class), any(), any());
    }

    @Test
    public void publishEvent_topicNotActive_throws400() {
        when(topicDAO.getActiveTopicByOrgAndNameForUpdate(any(Connection.class), eq("org1"), eq("topic-a")))
                .thenReturn(Optional.of(new Topic("topic-id-1", "org1", "topic-a", null, "deregistered")));

        try {
            publishService.publishEvent("org1", "g1", "topic-a", null, Collections.emptyMap());
            fail("Expected EventNotificationException");
        } catch (EventNotificationException e) {
            assertEquals(e.getStatusCode(), 400);
            assertEquals(e.getCode(), EventNotificationServiceConstants.ERROR_CODE_INVALID_REQUEST);
        }
        verify(eventDAO, never()).addEvent(any());
    }

    @Test
    public void publishEvent_topicDeregisteredBeforeInsert_throws400() {
        when(topicDAO.getActiveTopicByOrgAndNameForUpdate(any(Connection.class), eq("org1"), eq("topic-a")))
                .thenReturn(Optional.of(new Topic("topic-id-1", "org1", "topic-a", null, "active")));
        when(eventDAO.addEvent(any(Connection.class), any())).thenReturn(false);

        EventNotificationException exception = org.testng.Assert.expectThrows(EventNotificationException.class,
                () -> publishService.publishEvent("org1", "g1", "topic-a", null, Collections.emptyMap()));

        assertEquals(exception.getStatusCode(), 400);
        assertEquals(exception.getCode(), EventNotificationServiceConstants.ERROR_CODE_INVALID_REQUEST);
        verify(eventDAO, never()).addEventPurposes(any(Connection.class), anyString(), any());
        verify(fanOutService, never()).fanOutEvent(any(Connection.class), any(), any());
    }

    @Test
    public void publishEvent_fanOutFails_throws500() {
        when(topicDAO.getActiveTopicByOrgAndNameForUpdate(any(Connection.class), eq("org1"), eq("topic-a")))
                .thenReturn(Optional.of(new Topic("topic-id-1", "org1", "topic-a", null, "active")));
        doThrow(new RuntimeException("boom")).when(fanOutService)
                .fanOutEvent(any(Connection.class), any(), any());

        try {
            publishService.publishEvent("org1", "g1", "topic-a", Collections.emptyList(), Collections.emptyMap());
            fail("Expected EventNotificationException when fan-out fails");
        } catch (EventNotificationException e) {
            assertEquals(e.getStatusCode(), 500);
            assertEquals(e.getCode(), EventNotificationServiceConstants.ERROR_CODE_EVENT_PUBLISH_FAILED);
        }
    }

    @Test
    public void publishEvent_addEventThrows_throws500() {
        when(topicDAO.getActiveTopicByOrgAndNameForUpdate(any(Connection.class), eq("org1"), eq("topic-a")))
                .thenReturn(Optional.of(new Topic("topic-id-1", "org1", "topic-a", null, "active")));
        doThrow(new RuntimeException("db down")).when(eventDAO).addEvent(any(Connection.class), any());

        try {
            publishService.publishEvent("org1", "g1", "topic-a", null, Collections.emptyMap());
            fail("Expected EventNotificationException");
        } catch (EventNotificationException e) {
            assertEquals(e.getStatusCode(), 500);
            assertEquals(e.getCode(), EventNotificationServiceConstants.ERROR_CODE_EVENT_PUBLISH_FAILED);
        }
        verify(fanOutService, never()).fanOutEvent(any(Connection.class), any(), any());
    }

    @Test
    public void searchEvents_happyPath_mapsAndReturnsTotal() {
        Timestamp now = new Timestamp(System.currentTimeMillis());
        Event e1 = new Event("evt-1", "org1", "g1", "topic-id-1", "{\"k\":\"v1\"}", now);
        e1.setPurposes(Arrays.asList("marketing"));
        e1.setTopic("consent-events");
        e1.setDeliveriesCount(2);
        Event e2 = new Event("evt-2", "org1", null, "topic-id-1", "{\"k\":\"v2\"}", now);
        e2.setPurposes(Collections.emptyList());
        e2.setTopic("consent-events");
        e2.setDeliveriesCount(0);
        PaginatedDAOResult<Event> daoResult = new PaginatedDAOResult<>(Arrays.asList(e1, e2), 2);
        when(eventDAO.searchEvents(eq("org1"), any(), any(), any(), any(), eq("k"), anyInt(), anyInt())).thenReturn(daoResult);

        PaginatedResult<EventDTO> result = publishService.searchEvents("org1", "k", 10, 0);

        assertNotNull(result);
        assertEquals(result.getTotal(), 2);
        assertEquals(result.getItems().size(), 2);
        assertEquals(result.getItems().get(0).getEventId(), "evt-1");
        assertEquals(result.getItems().get(0).getTopic(), "consent-events");
        assertEquals(result.getItems().get(0).getDeliveriesCount(), 2);
        assertEquals(result.getItems().get(0).getPurposes(), Arrays.asList("marketing"));
        assertEquals(result.getItems().get(0).getPayload(), "{\"k\":\"v1\"}");
        assertEquals(result.getItems().get(1).getEventId(), "evt-2");
        assertEquals(result.getItems().get(1).getPurposes(), Collections.emptyList());
    }

    @Test
    public void searchEvents_withAllFilters_forwardsToDao() {
        when(eventDAO.searchEvents(eq("org1"), eq("topic1"), eq("delivered"), eq("grp1"), eq("marketing"), eq("search1"), eq(10), eq(0)))
                .thenReturn(new PaginatedDAOResult<>(Collections.emptyList(), 0));

        PaginatedResult<EventDTO> result = publishService.searchEvents("org1", "topic1", "DELIVERED", "grp1", "marketing", "search1", 10, 0);
        assertNotNull(result);
        verify(eventDAO, times(1)).searchEvents("org1", "topic1", "delivered", "grp1", "marketing", "search1", 10, 0);
    }

    @Test(expectedExceptions = EventNotificationException.class)
    public void searchEvents_blankOrgId_throws400() {
        publishService.searchEvents("  ", "search", 10, 0);
    }

    @Test(expectedExceptions = EventNotificationException.class)
    public void searchEvents_nullOrgId_throws400() {
        publishService.searchEvents(null, "search", 10, 0);
    }

    @Test
    public void searchEvents_limitClampedToMaxLimit() {
        when(eventDAO.searchEvents(anyString(), any(), any(), any(), any(), any(), anyInt(), anyInt()))
                .thenReturn(new PaginatedDAOResult<>(Collections.<Event>emptyList(), 0));

        publishService.searchEvents("org1", null, 10000, 0);

        verify(eventDAO, times(1)).searchEvents("org1", null, null, null, null, null,
                EventNotificationCommonConstants.MAX_LIMIT, 0);
    }

    @Test
    public void searchEvents_offsetNegative_treatedAsZero() {
        when(eventDAO.searchEvents(anyString(), any(), any(), any(), any(), any(), anyInt(), anyInt()))
                .thenReturn(new PaginatedDAOResult<>(Collections.<Event>emptyList(), 0));

        publishService.searchEvents("org1", null, 10, -5);

        verify(eventDAO, times(1)).searchEvents("org1", null, null, null, null, null, 10, 0);
    }

    @Test
    public void searchEvents_nullSearch_daoReceivesNull() {
        when(eventDAO.searchEvents(anyString(), any(), any(), any(), any(), any(), anyInt(), anyInt()))
                .thenReturn(new PaginatedDAOResult<>(Collections.<Event>emptyList(), 0));

        publishService.searchEvents("org1", null, 10, 0);

        verify(eventDAO, times(1)).searchEvents("org1", null, null, null, null, null, 10, 0);
    }

    @Test
    public void searchEvents_orgIdIsTrimmedBeforeDaoCall() {
        when(eventDAO.searchEvents(anyString(), any(), any(), any(), any(), any(), anyInt(), anyInt()))
                .thenReturn(new PaginatedDAOResult<>(Collections.<Event>emptyList(), 0));

        publishService.searchEvents("  org1  ", "search", 10, 0);

        verify(eventDAO, times(1)).searchEvents("org1", null, null, null, null, "search", 10, 0);
    }

    @Test
    public void searchEvents_emptyResult_isValidPaginatedResult() {
        when(eventDAO.searchEvents(anyString(), any(), any(), any(), any(), any(), anyInt(), anyInt()))
                .thenReturn(new PaginatedDAOResult<>(Collections.<Event>emptyList(), 0));

        PaginatedResult<EventDTO> result = publishService.searchEvents("org1", "no-match", 10, 0);

        assertNotNull(result);
        assertEquals(result.getTotal(), 0);
        assertNotNull(result.getItems());
        assertEquals(result.getItems().size(), 0);
    }

    @Test
    public void searchEvents_daoThrows_propagates() {
        when(eventDAO.searchEvents(anyString(), any(), any(), any(), any(), any(), anyInt(), anyInt()))
                .thenThrow(new RuntimeException("db down"));

        try {
            publishService.searchEvents("org1", "search", 10, 0);
            fail("Expected RuntimeException");
        } catch (RuntimeException e) {
            assertEquals(e.getMessage(), "db down");
        }
    }

    @Test
    public void listOrgDeliveries_happyPath() {
        org.wso2.dpdp.accelerator.event.notifications.dao.model.SubscriptionDeliverySummary summary =
                new org.wso2.dpdp.accelerator.event.notifications.dao.model.SubscriptionDeliverySummary(
                        "dlv-1", "evt-1", "sub-1", "topic-1", "DELIVERED", "webhook",
                        new Timestamp(1710000000000L), new Timestamp(1710000000000L), "{}");
        when(deliveryDAO.listOrgDeliveries(eq("org1"), eq("delivered"), eq("sub-1"), eq("grp-1"), eq("marketing"), eq("search"), eq(10), eq(0), any(int[].class)))
                .thenAnswer(invocation -> {
                    int[] total = invocation.getArgument(8);
                    total[0] = 1;
                    return Collections.singletonList(summary);
                });

        PaginatedResult<org.wso2.dpdp.accelerator.event.notifications.service.dto.SubscriptionDeliveryDTO> result =
                publishService.listOrgDeliveries("org1", "DELIVERED", "sub-1", "grp-1", "marketing", "search", 10, 0);

        assertNotNull(result);
        assertEquals(result.getTotal(), 1);
        assertEquals(result.getItems().size(), 1);
        assertEquals(result.getItems().get(0).getDeliveryId(), "dlv-1");
    }

    @Test
    public void listOrgDeliveries_missingOrgId_throws() {
        try {
            publishService.listOrgDeliveries(" ", null, null, null, null, 10, 0);
            fail("Expected EventNotificationException");
        } catch (EventNotificationException e) {
            assertEquals(e.getStatusCode(), 400);
        }
    }

    @Test
    public void getDeliveryHistory_happyPath() {
        org.wso2.dpdp.accelerator.event.notifications.dao.model.SubscriptionDeliverySummary summary =
                new org.wso2.dpdp.accelerator.event.notifications.dao.model.SubscriptionDeliverySummary(
                        "dlv-1", "evt-1", "sub-1", "topic-1", "DELIVERED", "webhook",
                        new Timestamp(1710000000000L), new Timestamp(1710000000000L), "{}");
        when(deliveryDAO.getOrgDeliveryById("org1", "dlv-1")).thenReturn(Optional.of(summary));
        when(deliveryDAO.getWebhookDeliveryAudits("dlv-1", "org1")).thenReturn(Collections.emptyList());

        org.wso2.dpdp.accelerator.event.notifications.service.dto.SubscriptionEventHistoryDTO dto =
                publishService.getDeliveryHistory("org1", "dlv-1");

        assertNotNull(dto);
        assertEquals(dto.getDeliveryId(), "dlv-1");
        assertEquals(dto.getEventId(), "evt-1");
    }

    @Test
    public void getDeliveryHistory_notFound_throws404() {
        when(deliveryDAO.getOrgDeliveryById("org1", "dlv-1")).thenReturn(Optional.empty());

        try {
            publishService.getDeliveryHistory("org1", "dlv-1");
            fail("Expected EventNotificationException");
        } catch (EventNotificationException e) {
            assertEquals(e.getStatusCode(), 404);
        }
    }

    @Test
    public void getEventById_happyPath() {
        Timestamp now = new Timestamp(1710000000000L);
        Event event = new Event("evt-1", "org1", "group-1", "top-1", "{\"msg\":\"hi\"}", now);
        event.setPurposes(Arrays.asList("marketing"));
        when(eventDAO.getEventById("evt-1", "org1")).thenReturn(Optional.of(event));
        when(topicDAO.getTopicById("top-1", "org1")).thenReturn(Optional.of(new Topic("top-1", "org1", "consent.granted", "desc", "active")));

        EventDTO dto = publishService.getEventById("org1", "evt-1");
        assertNotNull(dto);
        assertEquals(dto.getEventId(), "evt-1");
        assertEquals(dto.getTopic(), "consent.granted");
        assertEquals(dto.getPurposes(), Arrays.asList("marketing"));
    }

    @Test
    public void getEventById_notFound_throws404() {
        when(eventDAO.getEventById("evt-1", "org1")).thenReturn(Optional.empty());

        try {
            publishService.getEventById("org1", "evt-1");
            fail("Expected EventNotificationException");
        } catch (EventNotificationException e) {
            assertEquals(e.getStatusCode(), 404);
        }
    }

    @Test
    public void getEventDeliveries_happyPath() {
        org.wso2.dpdp.accelerator.event.notifications.dao.model.SubscriptionDeliverySummary summary =
                new org.wso2.dpdp.accelerator.event.notifications.dao.model.SubscriptionDeliverySummary(
                        "dlv-1", "evt-1", "sub-1", "consent.granted", "DELIVERED", "webhook",
                        new Timestamp(1710000000000L), new Timestamp(1710000000000L), "{}");
        when(deliveryDAO.listEventDeliveries(eq("org1"), eq("evt-1"), anyInt(), anyInt(), any(int[].class)))
                .thenReturn(Collections.singletonList(summary));

        PaginatedResult<SubscriptionDeliveryDTO> result = publishService.getEventDeliveries("org1", "evt-1", 10, 0);
        assertNotNull(result);
        assertEquals(result.getItems().size(), 1);
        assertEquals(result.getItems().get(0).getDeliveryId(), "dlv-1");
    }
}
