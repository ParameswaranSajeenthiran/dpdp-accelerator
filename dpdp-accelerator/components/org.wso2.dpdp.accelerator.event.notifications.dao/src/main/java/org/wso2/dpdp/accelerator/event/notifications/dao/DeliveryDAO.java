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

import org.wso2.dpdp.accelerator.common.util.DatabaseUtils;
import org.wso2.dpdp.accelerator.event.notifications.dao.model.PollDelivery;
import org.wso2.dpdp.accelerator.event.notifications.dao.model.SubscriptionDeliverySummary;
import org.wso2.dpdp.accelerator.event.notifications.dao.model.WebhookDelivery;
import org.wso2.dpdp.accelerator.event.notifications.dao.model.WebhookDeliveryAudit;
import org.wso2.dpdp.accelerator.event.notifications.dao.model.WebhookDeliveryDispatchContext;

import java.sql.Connection;
import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;

public interface DeliveryDAO {

    boolean addWebhookDelivery(Connection conn, WebhookDelivery delivery);

    default boolean addWebhookDelivery(WebhookDelivery delivery) {
        Connection conn = DatabaseUtils.getDBConnection();
        try {
            boolean result = addWebhookDelivery(conn, delivery);
            DatabaseUtils.commitTransaction(conn);
            return result;
        } catch (RuntimeException e) {
            DatabaseUtils.rollbackTransaction(conn);
            throw e;
        } finally {
            DatabaseUtils.closeConnection(conn);
        }
    }

    Optional<WebhookDelivery> getWebhookDeliveryById(String deliveryId, String orgId);

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

    List<WebhookDeliveryDispatchContext> getStuckInFlightWebhookDispatchContexts(int limit, Timestamp updatedBefore);

    boolean updateWebhookDeliveryStatus(WebhookDelivery delivery);

    boolean recordSuccessfulAttempt(WebhookDeliveryAudit audit, WebhookDelivery delivery);

    boolean recordRetryableFailure(WebhookDeliveryAudit audit, String deliveryId, int attemptCount, Timestamp nextRetryAt);

    boolean recordPermanentFailure(WebhookDeliveryAudit audit, WebhookDelivery delivery);

    boolean addWebhookDeliveryAudit(WebhookDeliveryAudit audit);

    List<WebhookDeliveryAudit> getWebhookDeliveryAudits(String deliveryId, String orgId);

    boolean addPollDelivery(PollDelivery delivery);

    boolean addPollDelivery(Connection connection, PollDelivery delivery);

    Optional<PollDelivery> getPollDeliveryById(String deliveryId, String orgId);

    List<PollDelivery> getPendingPollDeliveries(String orgId, String groupId, int limit);

    void updatePollDeliveryStatuses(String orgId, String groupId, List<String> ackEventIds, List<String> errEventIds);

    void updatePollDeliveryStatuses(Connection connection, String orgId, String groupId,
            List<String> ackEventIds, List<String> errEventIds);

    boolean claimWebhookDelivery(String deliveryId);

    boolean claimWebhookDelivery(Connection connection, String deliveryId);

    /**
     * Atomically reclaims a stuck {@code in_flight} delivery whose {@code UPDATED_AT} is
     * older than {@code updatedBefore}. Returns {@code true} only if the row was still
     * in_flight AND old enough — a concurrent active worker whose UPDATED_AT was just
     * refreshed will not be interrupted.
     */
    boolean claimStuckWebhookDelivery(String deliveryId, Timestamp updatedBefore);

    boolean claimStuckWebhookDelivery(Connection connection, String deliveryId, Timestamp updatedBefore);

    boolean releaseWebhookDelivery(String deliveryId, int attemptCount, Timestamp nextRetryAt);

    boolean releaseWebhookDelivery(Connection connection, String deliveryId, int attemptCount, Timestamp nextRetryAt);

    boolean claimPollDelivery(String deliveryId);

    boolean claimPollDelivery(Connection connection, String deliveryId);

    boolean updatePollDeliveryStatus(String deliveryId, String status);

    boolean updatePollDeliveryStatus(Connection connection, String deliveryId, String status);

    boolean updatePollDeliveryStatus(String deliveryId, String expectedStatus, String newStatus);

    boolean updatePollDeliveryStatus(Connection connection, String deliveryId, String expectedStatus,
            String newStatus);

    List<SubscriptionDeliverySummary> listSubscriptionDeliveries(String orgId, String subscriptionId, int limit, int offset, int[] totalOut);

    Optional<SubscriptionDeliverySummary> getSubscriptionDeliveryById(String orgId, String subscriptionId, String deliveryId);

    /**
     * Paginated list of event deliveries across the organisation.
     *
     * @param orgId organisation identifier.
     * @param statusFilter optional delivery status filter.
     * @param subscriptionIdFilter optional subscription filter.
     * @param purposesFilter optional comma-separated purposes filter.
     * @param search optional free-text search term.
     * @param limit page size.
     * @param offset pagination offset.
     * @param totalOut 1-element array to receive the total matching row count.
     * @return list of delivery summaries.
     */
    default List<SubscriptionDeliverySummary> listOrgDeliveries(String orgId, String statusFilter,
            String subscriptionIdFilter, String purposesFilter, String search, int limit, int offset, int[] totalOut) {
        return listOrgDeliveries(orgId, statusFilter, subscriptionIdFilter, null, purposesFilter, search, limit, offset, totalOut);
    }

    /**
     * Paginated list of event deliveries across the organisation with group filter support.
     *
     * @param orgId organisation identifier.
     * @param statusFilter optional delivery status filter.
     * @param subscriptionIdFilter optional subscription filter.
     * @param groupIdFilter optional consumer group ID filter.
     * @param purposesFilter optional comma-separated purposes filter.
     * @param search optional free-text search term.
     * @param limit page size.
     * @param offset pagination offset.
     * @param totalOut 1-element array to receive the total matching row count.
     * @return list of delivery summaries.
     */
    List<SubscriptionDeliverySummary> listOrgDeliveries(String orgId, String statusFilter,
            String subscriptionIdFilter, String groupIdFilter, String purposesFilter, String search, int limit, int offset, int[] totalOut);

    Optional<SubscriptionDeliverySummary> getOrgDeliveryById(String orgId, String deliveryId);

    /**
     * Paginated list of deliveries generated for a specific published event.
     *
     * @param orgId organisation identifier.
     * @param eventId event identifier.
     * @param limit page size.
     * @param offset pagination offset.
     * @param totalOut 1-element array to receive the total matching row count.
     * @return list of delivery summaries.
     */
    List<SubscriptionDeliverySummary> listEventDeliveries(String orgId, String eventId, int limit, int offset, int[] totalOut);
}
