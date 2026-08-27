package org.wso2.dpdp.accelerator.event.notifications.service.impl;

import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import org.wso2.dpdp.accelerator.event.notifications.dao.TopicDAO;
import org.wso2.dpdp.accelerator.event.notifications.dao.model.Topic;
import org.wso2.dpdp.accelerator.event.notifications.common.enums.Initiator;

import java.util.Optional;

import static org.mockito.Mockito.when;

public class TopicServiceBranchesTest {
    @Mock private TopicDAO dao;
    private TopicServiceImpl service;

    @BeforeMethod
    public void setUp() {
        MockitoAnnotations.openMocks(this);
        service = new TopicServiceImpl(dao);
    }

    @Test(expectedExceptions = org.wso2.dpdp.accelerator.event.notifications.service.exception.EventNotificationException.class)
    public void createTopicFalseAddIsRejected() {
        when(dao.getTopicByOrgAndName("org", "topic")).thenReturn(Optional.empty());
        when(dao.addTopic(org.mockito.ArgumentMatchers.any(Topic.class))).thenReturn(false);
        service.createTopic("org", "topic", null);
    }

    @Test(expectedExceptions = org.wso2.dpdp.accelerator.event.notifications.service.exception.EventNotificationException.class)
    public void deleteSystemTopicIsRejected() {
        Topic topic = new Topic("t", "org", "system", null, "active", Initiator.SYSTEM.getValue());
        when(dao.getTopicById("t", "org")).thenReturn(Optional.of(topic));
        service.deleteTopic("org", "t");
    }

    @Test(expectedExceptions = org.wso2.dpdp.accelerator.event.notifications.service.exception.EventNotificationException.class)
    public void deleteDeregisteredTopicIsRejected() {
        Topic topic = new Topic("t", "org", "topic", null, "deregistered", Initiator.USER.getValue());
        when(dao.getTopicById("t", "org")).thenReturn(Optional.of(topic));
        service.deleteTopic("org", "t");
    }

    @Test(expectedExceptions = org.wso2.dpdp.accelerator.event.notifications.service.exception.EventNotificationException.class)
    public void deleteFalseUpdateIsRejected() {
        Topic topic = new Topic("t", "org", "topic", null, "active", Initiator.USER.getValue());
        when(dao.getTopicById("t", "org")).thenReturn(Optional.of(topic));
        when(dao.deregisterTopicAtomic("t", "org")).thenReturn(false);
        service.deleteTopic("org", "t");
    }

    @Test
    public void getTopicReturnsEmptyForWrongOrgAndReturnsMappedTopic() {
        Topic wrong = new Topic("t", "other", "topic", null, "active", Initiator.USER.getValue());
        when(dao.getTopicById("t", "org")).thenReturn(Optional.of(wrong));
        org.testng.Assert.assertTrue(service.getTopic("org", "t").isEmpty());
        when(dao.getTopicById("t", "org")).thenReturn(Optional.of(
                new Topic("t", "org", "topic", "desc", "active", Initiator.USER.getValue())));
        org.testng.Assert.assertEquals(service.getTopic("org", "t").get().getName(), "topic");
    }
}
