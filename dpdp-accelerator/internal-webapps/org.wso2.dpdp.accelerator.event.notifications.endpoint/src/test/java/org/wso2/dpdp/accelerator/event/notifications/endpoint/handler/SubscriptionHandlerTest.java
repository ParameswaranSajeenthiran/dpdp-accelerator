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

import java.util.Collections;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
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

        when(subscriptionService.listSubscriptions(anyString(), any(), any(), any(), anyInt(), anyInt(), any())).thenReturn(serviceResult);

        PaginatedResult<SubscriptionDTO> response = subscriptionHandler.listSubscriptions("org1", "active", null, null, 10, 0, "asc");
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
        doNothing().when(subscriptionService).deleteSubscription("org1", "sub1");
        subscriptionHandler.deleteSubscription("org1", "sub1");
        verify(subscriptionService).deleteSubscription("org1", "sub1");
    }
}
