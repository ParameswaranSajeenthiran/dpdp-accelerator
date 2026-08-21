package org.wso2.dpdp.accelerator.event.notifications.service.impl;

import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import org.wso2.dpdp.accelerator.common.config.DPDPConfigurationService;
import org.wso2.dpdp.accelerator.event.notifications.dao.DeliveryAckDAO;
import org.wso2.dpdp.accelerator.event.notifications.dao.DeliveryDAO;
import org.wso2.dpdp.accelerator.event.notifications.dao.PaginatedDAOResult;
import org.wso2.dpdp.accelerator.event.notifications.dao.SubscriptionDAO;
import org.wso2.dpdp.accelerator.event.notifications.dao.TopicDAO;
import org.wso2.dpdp.accelerator.event.notifications.dao.model.Subscription;
import org.wso2.dpdp.accelerator.event.notifications.dao.model.SubscriptionDeliverySummary;
import org.wso2.dpdp.accelerator.event.notifications.dao.model.WebhookDelivery;
import org.wso2.dpdp.accelerator.event.notifications.dao.model.WebhookDeliveryAck;
import org.wso2.dpdp.accelerator.event.notifications.dao.model.WebhookDeliveryAudit;
import org.wso2.dpdp.accelerator.event.notifications.service.dto.SubscriptionEventHistoryDTO;
import org.wso2.dpdp.accelerator.event.notifications.service.model.PaginatedResult;

import java.sql.Timestamp;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.doAnswer;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;

public class SubscriptionServiceReadAndDeleteTest {

    @Mock private SubscriptionDAO subscriptionDAO;
    @Mock private TopicDAO topicDAO;
    @Mock private DeliveryDAO deliveryDAO;
    @Mock private DeliveryAckDAO deliveryAckDAO;
    @Mock private DPDPConfigurationService configurationService;

    private SubscriptionServiceImpl service;

    @Test(expectedExceptions = org.wso2.dpdp.accelerator.event.notifications.service.exception.EventNotificationException.class)
    public void listSubscriptionsRequiresOrganization() {
        service.listSubscriptions(" ", null, null, null, 1, 0, null);
    }

    @Test(expectedExceptions = org.wso2.dpdp.accelerator.event.notifications.service.exception.EventNotificationException.class)
    public void listSubscriptionEventsRequiresSubscription() {
        service.listSubscriptionEvents("org-1", " ", 1, 0);
    }

    @Test(expectedExceptions = org.wso2.dpdp.accelerator.event.notifications.service.exception.EventNotificationException.class)
    public void subscriptionHistoryRequiresDelivery() {
        service.getSubscriptionEventHistory("org-1", "sub-1", " ");
    }

    @Test(expectedExceptions = org.wso2.dpdp.accelerator.event.notifications.service.exception.EventNotificationException.class)
    public void retriggerVerificationRequiresCallback() {
        service.retriggerVerificationTask("org-1", "sub-1", " ", "topic");
    }

    @Test(expectedExceptions = org.wso2.dpdp.accelerator.event.notifications.service.exception.EventNotificationException.class)
    public void retriggerVerificationRequiresExistingSubscription() {
        when(subscriptionDAO.getSubscriptionById("sub-1", "org-1")).thenReturn(Optional.empty());
        service.retriggerVerificationTask("org-1", "sub-1", "https://93.184.216.34:443/callback", "topic");
    }

    @Test(expectedExceptions = org.wso2.dpdp.accelerator.event.notifications.service.exception.EventNotificationException.class)
    public void retryVerificationRejectsActiveSubscription() {
        Subscription active = subscription("sub-1", "topic-1", "active");
        when(subscriptionDAO.getSubscriptionById("sub-1", "org-1"))
                .thenReturn(Optional.of(active));
        service.retryVerification("org-1", "sub-1");
    }

    @Test(expectedExceptions = org.wso2.dpdp.accelerator.event.notifications.service.exception.EventNotificationException.class)
    public void getSubscriptionRequiresId() {
        service.getSubscription("org-1", " ");
    }

    @Test(expectedExceptions = org.wso2.dpdp.accelerator.event.notifications.service.exception.EventNotificationException.class)
    public void subscriptionHistoryReportsMissingSubscription() {
        when(subscriptionDAO.getSubscriptionById("missing", "org-1")).thenReturn(Optional.empty());
        service.getSubscriptionEventHistory("org-1", "missing", "delivery");
    }

    @Test(expectedExceptions = org.wso2.dpdp.accelerator.event.notifications.service.exception.EventNotificationException.class)
    public void subscriptionHistoryReportsMissingDelivery() {
        Subscription sub = subscription("sub-1", "topic-1", "active");
        when(subscriptionDAO.getSubscriptionById("sub-1", "org-1")).thenReturn(Optional.of(sub));
        when(deliveryDAO.getSubscriptionDeliveryById("org-1", "sub-1", "missing"))
                .thenReturn(Optional.empty());
        service.getSubscriptionEventHistory("org-1", "sub-1", "missing");
    }

    @Test(expectedExceptions = org.wso2.dpdp.accelerator.event.notifications.service.exception.EventNotificationException.class)
    public void getSubscriptionRequiresOrganization() {
        service.getSubscription(" ", "sub-1");
    }

    @Test(expectedExceptions = org.wso2.dpdp.accelerator.event.notifications.service.exception.EventNotificationException.class)
    public void listSubscriptionEventsRequiresOrganization() {
        service.listSubscriptionEvents(" ", "sub-1", 1, 0);
    }

    @Test(expectedExceptions = org.wso2.dpdp.accelerator.event.notifications.service.exception.EventNotificationException.class)
    public void deleteSubscriptionReportsMissingResource() {
        when(subscriptionDAO.getSubscriptionById("missing", "org-1")).thenReturn(Optional.empty());
        service.deleteSubscription("org-1", "missing");
    }

    @BeforeMethod
    public void setUp() {
        MockitoAnnotations.openMocks(this);
        when(configurationService.getEventNotificationThreadPoolSize()).thenReturn(1);
        when(configurationService.getEventNotificationBaseBackoffSeconds()).thenReturn(1L);
        when(configurationService.getEventNotificationMaxRetries()).thenReturn(1);
        when(configurationService.isEventNotificationHttpCallbackUrlAllowed()).thenReturn(true);
        when(configurationService.getEventNotificationMaxVerificationResponseBodyBytes()).thenReturn(4096);
        service = new SubscriptionServiceImpl(subscriptionDAO, topicDAO, deliveryDAO, deliveryAckDAO,
                configurationService);
    }

    @Test
    public void listSubscriptionsMapsItemsAndNormalizesPagination() {
        Subscription sub = subscription("sub-1", "topic-1", "active");
        when(subscriptionDAO.listSubscriptions(eq("org-1"), eq("active"), eq("p"), eq("search"),
                anyInt(), eq(0), eq("createdAt")))
                .thenReturn(new PaginatedDAOResult<>(Collections.singletonList(sub), 3));
        when(topicDAO.getTopicById("topic-1", "org-1")).thenReturn(Optional.empty());

        PaginatedResult<?> result = service.listSubscriptions(" org-1 ", "active", "p", "search", 0, -1,
                "createdAt");

        assertEquals(result.getTotal(), 3);
        assertEquals(result.getItems().size(), 1);
        assertEquals(((org.wso2.dpdp.accelerator.event.notifications.service.dto.SubscriptionDTO)
                result.getItems().get(0)).getTopic(), "unknown");
        verify(subscriptionDAO).listSubscriptions("org-1", "active", "p", "search", 20, 0, "createdAt");
    }

    @Test(expectedExceptions = org.wso2.dpdp.accelerator.event.notifications.service.exception.EventNotificationException.class)
    public void getSubscriptionRejectsMissingSubscription() {
        when(subscriptionDAO.getSubscriptionById("missing", "org-1")).thenReturn(Optional.empty());
        service.getSubscription("org-1", "missing");
    }

    @Test
    public void listSubscriptionEventsUsesFallbackValues() {
        Subscription sub = subscription("sub-1", "topic-1", "active");
        when(subscriptionDAO.getSubscriptionById("sub-1", "org-1")).thenReturn(Optional.of(sub));
        SubscriptionDeliverySummary summary = new SubscriptionDeliverySummary("del-1", "evt-1", "sub-1",
                "topic", null, null, null, new Timestamp(1000), null);
        when(deliveryDAO.listSubscriptionDeliveries(eq("org-1"), eq("sub-1"), anyInt(), eq(0), any(int[].class)))
                .thenReturn(Collections.singletonList(summary));

        PaginatedResult<?> result = service.listSubscriptionEvents("org-1", "sub-1", 0, -1);

        assertEquals(result.getTotal(), 0);
        assertEquals(result.getItems().size(), 1);
        verify(deliveryDAO).listSubscriptionDeliveries(eq("org-1"), eq("sub-1"), eq(20), eq(0), any(int[].class));
    }

    @Test
    public void webhookHistoryMapsAckAndAuditAttempts() {
        Subscription sub = subscription("sub-1", "topic-1", "active");
        when(subscriptionDAO.getSubscriptionById("sub-1", "org-1")).thenReturn(Optional.of(sub));
        SubscriptionDeliverySummary summary = new SubscriptionDeliverySummary("del-1", "evt-1", "sub-1",
                "topic", "FAILED", "webhook", new Timestamp(1000), new Timestamp(900), null);
        when(deliveryDAO.getSubscriptionDeliveryById("org-1", "sub-1", "del-1"))
                .thenReturn(Optional.of(summary));
        when(deliveryDAO.getWebhookDeliveryById("del-1", "org-1"))
                .thenReturn(Optional.of(new WebhookDelivery("del-1", "sub-1", "evt-1", "FAILED", 1,
                        new Timestamp(2000), null, null, null)));
        when(deliveryAckDAO.getDeliveryAckByDeliveryId("del-1"))
                .thenReturn(Optional.of(new WebhookDeliveryAck("ack", "del-1", null, "COMPLETED", "evidence")));
        when(deliveryDAO.getWebhookDeliveryAudits("del-1", "org-1"))
                .thenReturn(Arrays.asList(
                        new WebhookDeliveryAudit("a1", "evt-1", "del-1", "org-1", "200", null, new Timestamp(3000)),
                        new WebhookDeliveryAudit("a2", "evt-1", "del-1", "org-1", "500", null, null)));

        SubscriptionEventHistoryDTO result = service.getSubscriptionEventHistory("org-1", "sub-1", "del-1");

        assertNotNull(result);
        assertEquals(result.getCompletionStatus(), "COMPLETED");
        assertEquals(result.getCompletionEvidence(), "evidence");
        assertEquals(result.getHistory().size(), 2);
        assertEquals(result.getNextRetryAt().longValue(), 2000L);
    }

    @Test
    public void deleteSubscriptionReportsInFlightConflict() {
        Subscription sub = subscription("sub-1", "topic-1", "active");
        when(subscriptionDAO.getSubscriptionById("sub-1", "org-1")).thenReturn(Optional.of(sub));
        when(subscriptionDAO.deleteSubscriptionAtomic("sub-1", "org-1", "active")).thenReturn(false);
        when(subscriptionDAO.hasPendingOrInFlightDeliveries("sub-1", "org-1")).thenReturn(true);

        try {
            service.deleteSubscription("org-1", "sub-1");
        } catch (org.wso2.dpdp.accelerator.event.notifications.service.exception.EventNotificationException e) {
            assertEquals(e.getStatusCode(), 409);
        }
    }

    @Test
    public void verificationTaskRetriesWhenCallbackFails() throws Exception {
        java.lang.Class<?> taskClass = java.util.Arrays.stream(SubscriptionServiceImpl.class.getDeclaredClasses())
                .filter(type -> type.getSimpleName().equals("WebhookVerificationTask")).findFirst().get();
        java.lang.reflect.Constructor<?> constructor = taskClass.getDeclaredConstructor(SubscriptionServiceImpl.class,
                String.class, String.class, String.class, String.class, int.class);
        constructor.setAccessible(true);
        Runnable task = (Runnable) constructor.newInstance(service, "sub-1", "org-1", "not-a-url", "topic", 0);
        task.run();
        verify(configurationService).getEventNotificationMaxRetries();
    }

    @Test
    public void verificationTaskMarksStaleAfterRetries() throws Exception {
        java.lang.Class<?> taskClass = java.util.Arrays.stream(SubscriptionServiceImpl.class.getDeclaredClasses())
                .filter(type -> type.getSimpleName().equals("WebhookVerificationTask")).findFirst().get();
        java.lang.reflect.Constructor<?> constructor = taskClass.getDeclaredConstructor(SubscriptionServiceImpl.class,
                String.class, String.class, String.class, String.class, int.class);
        constructor.setAccessible(true);
        Runnable task = (Runnable) constructor.newInstance(service, "sub-1", "org-1", "not-a-url", "topic", 1);
        task.run();
        verify(subscriptionDAO).updateSubscriptionStatus("sub-1", "org-1", "pending", "stale");
    }

    @Test
    public void verificationTaskActivatesSubscriptionWhenChallengeMatches() throws Exception {
        HttpClient httpClient = org.mockito.Mockito.mock(HttpClient.class);
        @SuppressWarnings("unchecked")
        HttpResponse<byte[]> response = (HttpResponse<byte[]>) org.mockito.Mockito.mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(200);
        doAnswer(invocation -> {
            HttpRequest request = invocation.getArgument(0);
            String query = request.uri().getQuery();
            String encoded = Arrays.stream(query.split("&"))
                    .filter(part -> part.startsWith("hub.challenge="))
                    .findFirst().get().substring("hub.challenge=".length());
            when(response.body()).thenReturn(URLDecoder.decode(encoded, StandardCharsets.UTF_8)
                    .getBytes(StandardCharsets.UTF_8));
            return response;
        }).when(httpClient).send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class));
        java.lang.reflect.Field field = SubscriptionServiceImpl.class.getDeclaredField("httpClient");
        field.setAccessible(true);
        field.set(service, httpClient);
        java.lang.Class<?> taskClass = java.util.Arrays.stream(SubscriptionServiceImpl.class.getDeclaredClasses())
                .filter(type -> type.getSimpleName().equals("WebhookVerificationTask")).findFirst().get();
        java.lang.reflect.Constructor<?> constructor = taskClass.getDeclaredConstructor(SubscriptionServiceImpl.class,
                String.class, String.class, String.class, String.class, int.class);
        constructor.setAccessible(true);
        ((Runnable) constructor.newInstance(service, "sub-1", "org-1", "https://93.184.216.34:443/callback",
                "topic", 0)).run();
        verify(subscriptionDAO).updateSubscriptionStatus("sub-1", "org-1", "pending", "active");
    }

    private Subscription subscription(String id, String topicId, String status) {
        Subscription sub = org.mockito.Mockito.mock(Subscription.class);
        when(sub.getSubscriptionId()).thenReturn(id);
        when(sub.getOrgId()).thenReturn("org-1");
        when(sub.getGroupId()).thenReturn("group-1");
        when(sub.getTopicId()).thenReturn(topicId);
        when(sub.getStatus()).thenReturn(status);
        when(sub.getPurposeFilterMode()).thenReturn("ALL");
        when(sub.getDeliveryMode()).thenReturn("POLL");
        when(sub.getPurposes()).thenReturn(Collections.emptyList());
        return sub;
    }
}
