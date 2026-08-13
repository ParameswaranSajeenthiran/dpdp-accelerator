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

package org.wso2.dpdp.accelerator.event.notifications.endpoint.api;

import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import org.wso2.dpdp.accelerator.event.notifications.endpoint.handler.EventHandler;
import org.wso2.dpdp.accelerator.event.notifications.service.dto.EventCreateDTO;
import org.wso2.dpdp.accelerator.event.notifications.service.dto.EventDTO;
import org.wso2.dpdp.accelerator.event.notifications.service.model.PaginatedResult;

import javax.ws.rs.core.Response;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertEquals;

/**
 * Verifies the JAX-RS wiring on {@link EventEndpoint} — the handler is the
 * integration point, so the endpoint test only checks that the right
 * field/header values are forwarded and the response carries the right
 * HTTP status. Behaviour of the underlying service is covered by
 * {@code EventPublishServiceImplTest}.
 */
public class EventEndpointTest {

    @Mock
    private EventHandler eventHandler;

    private EventEndpoint eventEndpoint;

    @BeforeMethod
    public void setUp() {
        MockitoAnnotations.openMocks(this);
        eventEndpoint = new EventEndpoint(eventHandler);
    }

    @Test
    public void publishEvent_returns201WithDtoBody() {
        EventCreateDTO request = new EventCreateDTO("topic-a", Arrays.asList("marketing"),
                new HashMap<>());
        EventDTO published = new EventDTO("evt-1", "org1", "g1", "topic-id-1", "{}",
                Arrays.asList("marketing"), null, null);
        when(eventHandler.publishEvent(eq("org1"), eq("g1"), any())).thenReturn(published);

        Response response = eventEndpoint.publishEvent("org1", "g1", request);

        assertEquals(response.getStatus(), Response.Status.CREATED.getStatusCode());
        assertEquals(response.getEntity(), published);
        verify(eventHandler, times(1)).publishEvent("org1", "g1", request);
    }

    @Test
    public void publishEvent_propagatesHandlerException() {
        EventCreateDTO request = new EventCreateDTO("topic-a", null, null);
        when(eventHandler.publishEvent(any(), any(), any()))
                .thenThrow(new RuntimeException("boom"));

        try {
            eventEndpoint.publishEvent("org1", "g1", request);
            org.testng.Assert.fail("Expected RuntimeException");
        } catch (RuntimeException e) {
            assertEquals(e.getMessage(), "boom");
        }
    }

    @Test
    public void searchEvent_returns200WithDtoBody() {
        PaginatedResult<EventDTO> page = new PaginatedResult<>(
                Collections.singletonList(new EventDTO("evt-1", "org1", "g1", "topic-id-1", "{}",
                        Collections.emptyList(), null, null)),
                1);
        when(eventHandler.searchEvents(eq("org1"), eq("search"), eq(10), eq(0))).thenReturn(page);

        Response response = eventEndpoint.searchEvents("org1", "search", 10, 0);

        assertEquals(response.getStatus(), Response.Status.OK.getStatusCode());
        assertEquals(response.getEntity(), page);
        verify(eventHandler, times(1)).searchEvents("org1", "search", 10, 0);
    }

    @Test
    public void searchEvent_propagatesHandlerException() {
        when(eventHandler.searchEvents(anyString(), any(), anyInt(), anyInt()))
                .thenThrow(new RuntimeException("boom"));

        try {
            eventEndpoint.searchEvents("org1", "search", 10, 0);
            org.testng.Assert.fail("Expected RuntimeException");
        } catch (RuntimeException e) {
            assertEquals(e.getMessage(), "boom");
        }
    }
}
