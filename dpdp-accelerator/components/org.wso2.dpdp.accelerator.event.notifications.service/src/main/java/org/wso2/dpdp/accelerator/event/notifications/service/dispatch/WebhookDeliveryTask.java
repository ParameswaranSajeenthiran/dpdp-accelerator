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
import org.wso2.dpdp.accelerator.event.notifications.common.util.HmacSigner;
import org.wso2.dpdp.accelerator.event.notifications.dao.DeliveryDAO;
import org.wso2.dpdp.accelerator.event.notifications.dao.model.WebhookDelivery;
import org.wso2.dpdp.accelerator.event.notifications.dao.model.WebhookDeliveryAudit;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.sql.Timestamp;
import java.time.Duration;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Single-delivery unit of work. Pulled off the {@link DeliveryDAO} batch by
 * {@link WebhookDeliveryWorker} and executed on the shared
 * {@link java.util.concurrent.ScheduledExecutorService}.
 *
 * <p>For each invocation the task:</p>
 * <ol>
 *   <li>POSTs the payload to the subscriber's callback URL with an HMAC-SHA256 signature in
 *       the {@code Event-Signature} header.</li>
 *   <li>Writes exactly one {@code WEBHOOK_DELIVERY_AUDIT} row regardless of outcome.</li>
 *   <li>On a 2xx success, marks the delivery {@code delivered} with the current timestamp.</li>
 *   <li>On a non-2xx response or an exception, increments {@code ATTEMPT_COUNT}, computes the
 *       next retry timestamp with exponential backoff, and either releases the row back to
 *       {@code pending} (if retries remain) or marks it terminal {@code failed}.</li>
 * </ol>
 *
 * <p>Mirror of the shape used by
 * {@code SubscriptionServiceImpl.WebhookVerificationTask}, on purpose, so the two retry
 * subsystems stay easy to compare.</p>
 */
public class WebhookDeliveryTask implements Runnable {

    private static final Logger LOG = Logger.getLogger(WebhookDeliveryTask.class.getName());

    // Constants kept here (not in EventNotificationCommonConstants) so they stay scoped to
    // outbound HTTP delivery and don't leak into the common module.
    private static final String EVENT_SIGNATURE_HEADER = "Event-Signature";
    private static final String CONTENT_TYPE_HEADER = "Content-Type";
    private static final String CONTENT_TYPE_JSON = "application/json";
    private static final String RESPONSE_CODE_EXCEPTION = "EXCEPTION";
    private static final Duration HTTP_TIMEOUT = Duration.ofSeconds(5);

    private final WebhookDelivery delivery;
    private final String orgId;
    private final String payload;
    private final String callbackUrl;
    private final String sharedSecret;
    private final DeliveryDAO deliveryDAO;
    private final HttpClient httpClient;

    public WebhookDeliveryTask(WebhookDelivery delivery, String orgId, String payload, String callbackUrl,
            String sharedSecret, DeliveryDAO deliveryDAO, HttpClient httpClient) {
        this.delivery = delivery;
        this.orgId = orgId;
        this.payload = payload;
        this.callbackUrl = callbackUrl;
        this.sharedSecret = sharedSecret;
        this.deliveryDAO = deliveryDAO;
        this.httpClient = httpClient;
    }

    @Override
    public void run() {
        String responseCode;
        try {
            responseCode = dispatch();
        } catch (Exception e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            LOG.log(Level.WARNING, "Webhook delivery attempt failed for delivery ["
                    + delivery.getDeliveryId() + "]: " + e.getMessage(), e);
            recordFailure(RESPONSE_CODE_EXCEPTION, null);
            return;
        }

        int httpStatus = Integer.parseInt(responseCode);
        if (httpStatus >= 200 && httpStatus < 300) {
            recordSuccess(httpStatus);
        } else {
            recordFailure(responseCode, null);
        }
    }

    /**
     * Builds the POST, signs it, and returns the HTTP status code as a string. Interrupts and
     * IO errors propagate to the caller.
     */
    private String dispatch() throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(callbackUrl))
                .timeout(HTTP_TIMEOUT)
                .header(CONTENT_TYPE_HEADER, CONTENT_TYPE_JSON)
                .POST(HttpRequest.BodyPublishers.ofString(payload));
        String signature = HmacSigner.sign(sharedSecret, payload);
        if (signature != null) {
            builder.header(EVENT_SIGNATURE_HEADER, "sha256=" + signature);
        }
        HttpResponse<String> response = httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());
        return String.valueOf(response.statusCode());
    }

    private void recordSuccess(int httpStatus) {
        Timestamp now = new Timestamp(System.currentTimeMillis());
        try {
            WebhookDeliveryAudit audit = newAudit(now, String.valueOf(httpStatus));
            deliveryDAO.addWebhookDeliveryAudit(audit);
        } catch (Exception e) {
            LOG.log(Level.WARNING, "Failed to write audit row for successful delivery ["
                    + delivery.getDeliveryId() + "]: " + e.getMessage(), e);
        }

        WebhookDelivery updated = new WebhookDelivery(
                delivery.getDeliveryId(),
                delivery.getSubscriptionId(),
                delivery.getEventId(),
                DeliveryStatus.DELIVERED.getValue(),
                delivery.getAttemptCount() + 1,
                null,
                delivery.getCreatedAt(),
                now,
                now);
        try {
            deliveryDAO.updateWebhookDeliveryStatus(updated);
            LOG.info("Webhook delivered [delivery=" + delivery.getDeliveryId() + ", attempt="
                    + updated.getAttemptCount() + ", status=" + httpStatus + "].");
        } catch (Exception e) {
            LOG.log(Level.WARNING, "Failed to mark delivery [" + delivery.getDeliveryId()
                    + "] as delivered: " + e.getMessage(), e);
        }
    }

    private void recordFailure(String responseCode, Throwable cause) {
        Timestamp now = new Timestamp(System.currentTimeMillis());
        try {
            WebhookDeliveryAudit audit = newAudit(now, responseCode);
            deliveryDAO.addWebhookDeliveryAudit(audit);
        } catch (Exception e) {
            LOG.log(Level.WARNING, "Failed to write audit row for failed delivery ["
                    + delivery.getDeliveryId() + "]: " + e.getMessage(), e);
        }

        int newAttempt = delivery.getAttemptCount() + 1;
        int maxRetries = EventNotificationConfigParser.getInstance().getMaxRetries();
        if (newAttempt >= maxRetries) {
            WebhookDelivery failed = new WebhookDelivery(
                    delivery.getDeliveryId(),
                    delivery.getSubscriptionId(),
                    delivery.getEventId(),
                    DeliveryStatus.FAILED.getValue(),
                    newAttempt,
                    null,
                    delivery.getCreatedAt(),
                    now,
                    null);
            try {
                deliveryDAO.updateWebhookDeliveryStatus(failed);
                LOG.warning("Webhook delivery [" + delivery.getDeliveryId() + "] exhausted "
                        + maxRetries + " attempts; marked as failed (last response=" + responseCode + ").");
            } catch (Exception e) {
                LOG.log(Level.SEVERE, "Failed to mark delivery [" + delivery.getDeliveryId()
                        + "] as failed: " + e.getMessage(), e);
            }
            return;
        }

        long baseBackoffSeconds = EventNotificationConfigParser.getInstance().getBaseBackoffSeconds();
        long delaySeconds = baseBackoffSeconds * (long) Math.pow(3, newAttempt - 1);
        Timestamp nextRetryAt = new Timestamp(now.getTime() + delaySeconds * 1000L);

        try {
            boolean released = deliveryDAO.releaseWebhookDelivery(delivery.getDeliveryId(), newAttempt, nextRetryAt);
            if (released) {
                LOG.info("Webhook delivery [" + delivery.getDeliveryId() + "] attempt " + newAttempt
                        + " failed (response=" + responseCode + "); next retry at " + nextRetryAt
                        + " (~" + delaySeconds + "s).");
            } else {
                LOG.fine("Webhook delivery [" + delivery.getDeliveryId()
                        + "] was no longer in_flight; release was a no-op.");
            }
        } catch (Exception e) {
            LOG.log(Level.SEVERE, "Failed to release webhook delivery [" + delivery.getDeliveryId()
                    + "] for retry: " + e.getMessage(), e);
        }
    }

    private WebhookDeliveryAudit newAudit(Timestamp now, String responseCode) {
        return new WebhookDeliveryAudit(
                UUID.randomUUID().toString(),
                delivery.getEventId(),
                delivery.getDeliveryId(),
                orgId,
                responseCode,
                now,
                now);
    }
}
