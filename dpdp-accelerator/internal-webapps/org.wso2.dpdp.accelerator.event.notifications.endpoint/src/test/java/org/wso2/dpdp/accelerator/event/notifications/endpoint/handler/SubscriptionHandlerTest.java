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

import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import org.wso2.dpdp.accelerator.event.notifications.service.SubscriptionService;
import org.wso2.dpdp.accelerator.event.notifications.service.dto.SubscriptionDTO;
import org.wso2.dpdp.accelerator.event.notifications.common.enums.SubscriptionStatus;
import org.wso2.dpdp.accelerator.event.notifications.service.model.PaginatedResult;

import java.util.Arrays;
import java.util.Collections;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;

public class SubscriptionHandlerTest {

    @Mock
    private SubscriptionService subscriptionService;

    private SubscriptionHandler subscriptionHandler;

    @BeforeMethod
    public void setUp() {
        MockitoAnnotations.openMocks(this);
        subscriptionHandler = new SubscriptionHandler(subscriptionService);
    }

    @Test
    public void testListSubscriptions() {
        SubscriptionDTO dto = new SubscriptionDTO();
        dto.setSubscriptionId("sub1");
        dto.setStatus(SubscriptionStatus.ACTIVE);
        PaginatedResult<SubscriptionDTO> serviceResult = new PaginatedResult<>(Collections.singletonList(dto), 1);

        when(subscriptionService.listSubscriptions(anyString(), any(), any(), any(), anyInt(), anyInt(), any()))
                .thenReturn(serviceResult);

        PaginatedResult<SubscriptionDTO> response = subscriptionHandler.listSubscriptions("org1", "active", null, null,
                10, 0, "asc");
        assertNotNull(response);
        assertEquals(response.getTotal(), 1);
        assertEquals(response.getItems().size(), 1);
        assertEquals(response.getItems().get(0).getSubscriptionId(), "sub1");
    }

    @Test
    public void testGetSubscription() {
        SubscriptionDTO dto = new SubscriptionDTO();
        dto.setSubscriptionId("sub1");
        dto.setStatus(SubscriptionStatus.ACTIVE);
        when(subscriptionService.getSubscription("org1", "sub1")).thenReturn(dto);

        SubscriptionDTO response = subscriptionHandler.getSubscription("org1", "sub1");
        assertNotNull(response);
        assertEquals(response.getSubscriptionId(), "sub1");
    }

    @Test
    public void testDeleteSubscription() {
        SubscriptionDTO dto = new SubscriptionDTO();
        dto.setSubscriptionId("sub1");
        dto.setStatus(SubscriptionStatus.DELETED);
        when(subscriptionService.deleteSubscription("org1", "sub1")).thenReturn(dto);

        SubscriptionDTO response = subscriptionHandler.deleteSubscription("org1", "sub1");
        assertNotNull(response);
        assertEquals(response.getSubscriptionId(), "sub1");
        assertEquals(response.getStatus(), SubscriptionStatus.DELETED);
        verify(subscriptionService).deleteSubscription("org1", "sub1");
    }

    @Test
    public void testCreateSubscription_DerivesGroupIdFromOrgId() {
        SubscriptionDTO request = new SubscriptionDTO();
        request.setName("my-sub");
        request.setGroupId("group123");
        request.setTopics(Arrays.asList("topic1", "topic2"));

        SubscriptionDTO expectedResponse = new SubscriptionDTO();
        expectedResponse.setSubscriptionId("sub123");

        when(subscriptionService.createMultiTopicSubscription(eq(" org1 "), eq("org1"), eq("my-sub"), any(), any(), any()))
                .thenReturn(expectedResponse);

        SubscriptionDTO response = subscriptionHandler.createSubscription(" org1 ", request);

        assertNotNull(response);
        assertEquals(response.getSubscriptionId(), "sub123");
        verify(subscriptionService).createMultiTopicSubscription(eq(" org1 "), eq("org1"), eq("my-sub"), any(), any(), any());
    }

    @Test
    public void testCreateSubscription_WithoutGroupId() {
        SubscriptionDTO request = new SubscriptionDTO();
        request.setName("my-sub-2");
        request.setTopics(Collections.singletonList("topic1"));

        SubscriptionDTO expectedResponse = new SubscriptionDTO();
        expectedResponse.setSubscriptionId("sub456");

        when(subscriptionService.createMultiTopicSubscription(eq("org1"), eq("org1"), eq("my-sub-2"), any(), any(), any()))
                .thenReturn(expectedResponse);

        SubscriptionDTO response = subscriptionHandler.createSubscription("org1", request);

        assertNotNull(response);
        assertEquals(response.getSubscriptionId(), "sub456");
        verify(subscriptionService).createMultiTopicSubscription(eq("org1"), eq("org1"), eq("my-sub-2"), any(), any(), any());
    }

    @Test
    public void testCreateSubscription_WithNullRequest() {
        SubscriptionDTO expectedResponse = new SubscriptionDTO();
        expectedResponse.setSubscriptionId("sub789");

        when(subscriptionService.createMultiTopicSubscription(eq("org1"), eq("org1"), isNull(), isNull(), isNull(), isNull()))
                .thenReturn(expectedResponse);

        SubscriptionDTO response = subscriptionHandler.createSubscription("org1", null);

        assertNotNull(response);
        assertEquals(response.getSubscriptionId(), "sub789");
        verify(subscriptionService).createMultiTopicSubscription(eq("org1"), eq("org1"), isNull(), isNull(), isNull(), isNull());
    }

    @Test
    public void testCreateSubscription_WithNullOrgId() {
        SubscriptionDTO expectedResponse = new SubscriptionDTO();
        expectedResponse.setSubscriptionId("sub000");

        when(subscriptionService.createMultiTopicSubscription(isNull(), isNull(), isNull(), isNull(), isNull(), isNull()))
                .thenReturn(expectedResponse);

        SubscriptionDTO response = subscriptionHandler.createSubscription(null, null);

        assertNotNull(response);
        assertEquals(response.getSubscriptionId(), "sub000");
        verify(subscriptionService).createMultiTopicSubscription(isNull(), isNull(), isNull(), isNull(), isNull(), isNull());
    }
}
