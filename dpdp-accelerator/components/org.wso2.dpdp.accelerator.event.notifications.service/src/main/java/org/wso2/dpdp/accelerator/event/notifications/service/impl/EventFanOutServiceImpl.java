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

import org.wso2.dpdp.accelerator.event.notifications.common.enums.DeliveryMode;
import org.wso2.dpdp.accelerator.event.notifications.common.enums.DeliveryStatus;
import org.wso2.dpdp.accelerator.event.notifications.common.enums.PollStatus;
import org.wso2.dpdp.accelerator.event.notifications.common.enums.SubscriptionStatus;
import org.wso2.dpdp.accelerator.common.util.LogSanitizer;
import org.wso2.dpdp.accelerator.event.notifications.dao.DeliveryDAO;
import org.wso2.dpdp.accelerator.event.notifications.dao.SubscriptionDAO;
import org.wso2.dpdp.accelerator.event.notifications.dao.model.Event;
import org.wso2.dpdp.accelerator.event.notifications.dao.model.PollDelivery;
import org.wso2.dpdp.accelerator.event.notifications.dao.model.Subscription;
import org.wso2.dpdp.accelerator.event.notifications.dao.model.WebhookDelivery;
import org.wso2.dpdp.accelerator.event.notifications.service.EventFanOutService;
import org.wso2.dpdp.accelerator.event.notifications.service.matching.FilterMatcher;

import java.sql.Connection;
import java.sql.Timestamp;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * Default {@link EventFanOutService} implementation.
 *
 * <p>For each persisted event the service:</p>
 * <ol>
 *     <li>Loads active subscriptions for the event's {@code (orgId, topicId)}.</li>
 *     <li>Filters by group: a subscription whose {@code groupId} is non-null must
 *         match the event's {@code groupId}; a null/blank subscription groupId
 *         matches any event group.</li>
 *     <li>Filters by purpose via {@link FilterMatcher}.</li>
 *     <li>For each surviving subscription, creates a {@code WEBHOOK_DELIVERY} row
 *         in {@code pending} status. The existing
 *         {@code WebhookDeliveryWorker} picks up the row on its next tick and
 *         POSTs the payload to the subscriber's callback URL with HMAC.</li>
 * </ol>
 *
 * <p>Poll-mode subscriptions receive a pending poll-delivery row in the same
 * transaction as the event and webhook delivery rows.</p>
 */
public class EventFanOutServiceImpl implements EventFanOutService {

    private static final Log LOG = LogFactory.getLog(EventFanOutServiceImpl.class);

    private SubscriptionDAO subscriptionDAO;

    private DeliveryDAO deliveryDAO;

    public EventFanOutServiceImpl() {
    }

    public EventFanOutServiceImpl(SubscriptionDAO subscriptionDAO, DeliveryDAO deliveryDAO) {
        this.subscriptionDAO = subscriptionDAO;
        this.deliveryDAO = deliveryDAO;
    }

    @Override
    public void fanOutEvent(Connection conn, Event event, List<String> eventPurposes) {
        if (event == null) {
            return;
        }

        if (conn == null) {
            throw new IllegalArgumentException("Connection cannot be null for transactional event fan-out.");
        }
        List<Subscription> candidates = subscriptionDAO.getActiveSubscriptionsForFanOut(
                conn, event.getOrgId(), event.getTopicId());

        // Defensive dedupe — DAO should not return the same subscription twice but a downstream
        // bug could; we cap fan-out to one delivery per subscription per event.
        Set<String> processed = new HashSet<>();
        Timestamp now = new Timestamp(System.currentTimeMillis());

        for (Subscription subscription : candidates) {
            if (subscription.getSubscriptionId() == null
                    || !processed.add(subscription.getSubscriptionId())) {
                continue;
            }
            if (!SubscriptionStatus.ACTIVE.getValue().equals(subscription.getStatus())) {
                continue;
            }
            if (!matchesGroup(subscription, event.getGroupId())) {
                continue;
            }
            if (!FilterMatcher.matches(subscription.getPurposeFilterMode(), subscription.getPurposes(),
                    eventPurposes)) {
                continue;
            }

            DeliveryMode mode = DeliveryMode.fromValueOrDefault(subscription.getDeliveryMode(),
                    DeliveryMode.WEBHOOK);
            if (mode == DeliveryMode.WEBHOOK) {
                queueWebhookDelivery(conn, subscription, event, now);
            } else {
                queuePollDelivery(conn, subscription, event, now);
            }
        }
    }

    private static boolean matchesGroup(Subscription subscription, String eventGroupId) {
        String subscriptionGroupId = subscription.getGroupId();
        if (subscriptionGroupId == null || subscriptionGroupId.trim().isEmpty()) {
            // Subscription accepts any group.
            return true;
        }
        if (eventGroupId == null) {
            return false;
        }
        return subscriptionGroupId.trim().equalsIgnoreCase(eventGroupId.trim());
    }

    private void queueWebhookDelivery(java.sql.Connection conn, Subscription subscription, Event event, Timestamp now) {
        String deliveryId = UUID.randomUUID().toString();
        WebhookDelivery delivery = new WebhookDelivery(
                deliveryId,
                subscription.getSubscriptionId(),
                event.getEventId(),
                DeliveryStatus.PENDING.getValue(),
                0,
                null,
                now,
                now,
                null);
        boolean saved = deliveryDAO.addWebhookDelivery(conn, delivery);
        if (saved) {
            if (LOG.isInfoEnabled()) {
                LOG.info("Queued webhook delivery [" + LogSanitizer.sanitize(deliveryId) + "] for subscription ["
                        + LogSanitizer.sanitize(subscription.getSubscriptionId()) + "] on event ["
                        + LogSanitizer.sanitize(event.getEventId()) + "].");
            }
        } else {
            throw new org.wso2.dpdp.accelerator.event.notifications.common.exception.EventNotificationDataAccessException(
                    "Failed to queue webhook delivery for subscription ["
                            + LogSanitizer.sanitize(subscription.getSubscriptionId())
                            + "] on event [" + LogSanitizer.sanitize(event.getEventId())
                            + "] — DAO returned false.");
        }
    }

    private void queuePollDelivery(Connection conn, Subscription subscription, Event event, Timestamp now) {
        PollDelivery delivery = new PollDelivery(
                UUID.randomUUID().toString(),
                subscription.getSubscriptionId(),
                event.getEventId(),
                PollStatus.PENDING.getValue(),
                now,
                null);
        boolean saved = deliveryDAO.addPollDelivery(conn, delivery);
        if (!saved) {
            throw new org.wso2.dpdp.accelerator.event.notifications.common.exception.EventNotificationDataAccessException(
                    "Failed to queue poll delivery for subscription ["
                            + LogSanitizer.sanitize(subscription.getSubscriptionId())
                            + "] on event [" + LogSanitizer.sanitize(event.getEventId())
                            + "] — DAO returned false.");
        }
    }
}
