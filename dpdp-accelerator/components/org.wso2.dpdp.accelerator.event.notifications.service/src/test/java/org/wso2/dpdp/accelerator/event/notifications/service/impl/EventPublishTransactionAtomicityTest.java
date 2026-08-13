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
import org.wso2.dpdp.accelerator.event.notifications.common.enums.TopicStatus;
import org.wso2.dpdp.accelerator.event.notifications.common.exception.EventNotificationDataAccessException;
import org.wso2.dpdp.accelerator.event.notifications.dao.EventDAO;
import org.wso2.dpdp.accelerator.event.notifications.dao.TopicDAO;
import org.wso2.dpdp.accelerator.event.notifications.dao.model.Topic;
import org.wso2.dpdp.accelerator.event.notifications.service.EventFanOutService;
import org.wso2.dpdp.accelerator.event.notifications.service.exception.EventNotificationException;

import java.util.Arrays;
import java.util.Collections;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.fail;

public class EventPublishTransactionAtomicityTest {

    @Mock
    private TopicDAO topicDAO;

    @Mock
    private EventDAO eventDAO;

    @Mock
    private EventFanOutService eventFanOutService;

    private EventPublishServiceImpl publishService;

    @BeforeMethod
    public void setUp() {
        MockitoAnnotations.openMocks(this);
        publishService = new EventPublishServiceImpl(eventDAO, topicDAO, eventFanOutService);
    }

    @Test
    public void testFanOutFailureCausesEventPublishToFailWith500() {
        String orgId = "org-1";
        String groupId = "group-1";
        String topicName = "consent.update";
        Topic activeTopic = new Topic("topic-123", orgId, topicName, "desc", TopicStatus.ACTIVE.getValue());

        when(topicDAO.getTopicByOrgAndName(orgId, topicName)).thenReturn(Optional.of(activeTopic));
        when(eventDAO.addEvent(any())).thenReturn(true);
        doThrow(new EventNotificationDataAccessException("Fanout DB write failure"))
                .when(eventFanOutService).fanOutEvent(any(), any());

        try {
            publishService.publishEvent(orgId, groupId, topicName, Arrays.asList("purpose-1"), Collections.emptyMap());
            fail("Expected EventNotificationException on fan-out failure");
        } catch (EventNotificationException e) {
            assertEquals(e.getStatusCode(), 500);
            verify(eventFanOutService).fanOutEvent(any(), any());
        }
    }
}
