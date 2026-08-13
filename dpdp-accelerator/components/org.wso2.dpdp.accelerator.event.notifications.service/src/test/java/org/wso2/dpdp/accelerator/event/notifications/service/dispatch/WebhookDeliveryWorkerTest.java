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

package org.wso2.dpdp.accelerator.event.notifications.service.dispatch;

import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import org.wso2.dpdp.accelerator.event.notifications.common.enums.DeliveryStatus;
import org.wso2.dpdp.accelerator.event.notifications.dao.DeliveryDAO;
import org.wso2.dpdp.accelerator.event.notifications.dao.model.WebhookDelivery;
import org.wso2.dpdp.accelerator.event.notifications.dao.model.WebhookDeliveryDispatchContext;

import java.net.http.HttpClient;
import java.sql.Timestamp;
import java.util.Collections;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertEquals;

/**
 * Unit tests for {@link WebhookDeliveryWorker}. Verifies that the worker claims deliveries,
 * skips unclaimable rows, and falls through to the stuck-in-flight reclaim pass when the
 * pending pass is empty.
 */
public class WebhookDeliveryWorkerTest {

    @Mock
    private DeliveryDAO deliveryDAO;

    private ScheduledExecutorService scheduler;
    private HttpClient httpClient;

    @BeforeMethod
    public void setUp() {
        MockitoAnnotations.openMocks(this);
        // The worker submits WebhookDeliveryTask instances to the scheduler; we don't want
        // them to make real HTTP calls in this test. DiscardingExecutor drops the task.
        scheduler = new DiscardingExecutor();
        httpClient = HttpClient.newHttpClient();
    }

    // Minimal ScheduledExecutorService that records nothing and runs nothing — keeps the
    // worker happy without taking the network dependency.
    private static class DiscardingExecutor implements ScheduledExecutorService {
        @Override public java.util.concurrent.ScheduledFuture<?> schedule(Runnable r, long d, TimeUnit u) { return null; }
        @Override public <V> java.util.concurrent.ScheduledFuture<V> schedule(java.util.concurrent.Callable<V> c, long d, TimeUnit u) { return null; }
        @Override public java.util.concurrent.ScheduledFuture<?> scheduleAtFixedRate(Runnable r, long i, long p, TimeUnit u) { return null; }
        @Override public java.util.concurrent.ScheduledFuture<?> scheduleWithFixedDelay(Runnable r, long i, long p, TimeUnit u) { return null; }
        @Override public void execute(Runnable r) { /* discard */ }
        @Override public void shutdown() {}
        @Override public java.util.List<Runnable> shutdownNow() { return java.util.Collections.emptyList(); }
        @Override public boolean isShutdown() { return false; }
        @Override public boolean isTerminated() { return false; }
        @Override public boolean awaitTermination(long t, TimeUnit u) { return true; }
        @Override public <T> java.util.concurrent.Future<T> submit(java.util.concurrent.Callable<T> c) { return null; }
        @Override public <T> java.util.concurrent.Future<T> submit(Runnable r, T result) { return null; }
        @Override public java.util.concurrent.Future<?> submit(Runnable r) { return null; }
        @Override public <T> java.util.List<java.util.concurrent.Future<T>> invokeAll(java.util.Collection<? extends java.util.concurrent.Callable<T>> c) { return java.util.Collections.emptyList(); }
        @Override public <T> java.util.List<java.util.concurrent.Future<T>> invokeAll(java.util.Collection<? extends java.util.concurrent.Callable<T>> c, long t, TimeUnit u) { return java.util.Collections.emptyList(); }
        @Override public <T> T invokeAny(java.util.Collection<? extends java.util.concurrent.Callable<T>> c) { return null; }
        @Override public <T> T invokeAny(java.util.Collection<? extends java.util.concurrent.Callable<T>> c, long t, TimeUnit u) { return null; }
    }

    private WebhookDeliveryDispatchContext context(String deliveryId, int attemptCount) {
        return context(deliveryId, attemptCount, "topic-1", "accounts");
    }

    private WebhookDeliveryDispatchContext context(String deliveryId, int attemptCount,
            String topicId, String topicName) {
        WebhookDelivery delivery = new WebhookDelivery(
                deliveryId,
                "sub-1",
                "event-1",
                "pending",
                attemptCount,
                null,
                new Timestamp(System.currentTimeMillis() - 5_000L),
                new Timestamp(System.currentTimeMillis() - 5_000L),
                null);
        return new WebhookDeliveryDispatchContext(
                delivery,
                "org-1",
                "https://callback.example.com/hook",
                "secret",
                "{\"hello\":\"world\"}",
                delivery.getUpdatedAt(),
                topicId,
                topicName);
    }

    @Test
    public void testTickSubmitsPendingDeliveries() {
        // Default batch size is 50. Returning 50 pending rows fills the batch and skips the
        // reclaim pass — we already have a full tick of work.
        java.util.List<WebhookDeliveryDispatchContext> full = new java.util.ArrayList<>();
        for (int i = 0; i < 50; i++) {
            full.add(context("d" + i, 0));
        }
        when(deliveryDAO.getPendingWebhookDispatchContexts(anyInt())).thenReturn(full);
        when(deliveryDAO.claimWebhookDelivery(anyString())).thenReturn(true);

        WebhookDeliveryWorker worker = new WebhookDeliveryWorker(deliveryDAO, scheduler, httpClient);
        int[] counts = worker.runTick();

        assertEquals(counts[0], 50, "50 pending rows should be submitted");
        verify(deliveryDAO, never()).getStuckInFlightWebhookDispatchContexts(anyInt());
    }

    @Test
    public void testTickSkipsRowsThatCannotBeClaimed() {
        when(deliveryDAO.getPendingWebhookDispatchContexts(anyInt()))
                .thenReturn(java.util.Collections.singletonList(context("d1", 0)));
        when(deliveryDAO.claimWebhookDelivery(eq("d1"))).thenReturn(false);

        WebhookDeliveryWorker worker = new WebhookDeliveryWorker(deliveryDAO, scheduler, httpClient);
        int[] counts = worker.runTick();

        assertEquals(counts[0], 0);
    }

    @Test
    public void testEmptyPendingTriggersStuckPass() {
        // No pending rows; the second pass should pick up stuck in-flight rows.
        when(deliveryDAO.getPendingWebhookDispatchContexts(anyInt()))
                .thenReturn(Collections.emptyList());
        when(deliveryDAO.getStuckInFlightWebhookDispatchContexts(anyInt(), any()))
                .thenReturn(java.util.Collections.singletonList(context("stuck-1", 3)));
        // Stuck rows are claimed via claimStuckWebhookDelivery (cutoff-guarded), not the
        // regular claimWebhookDelivery, so the pending-claim mock is intentionally absent.
        when(deliveryDAO.claimStuckWebhookDelivery(eq("stuck-1"), any())).thenReturn(true);

        WebhookDeliveryWorker worker = new WebhookDeliveryWorker(deliveryDAO, scheduler, httpClient);
        int[] counts = worker.runTick();

        assertEquals(counts[0], 0, "no pending to submit");
        assertEquals(counts[1], 1, "one stuck row reclaimed");
    }

    @Test
    public void testMissingCallbackUrlMarksUnrecoverable() {
        WebhookDelivery delivery = new WebhookDelivery(
                "d1", "sub-1", "event-1", "pending", 0, null, new Timestamp(0), new Timestamp(0), null);
        WebhookDeliveryDispatchContext broken = new WebhookDeliveryDispatchContext(
                delivery, "org-1", null, "secret", "{}", new Timestamp(0), "topic-1", "accounts");

        when(deliveryDAO.getPendingWebhookDispatchContexts(anyInt()))
                .thenReturn(java.util.Collections.singletonList(broken));
        when(deliveryDAO.claimWebhookDelivery(eq("d1"))).thenReturn(true);
        when(deliveryDAO.updateWebhookDeliveryStatus(any())).thenReturn(true);

        WebhookDeliveryWorker worker = new WebhookDeliveryWorker(deliveryDAO, scheduler, httpClient);
        int[] counts = worker.runTick();

        assertEquals(counts[0], 0);
        verify(deliveryDAO).updateWebhookDeliveryStatus(any());
    }

    @Test
    public void testMissingPayloadMarksUnrecoverable() {
        WebhookDelivery delivery = new WebhookDelivery(
                "d1", "sub-1", "event-1", "pending", 0, null, new Timestamp(0), new Timestamp(0), null);
        WebhookDeliveryDispatchContext broken = new WebhookDeliveryDispatchContext(
                delivery, "org-1", "https://callback.example.com/hook", "secret", null,
                new Timestamp(0), "topic-1", "accounts");

        when(deliveryDAO.getPendingWebhookDispatchContexts(anyInt()))
                .thenReturn(java.util.Collections.singletonList(broken));
        when(deliveryDAO.claimWebhookDelivery(eq("d1"))).thenReturn(true);
        when(deliveryDAO.updateWebhookDeliveryStatus(any())).thenReturn(true);

        WebhookDeliveryWorker worker = new WebhookDeliveryWorker(deliveryDAO, scheduler, httpClient);
        worker.runTick();

        verify(deliveryDAO).updateWebhookDeliveryStatus(any());
    }

    @Test
    public void testFailedStatusIsUsedForUnrecoverable() {
        WebhookDelivery delivery = new WebhookDelivery(
                "d1", "sub-1", "event-1", "pending", 0, null, new Timestamp(0), new Timestamp(0), null);
        WebhookDeliveryDispatchContext broken = new WebhookDeliveryDispatchContext(
                delivery, "org-1", null, "secret", "{}", new Timestamp(0), "topic-1", "accounts");

        when(deliveryDAO.getPendingWebhookDispatchContexts(anyInt()))
                .thenReturn(java.util.Collections.singletonList(broken));
        when(deliveryDAO.claimWebhookDelivery(eq("d1"))).thenReturn(true);

        org.mockito.ArgumentCaptor<WebhookDelivery> captor =
                org.mockito.ArgumentCaptor.forClass(WebhookDelivery.class);
        when(deliveryDAO.updateWebhookDeliveryStatus(captor.capture())).thenReturn(true);

        WebhookDeliveryWorker worker = new WebhookDeliveryWorker(deliveryDAO, scheduler, httpClient);
        worker.runTick();

        assertEquals(captor.getValue().getStatus(), DeliveryStatus.FAILED.getValue());
    }
}
