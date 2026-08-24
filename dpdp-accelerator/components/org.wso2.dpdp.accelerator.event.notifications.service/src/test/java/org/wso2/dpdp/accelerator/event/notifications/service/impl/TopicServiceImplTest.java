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
import org.wso2.dpdp.accelerator.event.notifications.dao.PaginatedDAOResult;
import org.wso2.dpdp.accelerator.event.notifications.dao.TopicDAO;
import org.wso2.dpdp.accelerator.event.notifications.dao.model.Topic;
import org.wso2.dpdp.accelerator.event.notifications.service.dto.TopicDTO;
import org.wso2.dpdp.accelerator.event.notifications.service.exception.EventNotificationException;
import org.wso2.dpdp.accelerator.event.notifications.common.exception.EventNotificationDuplicateResourceException;
import org.wso2.dpdp.accelerator.event.notifications.common.exception.EventNotificationInvalidStateException;
import org.wso2.dpdp.accelerator.event.notifications.service.model.PaginatedResult;

import java.util.Collections;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;

public class TopicServiceImplTest {

    @Mock
    private TopicDAO topicDAO;

    private TopicServiceImpl topicService;

    @BeforeMethod
    public void setUp() {
        MockitoAnnotations.openMocks(this);
        topicService = new TopicServiceImpl(topicDAO);
    }

    @Test
    public void testCreateTopicSuccess() {
        when(topicDAO.getTopicByOrgAndName("org1", "user-consent")).thenReturn(Optional.empty());
        when(topicDAO.addTopic(any(Topic.class))).thenReturn(true);

        TopicDTO result = topicService.createTopic("org1", "user-consent", "User consent events");
        assertNotNull(result);
        assertEquals(result.getName(), "user-consent");
        assertEquals(result.getDescription(), "User consent events");
        assertEquals(result.getStatus(), "active");
    }

    @Test(expectedExceptions = EventNotificationException.class)
    public void testCreateTopicMissingOrgId() {
        topicService.createTopic(null, "user-consent", "desc");
    }

    @Test(expectedExceptions = EventNotificationException.class)
    public void testCreateTopicMissingName() {
        topicService.createTopic("org1", "", "desc");
    }

    @Test(expectedExceptions = EventNotificationException.class)
    public void testCreateTopicAlreadyExists() {
        Topic existing = new Topic("t1", "org1", "user-consent", "desc", "active");
        when(topicDAO.getTopicByOrgAndName("org1", "user-consent")).thenReturn(Optional.of(existing));

        topicService.createTopic("org1", "user-consent", "desc");
    }

    @Test
    public void testListTopics() {
        Topic topic = new Topic("t1", "org1", "user-consent", "desc", "active");
        PaginatedDAOResult<Topic> daoResult = new PaginatedDAOResult<>(Collections.singletonList(topic), 1);
        when(topicDAO.listTopics("org1", "active", null, 10, 0, "asc")).thenReturn(daoResult);

        PaginatedResult<TopicDTO> result = topicService.listTopics("org1", " ACTIVE ", null, 10, 0, "asc");
        assertNotNull(result);
        assertEquals(result.getTotal(), 1);
        assertEquals(result.getItems().size(), 1);
        assertEquals(result.getItems().get(0).getTopicId(), "t1");
    }

    @Test
    public void testDeleteTopicSuccess() {
        Topic topic = new Topic("t1", "org1", "user-consent", "desc", "active");
        when(topicDAO.getTopicById("t1", "org1")).thenReturn(Optional.of(topic));
        when(topicDAO.deregisterTopicAtomic("t1", "org1")).thenReturn(true);

        TopicDTO result = topicService.deleteTopic("org1", "t1");
        assertNotNull(result);
        assertEquals(result.getStatus(), "deregistered");
    }

    @Test(expectedExceptions = EventNotificationException.class)
    public void testDeleteTopicHasActiveSubscriptionsReturns409() {
        Topic topic = new Topic("t1", "org1", "user-consent", "desc", "active");
        when(topicDAO.getTopicById("t1", "org1")).thenReturn(Optional.of(topic));
        when(topicDAO.deregisterTopicAtomic("t1", "org1")).thenThrow(
                new EventNotificationInvalidStateException(
                        org.wso2.dpdp.accelerator.event.notifications.common.constants.EventNotificationCommonConstants.ERROR_TOPIC_HAS_ACTIVE_SUBSCRIPTIONS));

        topicService.deleteTopic("org1", "t1");
    }

    @Test(expectedExceptions = EventNotificationException.class)
    public void testDeleteTopicNotFound() {
        when(topicDAO.getTopicById("t99", "org1")).thenReturn(Optional.empty());
        topicService.deleteTopic("org1", "t99");
    }

    @Test(expectedExceptions = EventNotificationException.class)
    public void testCreateTopicDataAccessExceptionMappedTo409() {
        when(topicDAO.getTopicByOrgAndName("org1", "user-consent")).thenReturn(Optional.empty());
        when(topicDAO.addTopic(any(Topic.class))).thenThrow(
                new EventNotificationDuplicateResourceException(
                        "Duplicate key", null));

        topicService.createTopic("org1", "user-consent", "desc");
    }
}
