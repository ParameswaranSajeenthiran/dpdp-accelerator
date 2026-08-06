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

import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import org.wso2.dpdp.accelerator.event.notifications.service.dao.DeliveryAckDAO;
import org.wso2.dpdp.accelerator.event.notifications.service.dao.DeliveryDAO;
import org.wso2.dpdp.accelerator.event.notifications.service.dao.SubscriptionDAO;
import org.wso2.dpdp.accelerator.event.notifications.service.dao.TopicDAO;
import org.wso2.dpdp.accelerator.event.notifications.service.dao.model.Subscription;
import org.wso2.dpdp.accelerator.event.notifications.service.dao.model.Topic;
import org.wso2.dpdp.accelerator.event.notifications.service.dto.DeliveryConfigDTO;
import org.wso2.dpdp.accelerator.event.notifications.service.dto.FilterDTO;
import org.wso2.dpdp.accelerator.event.notifications.service.dto.SubscriptionDTO;
import org.wso2.dpdp.accelerator.event.notifications.service.enums.DeliveryMode;
import org.wso2.dpdp.accelerator.event.notifications.service.enums.PurposeFilterMode;
import org.wso2.dpdp.accelerator.event.notifications.service.enums.SubscriptionStatus;
import org.wso2.dpdp.accelerator.event.notifications.service.exception.ENFException;
import org.wso2.dpdp.accelerator.event.notifications.service.model.PaginatedResult;

import java.sql.Timestamp;
import java.util.Collections;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertTrue;

public class SubscriptionServiceImplTest {

    @Mock
    private SubscriptionDAO subscriptionDAO;

    @Mock
    private TopicDAO topicDAO;

    @Mock
    private DeliveryDAO deliveryDAO;

    @Mock
    private DeliveryAckDAO deliveryAckDAO;

    private SubscriptionServiceImpl subscriptionService;

    @BeforeMethod
    public void setUp() {
        MockitoAnnotations.openMocks(this);
        subscriptionService = new SubscriptionServiceImpl(subscriptionDAO, topicDAO, deliveryDAO, deliveryAckDAO);
    }

    @Test
    public void testCreatePollSubscriptionSuccess() {
        Topic topic = new Topic("t1", "org1", "user-consent", "desc", "active");
        when(topicDAO.getTopicByOrgAndName("org1", "user-consent")).thenReturn(Optional.of(topic));
        when(subscriptionDAO.findDuplicateSubscription(anyString(), anyString(), anyString(), anyString(), any())).thenReturn(Optional.empty());
        when(subscriptionDAO.getActiveSubscriptionsForMatching(anyString(), anyString(), anyString())).thenReturn(Collections.emptyList());
        when(subscriptionDAO.addSubscription(any(Subscription.class))).thenReturn(true);

        FilterDTO filter = new FilterDTO(PurposeFilterMode.ALL, Collections.emptyList());
        DeliveryConfigDTO delivery = new DeliveryConfigDTO(DeliveryMode.POLL, null, "secret123");

        SubscriptionDTO result = subscriptionService.createSubscription("org1", "group1", "user-consent", filter, delivery);
        assertNotNull(result);
        assertEquals(result.getStatus(), SubscriptionStatus.ACTIVE);
    }

    @Test(expectedExceptions = ENFException.class)
    public void testCreateSubscriptionMissingTopic() {
        FilterDTO filter = new FilterDTO(PurposeFilterMode.ALL, Collections.emptyList());
        DeliveryConfigDTO delivery = new DeliveryConfigDTO(DeliveryMode.POLL, null, "secret123");
        subscriptionService.createSubscription("org1", "group1", null, filter, delivery);
    }

    @Test(expectedExceptions = ENFException.class)
    public void testCreateSpecificSubscriptionMissingPurposes() {
        FilterDTO filter = new FilterDTO(PurposeFilterMode.SPECIFIC, Collections.emptyList());
        DeliveryConfigDTO delivery = new DeliveryConfigDTO(DeliveryMode.POLL, null, "secret123");
        subscriptionService.createSubscription("org1", "group1", "topic1", filter, delivery);
    }

    @Test
    public void testCreateSubscriptionDuplicateReturnsAlreadyExists() {
        Topic topic = new Topic("t1", "org1", "user-consent", "desc", "active");
        when(topicDAO.getTopicByOrgAndName("org1", "user-consent")).thenReturn(Optional.of(topic));

        Subscription existing = new Subscription("sub-existing", "org1", "group1", "t1", "active", "all", null, "secret", new Timestamp(System.currentTimeMillis()), new Timestamp(System.currentTimeMillis()), "poll");
        when(subscriptionDAO.findDuplicateSubscription(anyString(), anyString(), anyString(), anyString(), any())).thenReturn(Optional.of(existing));

        FilterDTO filter = new FilterDTO(PurposeFilterMode.ALL, Collections.emptyList());
        DeliveryConfigDTO delivery = new DeliveryConfigDTO(DeliveryMode.POLL, null, "secret123");

        SubscriptionDTO result = subscriptionService.createSubscription("org1", "group1", "user-consent", filter, delivery);
        assertNotNull(result);
        assertTrue(Boolean.TRUE.equals(result.getAlreadyExists()));
        assertEquals(result.getSubscriptionId(), "sub-existing");
    }

    @Test
    public void testListSubscriptions() {
        Subscription sub = new Subscription("sub1", "org1", "group1", "t1", "active", "all", null, "secret", new Timestamp(System.currentTimeMillis()), new Timestamp(System.currentTimeMillis()), "poll");
        PaginatedResult<Subscription> daoResult = new PaginatedResult<>(Collections.singletonList(sub), 1);
        when(subscriptionDAO.listSubscriptions("org1", "active", null, null, 10, 0, "asc")).thenReturn(daoResult);
        when(topicDAO.getTopicById("t1")).thenReturn(Optional.of(new Topic("t1", "org1", "user-consent", "desc", "active")));

        PaginatedResult<SubscriptionDTO> result = subscriptionService.listSubscriptions("org1", "active", null, null, 10, 0, "asc");
        assertNotNull(result);
        assertEquals(result.getTotal(), 1);
        assertEquals(result.getItems().get(0).getSubscriptionId(), "sub1");
    }

    @Test
    public void testGetSubscriptionSuccess() {
        Subscription sub = new Subscription("sub1", "org1", "group1", "t1", "active", "all", null, "secret", new Timestamp(System.currentTimeMillis()), new Timestamp(System.currentTimeMillis()), "poll");
        when(subscriptionDAO.getSubscriptionById("sub1", "org1")).thenReturn(Optional.of(sub));
        when(topicDAO.getTopicById("t1")).thenReturn(Optional.of(new Topic("t1", "org1", "user-consent", "desc", "active")));

        SubscriptionDTO result = subscriptionService.getSubscription("org1", "sub1");
        assertNotNull(result);
        assertEquals(result.getSubscriptionId(), "sub1");
    }

    @Test
    public void testDeleteSubscriptionSuccess() {
        Subscription sub = new Subscription("sub1", "org1", "group1", "t1", "active", "all", null, "secret", new Timestamp(System.currentTimeMillis()), new Timestamp(System.currentTimeMillis()), "poll");
        when(subscriptionDAO.getSubscriptionById("sub1", "org1")).thenReturn(Optional.of(sub));
        when(subscriptionDAO.hasPendingOrInFlightDeliveries("sub1")).thenReturn(false);
        when(subscriptionDAO.updateSubscriptionStatus(eq("sub1"), anyString())).thenReturn(true);

        subscriptionService.deleteSubscription("org1", "sub1");
        verify(subscriptionDAO).updateSubscriptionStatus(eq("sub1"), anyString());
    }
}
