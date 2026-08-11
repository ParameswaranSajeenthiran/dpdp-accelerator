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

import org.wso2.dpdp.accelerator.event.notifications.common.config.EventNotificationConfigParser;
import org.wso2.dpdp.accelerator.event.notifications.common.enums.DeliveryStatus;
import org.wso2.dpdp.accelerator.event.notifications.dao.DeliveryDAO;
import org.wso2.dpdp.accelerator.event.notifications.dao.model.WebhookDelivery;
import org.wso2.dpdp.accelerator.event.notifications.dao.model.WebhookDeliveryDispatchContext;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ScheduledExecutorService;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Batch driver for the webhook dispatch loop. One tick:
 *
 * <ol>
 *   <li>Reads up to {@code delivery_worker_batch_size} pending dispatch contexts from the
 *       DAO. Each context is a single-row join of {@code WEBHOOK_DELIVERY}, the matching
 *       {@code SUBSCRIPTION} (callback URL + shared secret), and the matching {@code EVENT}
 *       payload, so the worker can hand one object straight to {@link WebhookDeliveryTask}
 *       without further DAO calls.</li>
 *   <li>For each context, atomically flips the row to {@code in_flight} via
 *       {@link DeliveryDAO#claimWebhookDelivery(String)} so a concurrent worker does not
 *       pick the same row.</li>
 *   <li>Submits a {@link WebhookDeliveryTask} to the shared scheduler.</li>
 *   <li>Also drains stuck {@code in_flight} rows whose {@code UPDATED_AT} is older than the
 *       configured stuck threshold so a crashed worker does not permanently block delivery.</li>
 * </ol>
 *
 * <p>Owned by {@code DeliveryRecoveryService}; not an OSGi component itself because its
 * lifecycle is tied to the same {@link ScheduledExecutorService} that powers the pending
 * subscription recovery task.</p>
 */
public class WebhookDeliveryWorker implements Runnable {

    private static final Logger LOG = Logger.getLogger(WebhookDeliveryWorker.class.getName());

    private final DeliveryDAO deliveryDAO;
    private final ScheduledExecutorService scheduler;
    private final HttpClient httpClient;

    public WebhookDeliveryWorker(DeliveryDAO deliveryDAO, ScheduledExecutorService scheduler) {
        this(deliveryDAO, scheduler, defaultHttpClient());
    }

    public WebhookDeliveryWorker(DeliveryDAO deliveryDAO, ScheduledExecutorService scheduler, HttpClient httpClient) {
        this.deliveryDAO = deliveryDAO;
        this.scheduler = scheduler;
        this.httpClient = httpClient;
    }

    private static HttpClient defaultHttpClient() {
        return HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();
    }

    @Override
    public void run() {
        try {
            runTick();
        } catch (Exception e) {
            LOG.log(Level.WARNING, "Webhook delivery worker tick failed: " + e.getMessage(), e);
        }
    }

    /**
     * Visible for tests so they can drive the loop deterministically without scheduling.
     * Returns {@code int[submitted, reclaimed]} so tests can verify both the first
     * pass and the stuck-recovery pass fired.
     */
    public int[] runTick() {
        int batchSize = EventNotificationConfigParser.getInstance().getDeliveryWorkerBatchSize();
        List<WebhookDeliveryDispatchContext> pending = fetch(batchSize, false);
        int submitted = submitBatch(pending);
        // Only reclaim stuck in-flight if the pending pass was short of the batch — otherwise
        // we already have work above budget and the in-flight rows will be picked up on a
        // later tick anyway.
        int reclaimed = 0;
        if (pending.size() < batchSize) {
            List<WebhookDeliveryDispatchContext> stuck = fetch(Math.max(0, batchSize - submitted), true);
            if (!stuck.isEmpty()) {
                LOG.info("Reclaiming " + stuck.size() + " stuck in-flight webhook deliveries.");
            }
            reclaimed = submitBatch(stuck);
        }
        if (submitted + reclaimed > 0) {
            LOG.info("Webhook delivery tick: submitted=" + submitted + ", reclaimed=" + reclaimed + ".");
        }
        return new int[] { submitted, reclaimed };
    }

    private List<WebhookDeliveryDispatchContext> fetch(int limit, boolean reclaim) {
        if (limit <= 0) {
            return Collections.emptyList();
        }
        try {
            return reclaim
                    ? deliveryDAO.getStuckInFlightWebhookDispatchContexts(limit)
                    : deliveryDAO.getPendingWebhookDispatchContexts(limit);
        } catch (Exception e) {
            LOG.log(Level.WARNING,
                    "Failed to fetch " + (reclaim ? "stuck" : "pending") + " webhook deliveries: "
                            + e.getMessage(), e);
            return Collections.emptyList();
        }
    }

    private int submitBatch(List<WebhookDeliveryDispatchContext> contexts) {
        int submitted = 0;
        for (WebhookDeliveryDispatchContext ctx : contexts) {
            WebhookDelivery delivery = ctx.getDelivery();
            if (!claim(delivery.getDeliveryId())) {
                continue;
            }
            if (!isDeliverable(ctx)) {
                markUnrecoverable(delivery, "missing callback URL or event payload");
                continue;
            }
            try {
                scheduler.submit(new WebhookDeliveryTask(
                        delivery,
                        ctx.getOrgId(),
                        ctx.getPayload(),
                        ctx.getCallbackUrl(),
                        ctx.getSharedSecret(),
                        deliveryDAO,
                        httpClient));
                submitted++;
            } catch (Exception e) {
                LOG.log(Level.SEVERE, "Failed to submit WebhookDeliveryTask for delivery ["
                        + delivery.getDeliveryId() + "]: " + e.getMessage(), e);
                markUnrecoverable(delivery, "scheduler rejected task");
            }
        }
        return submitted;
    }

    private boolean claim(String deliveryId) {
        try {
            return deliveryDAO.claimWebhookDelivery(deliveryId);
        } catch (Exception e) {
            LOG.log(Level.WARNING, "claimWebhookDelivery failed for [" + deliveryId + "]: "
                    + e.getMessage(), e);
            return false;
        }
    }

    private static boolean isDeliverable(WebhookDeliveryDispatchContext ctx) {
        String url = ctx.getCallbackUrl();
        if (url == null || url.trim().isEmpty()) {
            return false;
        }
        String payload = ctx.getPayload();
        if (payload == null) {
            return false;
        }
        return true;
    }

    /**
     * Best-effort flip to {@code failed} for a delivery whose claim succeeded but whose task
     * could not be hydrated. We deliberately skip an audit row here — the operator can see
     * the FAILED status and the reason in the worker logs.
     */
    private void markUnrecoverable(WebhookDelivery delivery, String reason) {
        LOG.warning("Marking webhook delivery [" + delivery.getDeliveryId() + "] unrecoverable: " + reason);
        WebhookDelivery failed = new WebhookDelivery(
                delivery.getDeliveryId(),
                delivery.getSubscriptionId(),
                delivery.getEventId(),
                DeliveryStatus.FAILED.getValue(),
                delivery.getAttemptCount(),
                null,
                delivery.getCreatedAt(),
                new java.sql.Timestamp(System.currentTimeMillis()),
                null);
        try {
            deliveryDAO.updateWebhookDeliveryStatus(failed);
        } catch (Exception e) {
            LOG.log(Level.SEVERE, "Failed to mark unrecoverable webhook delivery ["
                    + delivery.getDeliveryId() + "] as failed: " + e.getMessage(), e);
        }
    }
}
