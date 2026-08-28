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

package org.wso2.dpdp.accelerator.event.notifications.endpoint.handler;

import org.mockito.ArgumentCaptor;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import org.wso2.dpdp.accelerator.event.notifications.service.EventPublishService;
import org.wso2.dpdp.accelerator.event.notifications.service.dto.EventCreateDTO;
import org.wso2.dpdp.accelerator.event.notifications.service.dto.EventDTO;
import org.wso2.dpdp.accelerator.event.notifications.service.model.PaginatedResult;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;

public class EventHandlerTest {

    private EventPublishService eventPublishService;
    private EventHandler eventHandler;

    @BeforeMethod
    public void setUp() {
        eventPublishService = mock(EventPublishService.class);
        eventHandler = new EventHandler(eventPublishService);
    }

    @Test
    public void publishEvent_passesRequestFieldsToService() {
        Map<String, Object> payload = new HashMap<>();
        payload.put("k", "v");
        EventCreateDTO request = new EventCreateDTO("topic-a", Arrays.asList("marketing"), payload);
        EventDTO published = new EventDTO("evt-1", "org1", "g1", "topic-id-1", "{\"k\":\"v\"}",
                Arrays.asList("marketing"), null, null);
        when(eventPublishService.publishEvent(anyString(), anyString(), anyString(), any(), any()))
                .thenReturn(published);

        EventDTO result = eventHandler.publishEvent("org1", "g1", request);

        assertEquals(result, published);
        ArgumentCaptor<String> orgCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> groupCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> topicCaptor = ArgumentCaptor.forClass(String.class);
        verify(eventPublishService, times(1)).publishEvent(orgCaptor.capture(), groupCaptor.capture(),
                topicCaptor.capture(), any(), any());
        assertEquals(orgCaptor.getValue(), "org1");
        assertEquals(groupCaptor.getValue(), "g1");
        assertEquals(topicCaptor.getValue(), "topic-a");
    }

    @Test
    public void publishEvent_nullRequest_doesNotThrow() {
        when(eventPublishService.publishEvent(anyString(), any(), any(), any(), any()))
                .thenReturn(new EventDTO("evt-1", "org1", null, null, "{}", null, null, null));

        EventDTO result = eventHandler.publishEvent("org1", null, null);

        assertNotNull(result);
        assertEquals(result.getEventId(), "evt-1");
        verify(eventPublishService, times(1)).publishEvent(eq("org1"), eq(null), eq(null), eq(null), eq(null));
    }

    @Test
    public void publishEvent_serviceThrows_propagates() {
        EventCreateDTO request = new EventCreateDTO("topic-a", null, null);
        when(eventPublishService.publishEvent(anyString(), anyString(), any(), any(), any()))
                .thenThrow(new RuntimeException("boom"));

        try {
            eventHandler.publishEvent("org1", "g1", request);
            org.testng.Assert.fail("Expected RuntimeException");
        } catch (RuntimeException e) {
            assertEquals(e.getMessage(), "boom");
        }
    }

    @Test
    public void searchEvents_forwardsPaginationToService() {
        PaginatedResult<EventDTO> daoResult = new PaginatedResult<>(Collections.<EventDTO>emptyList(), 0);
        when(eventPublishService.searchEvents(anyString(), any(), any(), any(), any(), any(), anyInt(), anyInt()))
                .thenReturn(daoResult);

        PaginatedResult<EventDTO> result = eventHandler.searchEvents("org1", "search", 10000, -5);

        assertNotNull(result);
        verify(eventPublishService, times(1)).searchEvents("org1", null, null, null, null, "search",
                10000, -5);
    }

    @Test
    public void searchEvents_withAllFilters_forwardsToService() {
        PaginatedResult<EventDTO> daoResult = new PaginatedResult<>(Collections.<EventDTO>emptyList(), 0);
        when(eventPublishService.searchEvents(eq("org1"), eq("t1"), eq("DELIVERED"), eq("g1"), eq("purposes"), eq("search"), anyInt(), anyInt()))
                .thenReturn(daoResult);

        PaginatedResult<EventDTO> result = eventHandler.searchEvents("org1", "t1", "DELIVERED", "g1", "purposes", "search", 10, 0);

        assertNotNull(result);
        verify(eventPublishService, times(1)).searchEvents("org1", "t1", "DELIVERED", "g1", "purposes", "search", 10, 0);
    }

    @Test
    public void searchEvents_nullPaginationUsesServiceSentinels() {
        when(eventPublishService.searchEvents(anyString(), any(), any(), any(), any(), any(), anyInt(), anyInt()))
                .thenReturn(new PaginatedResult<>(Collections.<EventDTO>emptyList(), 0));

        eventHandler.searchEvents("org1", "search", null, null);

        verify(eventPublishService, times(1)).searchEvents("org1", null, null, null, null, "search",
                0, -1);
    }

    @Test
    public void searchEvents_serviceThrows_propagates() {
        when(eventPublishService.searchEvents(anyString(), any(), any(), any(), any(), any(), anyInt(), anyInt()))
                .thenThrow(new RuntimeException("boom"));

        try {
            eventHandler.searchEvents("org1", "search", 10, 0);
            org.testng.Assert.fail("Expected RuntimeException");
        } catch (RuntimeException e) {
            assertEquals(e.getMessage(), "boom");
        }
    }
}
