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

package org.wso2.dpdp.accelerator.event.notifications.dao;

import org.wso2.dpdp.accelerator.event.notifications.dao.model.PollDelivery;
import org.wso2.dpdp.accelerator.event.notifications.dao.model.SubscriptionDeliverySummary;
import org.wso2.dpdp.accelerator.event.notifications.dao.model.WebhookDelivery;
import org.wso2.dpdp.accelerator.event.notifications.dao.model.WebhookDeliveryAudit;
import org.wso2.dpdp.accelerator.event.notifications.dao.model.WebhookDeliveryDispatchContext;

import java.util.List;
import java.util.Optional;

public interface DeliveryDAO {

    boolean addWebhookDelivery(WebhookDelivery delivery);

    Optional<WebhookDelivery> getWebhookDeliveryById(String deliveryId, String orgId);

    /**
     * Returns the payload stored on the {@code EVENT} row keyed by {@code eventId}. Lives on
     * the delivery DAO rather than a dedicated event DAO because the worker that dispatches
     * webhook deliveries needs it and the dispatch loop is owned by the service layer.
     */
    Optional<String> getEventPayload(String eventId);

    /**
     * Returns the next batch of pending webhook deliveries joined with the matching
     * subscription callback URL, shared secret, and event payload so the dispatch worker can
     * issue a single HTTP POST without further DAO calls.
     */
    List<WebhookDeliveryDispatchContext> getPendingWebhookDispatchContexts(int limit);

    /**
     * Returns the next batch of stuck in-flight webhook deliveries joined with the same
     * subscription/event context used by {@link #getPendingWebhookDispatchContexts(int)}.
     */
    List<WebhookDeliveryDispatchContext> getStuckInFlightWebhookDispatchContexts(int limit);

    List<WebhookDelivery> getPendingWebhookDeliveries(int limit);

    List<WebhookDelivery> getStuckInFlightWebhookDeliveries(int limit);

    boolean updateWebhookDeliveryStatus(WebhookDelivery delivery);

    boolean addWebhookDeliveryAudit(WebhookDeliveryAudit audit);

    List<WebhookDeliveryAudit> getWebhookDeliveryAudits(String deliveryId, String orgId);

    boolean addPollDelivery(PollDelivery delivery);

    Optional<PollDelivery> getPollDeliveryById(String deliveryId, String orgId);

    List<PollDelivery> getPendingPollDeliveries(String orgId, String groupId, int limit);

    void updatePollDeliveryStatuses(String orgId, String groupId, List<String> ackEventIds, List<String> errEventIds);

    boolean claimWebhookDelivery(String deliveryId);

    boolean releaseWebhookDelivery(String deliveryId, int attemptCount, java.sql.Timestamp nextRetryAt);

    boolean claimPollDelivery(String deliveryId);

    boolean updatePollDeliveryStatus(String deliveryId, String status);

    boolean updatePollDeliveryStatus(String deliveryId, String expectedStatus, String newStatus);

    List<SubscriptionDeliverySummary> listSubscriptionDeliveries(String orgId, String subscriptionId, int limit, int offset, int[] totalOut);

    Optional<SubscriptionDeliverySummary> getSubscriptionDeliveryById(String orgId, String subscriptionId, String deliveryId);

    List<SubscriptionDeliverySummary> listOrgDeliveries(String orgId, String statusFilter, String subscriptionIdFilter, String purposesFilter, String search, int limit, int offset, int[] totalOut);

    Optional<SubscriptionDeliverySummary> getOrgDeliveryById(String orgId, String deliveryId);
}
