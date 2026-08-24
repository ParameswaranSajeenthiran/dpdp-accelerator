/*
 * Copyright (c) 2026, WSO2 LLC. (https://www.wso2.com).
 *
 * WSO2 LLC. licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License.
 */
package org.wso2.dpdp.accelerator.event.notifications.service.recovery;

import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import org.wso2.dpdp.accelerator.common.config.DPDPConfigurationService;
import org.wso2.dpdp.accelerator.event.notifications.dao.DeliveryDAO;
import org.wso2.dpdp.accelerator.event.notifications.dao.SubscriptionDAO;
import org.wso2.dpdp.accelerator.event.notifications.dao.model.Subscription;
import org.wso2.dpdp.accelerator.event.notifications.service.SubscriptionService;

import java.lang.reflect.Constructor;
import java.sql.Timestamp;
import java.util.Arrays;
import java.util.Collections;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class DeliveryRecoveryServiceTest {

    @Mock
    private SubscriptionDAO subscriptionDAO;
    @Mock
    private DeliveryDAO deliveryDAO;
    @Mock
    private SubscriptionService subscriptionService;
    @Mock
    private DPDPConfigurationService configurationService;

    private DeliveryRecoveryService recoveryService;

    @BeforeMethod
    public void setUp() {
        MockitoAnnotations.openMocks(this);
        when(configurationService.getEventNotificationThreadPoolSize()).thenReturn(2);
        when(configurationService.getEventNotificationDeliveryWorkerPollSeconds()).thenReturn(5);
        when(configurationService.getEventNotificationPendingSubscriptionRecoveryThresholdSeconds())
                .thenReturn(60);
        when(configurationService.getEventNotificationBackgroundWorkerInitialDelaySeconds()).thenReturn(10);
        when(configurationService.getEventNotificationPendingSubscriptionRecoveryIntervalSeconds()).thenReturn(30);
        when(configurationService.getEventNotificationPendingSubscriptionRecoveryBatchSize()).thenReturn(20);
        when(configurationService.getEventNotificationWorkerShutdownTimeoutSeconds()).thenReturn(5);
        recoveryService = new DeliveryRecoveryService(subscriptionDAO, deliveryDAO,
                subscriptionService, configurationService);
    }

    @Test
    public void pendingSubscriptionsWithCallbacksAreRetried() throws Exception {
        Subscription retryable = subscription("retryable", "https://example.com:443/callback");
        Subscription withoutCallback = subscription("without-callback", " ");
        when(subscriptionDAO.getPendingSubscriptionsForRecovery(any(Timestamp.class), anyInt()))
                .thenReturn(Arrays.asList(retryable, withoutCallback));

        runPendingRecoveryTask();

        verify(subscriptionService).retryVerification("org1", "retryable");
    }

    @Test
    public void retryFailureDoesNotAbortOtherRecoveryRuns() throws Exception {
        Subscription retryable = subscription("retryable", "https://example.com:443/callback");
        when(subscriptionDAO.getPendingSubscriptionsForRecovery(any(Timestamp.class), anyInt()))
                .thenReturn(Collections.singletonList(retryable));
        doThrow(new RuntimeException("verification unavailable"))
                .when(subscriptionService).retryVerification("org1", "retryable");

        runPendingRecoveryTask();

        verify(subscriptionService).retryVerification("org1", "retryable");
    }

    @Test
    public void serviceCanActivateAndDeactivate() {
        recoveryService.activate();
        recoveryService.deactivate();
        recoveryService.deactivate();
    }

    private void runPendingRecoveryTask() throws Exception {
        Class<?> taskClass = Arrays.stream(DeliveryRecoveryService.class.getDeclaredClasses())
                .filter(clazz -> clazz.getSimpleName().equals("PendingDeliveryRecoveryTask"))
                .findFirst()
                .orElseThrow();
        Constructor<?> constructor = taskClass.getDeclaredConstructor(DeliveryRecoveryService.class);
        constructor.setAccessible(true);
        Runnable task = (Runnable) constructor.newInstance(recoveryService);
        task.run();
    }

    private Subscription subscription(String id, String callbackUrl) {
        Timestamp now = new Timestamp(System.currentTimeMillis());
        return new Subscription(id, "org1", "group1", "topic1", "ALL", Collections.emptyList(),
                "WEBHOOK", callbackUrl, "secret", "PENDING", now, now);
    }
}
