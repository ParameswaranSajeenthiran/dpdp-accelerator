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
import org.wso2.dpdp.accelerator.event.notifications.common.constants.EventNotificationCommonConstants;
import org.wso2.dpdp.accelerator.event.notifications.dao.EventDAO;
import org.wso2.dpdp.accelerator.event.notifications.dao.PaginatedDAOResult;
import org.wso2.dpdp.accelerator.event.notifications.dao.TopicDAO;
import org.wso2.dpdp.accelerator.event.notifications.dao.model.Event;
import org.wso2.dpdp.accelerator.event.notifications.dao.model.Topic;
import org.wso2.dpdp.accelerator.event.notifications.service.EventFanOutService;
import org.wso2.dpdp.accelerator.event.notifications.service.constants.EventNotificationServiceConstants;
import org.wso2.dpdp.accelerator.event.notifications.service.dto.EventDTO;
import org.wso2.dpdp.accelerator.event.notifications.service.exception.EventNotificationException;
import org.wso2.dpdp.accelerator.event.notifications.service.model.PaginatedResult;

import java.sql.Timestamp;
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
    private EventPublishServiceImpl publishService;

    @BeforeMethod
    public void setUp() {
        eventDAO = mock(EventDAO.class);
        topicDAO = mock(TopicDAO.class);
        fanOutService = mock(EventFanOutService.class);
        publishService = new EventPublishServiceImpl(eventDAO, topicDAO, fanOutService);
    }

    @Test
    public void publishEvent_happyPath_persistsAndFansOut() {
        when(topicDAO.getTopicByOrgAndName("org1", "topic-a"))
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
        verify(eventDAO, times(1)).addEvent(eventCaptor.capture());
        Event persisted = eventCaptor.getValue();
        assertEquals(persisted.getEventId(), dto.getEventId());
        assertEquals(persisted.getOrgId(), "org1");
        assertEquals(persisted.getTopicId(), "topic-id-1");

        verify(eventDAO, times(1)).addEventPurposes(eq(dto.getEventId()), eq(Arrays.asList("marketing")));
        verify(fanOutService, times(1)).fanOutEvent(any(Event.class), eq(Arrays.asList("marketing")));
    }

    @Test
    public void publishEvent_nullPayload_isStoredAsEmptyJson() {
        when(topicDAO.getTopicByOrgAndName("org1", "topic-a"))
                .thenReturn(Optional.of(new Topic("topic-id-1", "org1", "topic-a", null, "active")));

        EventDTO dto = publishService.publishEvent("org1", "g1", "topic-a", Collections.emptyList(), null);

        assertEquals(dto.getPayload(), "{}");
        verify(eventDAO, never()).addEventPurposes(anyString(), anyList());
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
        verify(topicDAO, never()).getTopicByOrgAndName(anyString(), anyString());
        verify(eventDAO, never()).addEvent(any());
        verify(fanOutService, never()).fanOutEvent(any(), any());
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
        verify(fanOutService, never()).fanOutEvent(any(), any());
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
        verify(topicDAO, never()).getTopicByOrgAndName(anyString(), anyString());
        verify(eventDAO, never()).addEvent(any());
        verify(fanOutService, never()).fanOutEvent(any(), any());
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
        verify(topicDAO, never()).getTopicByOrgAndName(anyString(), anyString());
        verify(eventDAO, never()).addEvent(any());
        verify(fanOutService, never()).fanOutEvent(any(), any());
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
        when(topicDAO.getTopicByOrgAndName("org1", "missing-topic")).thenReturn(Optional.empty());

        try {
            publishService.publishEvent("org1", "g1", "missing-topic", null, null);
            fail("Expected EventNotificationException");
        } catch (EventNotificationException e) {
            assertEquals(e.getStatusCode(), 404);
            assertEquals(e.getCode(), EventNotificationServiceConstants.ERROR_CODE_TOPIC_NOT_FOUND);
            assertTrue(e.getDescription().contains("missing-topic"));
        }
        verify(eventDAO, never()).addEvent(any());
        verify(fanOutService, never()).fanOutEvent(any(), any());
    }

    @Test
    public void publishEvent_topicNotActive_throws400() {
        when(topicDAO.getTopicByOrgAndName("org1", "topic-a"))
                .thenReturn(Optional.of(new Topic("topic-id-1", "org1", "topic-a", null, "deregistered")));

        try {
            publishService.publishEvent("org1", "g1", "topic-a", null, null);
            fail("Expected EventNotificationException");
        } catch (EventNotificationException e) {
            assertEquals(e.getStatusCode(), 400);
            assertEquals(e.getCode(), EventNotificationServiceConstants.ERROR_CODE_INVALID_REQUEST);
        }
        verify(eventDAO, never()).addEvent(any());
    }

    @Test
    public void publishEvent_fanOutFails_throws500() {
        when(topicDAO.getTopicByOrgAndName("org1", "topic-a"))
                .thenReturn(Optional.of(new Topic("topic-id-1", "org1", "topic-a", null, "active")));
        doThrow(new RuntimeException("boom")).when(fanOutService).fanOutEvent(any(), any());

        try {
            publishService.publishEvent("org1", "g1", "topic-a", Collections.emptyList(), null);
            fail("Expected EventNotificationException when fan-out fails");
        } catch (EventNotificationException e) {
            assertEquals(e.getStatusCode(), 500);
            assertEquals(e.getCode(), EventNotificationServiceConstants.ERROR_CODE_EVENT_PUBLISH_FAILED);
        }
    }

    @Test
    public void publishEvent_addEventThrows_throws500() {
        when(topicDAO.getTopicByOrgAndName("org1", "topic-a"))
                .thenReturn(Optional.of(new Topic("topic-id-1", "org1", "topic-a", null, "active")));
        doThrow(new RuntimeException("db down")).when(eventDAO).addEvent(any());

        try {
            publishService.publishEvent("org1", "g1", "topic-a", null, null);
            fail("Expected EventNotificationException");
        } catch (EventNotificationException e) {
            assertEquals(e.getStatusCode(), 500);
            assertEquals(e.getCode(), EventNotificationServiceConstants.ERROR_CODE_EVENT_PUBLISH_FAILED);
        }
        verify(fanOutService, never()).fanOutEvent(any(), any());
    }

    @Test
    public void searchEvents_happyPath_mapsAndReturnsTotal() {
        Timestamp now = new Timestamp(System.currentTimeMillis());
        Event e1 = new Event("evt-1", "org1", "g1", "topic-id-1", "{\"k\":\"v1\"}", now);
        e1.setPurposes(Arrays.asList("marketing"));
        Event e2 = new Event("evt-2", "org1", null, "topic-id-1", "{\"k\":\"v2\"}", now);
        e2.setPurposes(Collections.emptyList());
        PaginatedDAOResult<Event> daoResult = new PaginatedDAOResult<>(Arrays.asList(e1, e2), 2);
        when(eventDAO.searchEvents(eq("org1"), eq("k"), anyInt(), anyInt())).thenReturn(daoResult);

        PaginatedResult<EventDTO> result = publishService.searchEvents("org1", "k", 10, 0);

        assertNotNull(result);
        assertEquals(result.getTotal(), 2);
        assertEquals(result.getItems().size(), 2);
        assertEquals(result.getItems().get(0).getEventId(), "evt-1");
        assertEquals(result.getItems().get(0).getPurposes(), Arrays.asList("marketing"));
        assertEquals(result.getItems().get(0).getPayload(), "{\"k\":\"v1\"}");
        assertEquals(result.getItems().get(1).getEventId(), "evt-2");
        assertEquals(result.getItems().get(1).getPurposes(), Collections.emptyList());
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
        when(eventDAO.searchEvents(anyString(), any(), anyInt(), anyInt()))
                .thenReturn(new PaginatedDAOResult<>(Collections.<Event>emptyList(), 0));

        publishService.searchEvents("org1", null, 10000, 0);

        verify(eventDAO, times(1)).searchEvents("org1", null,
                EventNotificationCommonConstants.MAX_LIMIT, 0);
    }

    @Test
    public void searchEvents_offsetNegative_treatedAsZero() {
        when(eventDAO.searchEvents(anyString(), any(), anyInt(), anyInt()))
                .thenReturn(new PaginatedDAOResult<>(Collections.<Event>emptyList(), 0));

        publishService.searchEvents("org1", null, 10, -5);

        verify(eventDAO, times(1)).searchEvents("org1", null, 10, 0);
    }

    @Test
    public void searchEvents_nullSearch_daoReceivesNull() {
        when(eventDAO.searchEvents(anyString(), any(), anyInt(), anyInt()))
                .thenReturn(new PaginatedDAOResult<>(Collections.<Event>emptyList(), 0));

        publishService.searchEvents("org1", null, 10, 0);

        verify(eventDAO, times(1)).searchEvents("org1", null, 10, 0);
    }

    @Test
    public void searchEvents_orgIdIsTrimmedBeforeDaoCall() {
        when(eventDAO.searchEvents(anyString(), any(), anyInt(), anyInt()))
                .thenReturn(new PaginatedDAOResult<>(Collections.<Event>emptyList(), 0));

        publishService.searchEvents("  org1  ", "search", 10, 0);

        verify(eventDAO, times(1)).searchEvents("org1", "search", 10, 0);
    }

    @Test
    public void searchEvents_emptyResult_isValidPaginatedResult() {
        when(eventDAO.searchEvents(anyString(), any(), anyInt(), anyInt()))
                .thenReturn(new PaginatedDAOResult<>(Collections.<Event>emptyList(), 0));

        PaginatedResult<EventDTO> result = publishService.searchEvents("org1", "no-match", 10, 0);

        assertNotNull(result);
        assertEquals(result.getTotal(), 0);
        assertNotNull(result.getItems());
        assertEquals(result.getItems().size(), 0);
    }

    @Test
    public void searchEvents_daoThrows_propagates() {
        when(eventDAO.searchEvents(anyString(), any(), anyInt(), anyInt()))
                .thenThrow(new RuntimeException("db down"));

        try {
            publishService.searchEvents("org1", "search", 10, 0);
            fail("Expected RuntimeException");
        } catch (RuntimeException e) {
            assertEquals(e.getMessage(), "db down");
        }
    }
}
