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

package org.wso2.dpdp.accelerator.event.notifications.dao.queries;

import org.wso2.dpdp.accelerator.event.notifications.common.enums.DeliveryMode;

/**
 * Common ANSI SQL queries base provider for DPDP Event Notification Framework.
 */
public class EventNotificationCommonDBQueries {

    // TOPIC Queries
    public String getAddTopicQuery() {
        return "INSERT INTO TOPIC (TOPIC_ID, ORG_ID, NAME, DESCRIPTION, STATUS, INITIATED_BY) " +
                "VALUES (?, ?, ?, ?, ?, ?)";
    }

    public String getGetTopicByIdQuery() {
        return "SELECT TOPIC_ID, ORG_ID, NAME, DESCRIPTION, STATUS, INITIATED_BY " +
                "FROM TOPIC WHERE TOPIC_ID = ? AND ORG_ID = ?";
    }

    public String getGetTopicByOrgAndNameQuery() {
        return "SELECT TOPIC_ID, ORG_ID, NAME, DESCRIPTION, STATUS, INITIATED_BY " +
                "FROM TOPIC WHERE ORG_ID = ? AND LOWER(NAME) = LOWER(?) AND LOWER(STATUS) = 'active'";
    }

    public String getUpdateTopicStatusQuery() {
        return "UPDATE TOPIC SET STATUS = ? WHERE TOPIC_ID = ? AND ORG_ID = ?";
    }

    // SUBSCRIPTION Queries
    public String getAddSubscriptionQuery() {
        return "INSERT INTO SUBSCRIPTION (SUBSCRIPTION_ID, ORG_ID, GROUP_ID, TOPIC_ID, PURPOSE_FILTER_MODE, " +
                "PURPOSE_SET_HASH, DELIVERY_MODE, CALLBACK_URL, SHARED_SECRET, STATUS, CREATED_AT, UPDATED_AT) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)";
    }

    public String getAddSubscriptionPurposesQuery() {
        return "INSERT INTO SUBSCRIPTION_PURPOSE (SUBSCRIPTION_ID, PURPOSE_NAME) VALUES (?, ?)";
    }

    public String getGetSubscriptionByIdQuery() {
        return "SELECT SUBSCRIPTION_ID, ORG_ID, GROUP_ID, TOPIC_ID, PURPOSE_FILTER_MODE, PURPOSE_SET_HASH, DELIVERY_MODE, "
                +
                "CALLBACK_URL, SHARED_SECRET, STATUS, CREATED_AT, UPDATED_AT " +
                "FROM SUBSCRIPTION WHERE SUBSCRIPTION_ID = ? AND ORG_ID = ?";
    }

    public String getLockActiveSubscriptionsQuery() {
        return "SELECT SUBSCRIPTION_ID, ORG_ID, GROUP_ID, TOPIC_ID, PURPOSE_FILTER_MODE, PURPOSE_SET_HASH, DELIVERY_MODE, "
                +
                "CALLBACK_URL, SHARED_SECRET, STATUS, CREATED_AT, UPDATED_AT " +
                "FROM SUBSCRIPTION WHERE ORG_ID = ? AND GROUP_ID = ? AND TOPIC_ID = ? " +
                "AND STATUS IN ('active', 'pending', 'stale') FOR UPDATE";
    }

    public String getLockTopicForSubscriptionQuery() {
        return "SELECT TOPIC_ID FROM TOPIC WHERE TOPIC_ID = ? AND ORG_ID = ? FOR UPDATE";
    }

    public String getGetTopicStatusForSubscriptionQuery() {
        return "SELECT STATUS FROM TOPIC WHERE TOPIC_ID = ? AND ORG_ID = ?";
    }

    public String getUpdateSubscriptionStatusQuery() {
        return "UPDATE SUBSCRIPTION SET STATUS = ?, UPDATED_AT = CURRENT_TIMESTAMP WHERE SUBSCRIPTION_ID = ? AND ORG_ID = ?";
    }

    public String getUpdateSubscriptionStatusGuardedQuery() {
        return "UPDATE SUBSCRIPTION SET STATUS = ?, UPDATED_AT = CURRENT_TIMESTAMP WHERE SUBSCRIPTION_ID = ? AND ORG_ID = ? AND STATUS = ?";
    }

    /**
     * Atomically soft-deletes a subscription if it has no pending or in-flight
     * deliveries.
     * <p>
     * <b>Parameter binding order (caller must bind all five in sequence):</b>
     * <ol>
     * <li>SUBSCRIPTION_ID (main WHERE)</li>
     * <li>ORG_ID</li>
     * <li>STATUS (expected current status guard)</li>
     * <li>SUBSCRIPTION_ID (NOT EXISTS WEBHOOK_DELIVERY sub-query)</li>
     * <li>SUBSCRIPTION_ID (NOT EXISTS POLL_DELIVERY sub-query)</li>
     * </ol>
     */
    public String getDeleteSubscriptionAtomicQuery() {
        return "UPDATE SUBSCRIPTION SET STATUS = 'deleted', UPDATED_AT = CURRENT_TIMESTAMP " +
                "WHERE SUBSCRIPTION_ID = ? AND ORG_ID = ? AND STATUS = ? " +
                "AND NOT EXISTS (SELECT 1 FROM WEBHOOK_DELIVERY WHERE SUBSCRIPTION_ID = ? AND STATUS IN ('pending', 'in_flight')) "
                +
                "AND NOT EXISTS (SELECT 1 FROM POLL_DELIVERY WHERE SUBSCRIPTION_ID = ? AND STATUS = 'pending')";
    }

    public String getGetSubscriptionsByOrgAndTopicQuery() {
        return "SELECT SUBSCRIPTION_ID, ORG_ID, GROUP_ID, TOPIC_ID, PURPOSE_FILTER_MODE, PURPOSE_SET_HASH, DELIVERY_MODE, "
                +
                "CALLBACK_URL, SHARED_SECRET, STATUS, CREATED_AT, UPDATED_AT " +
                "FROM SUBSCRIPTION WHERE ORG_ID = ? AND TOPIC_ID = ? AND STATUS = ?";
    }

    /**
     * Returns all subscriptions for an org/topic that are in a live state
     * (active, pending, stale). Used by duplicate-check logic so that a second
     * overlapping subscription cannot be created while the first is still pending
     * webhook verification or has a stale verification.
     */
    public String getGetLiveSubscriptionsByOrgAndTopicQuery() {
        return "SELECT SUBSCRIPTION_ID, ORG_ID, GROUP_ID, TOPIC_ID, PURPOSE_FILTER_MODE, PURPOSE_SET_HASH, DELIVERY_MODE, "
                +
                "CALLBACK_URL, SHARED_SECRET, STATUS, CREATED_AT, UPDATED_AT " +
                "FROM SUBSCRIPTION WHERE ORG_ID = ? AND TOPIC_ID = ? " +
                "AND STATUS IN ('active', 'pending', 'stale')";
    }

    public String getCountActiveSubscriptionsForTopicQuery() {
        return "SELECT COUNT(*) FROM SUBSCRIPTION WHERE ORG_ID = ? AND TOPIC_ID = ? " +
                "AND STATUS IN ('active', 'pending', 'stale')";
    }

    public String getGetSubscriptionPurposesQuery() {
        return "SELECT sp.PURPOSE_NAME FROM SUBSCRIPTION_PURPOSE sp " +
                "JOIN SUBSCRIPTION s ON sp.SUBSCRIPTION_ID = s.SUBSCRIPTION_ID " +
                "WHERE sp.SUBSCRIPTION_ID = ? AND s.ORG_ID = ?";
    }

    public String getGetPurposesBySubscriptionIdWithoutOrgIdQuery() {
        return "SELECT PURPOSE_NAME FROM SUBSCRIPTION_PURPOSE WHERE SUBSCRIPTION_ID = ?";
    }

    public String getGetSubscriptionPurposesByIdsTemplate() {
        return "SELECT SUBSCRIPTION_ID, PURPOSE_NAME FROM SUBSCRIPTION_PURPOSE WHERE SUBSCRIPTION_ID IN (%s)";
    }

    public String getHasPendingOrInFlightDeliveriesForSubscriptionQuery() {
        return "SELECT 1 FROM WEBHOOK_DELIVERY w JOIN SUBSCRIPTION s ON w.SUBSCRIPTION_ID = s.SUBSCRIPTION_ID WHERE w.SUBSCRIPTION_ID = ? AND s.ORG_ID = ? AND w.STATUS IN (?, ?) "
                +
                "UNION ALL " +
                "SELECT 1 FROM POLL_DELIVERY p JOIN SUBSCRIPTION s ON p.SUBSCRIPTION_ID = s.SUBSCRIPTION_ID WHERE p.SUBSCRIPTION_ID = ? AND s.ORG_ID = ? AND p.STATUS = ?";
    }

    // WEBHOOK_DELIVERY Queries
    public String getAddWebhookDeliveryQuery() {
        return "INSERT INTO WEBHOOK_DELIVERY (DELIVERY_ID, SUBSCRIPTION_ID, EVENT_ID, STATUS, ATTEMPT_COUNT, " +
                "NEXT_RETRY_AT, CREATED_AT, UPDATED_AT, DELIVERED_AT) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";
    }

    public String getGetWebhookDeliveryByIdAndOrgQuery() {
        return "SELECT d.DELIVERY_ID, d.SUBSCRIPTION_ID, d.EVENT_ID, d.STATUS, d.ATTEMPT_COUNT, d.NEXT_RETRY_AT, " +
                "d.CREATED_AT, d.UPDATED_AT, d.DELIVERED_AT " +
                "FROM WEBHOOK_DELIVERY d JOIN SUBSCRIPTION s ON d.SUBSCRIPTION_ID = s.SUBSCRIPTION_ID " +
                "WHERE d.DELIVERY_ID = ? AND s.ORG_ID = ?";
    }

    public String getUpdateWebhookDeliveryStatusQuery() {
        return "UPDATE WEBHOOK_DELIVERY SET STATUS = ?, ATTEMPT_COUNT = ?, NEXT_RETRY_AT = ?, DELIVERED_AT = ?, " +
                "UPDATED_AT = CURRENT_TIMESTAMP WHERE DELIVERY_ID = ? AND STATUS = 'in_flight'";
    }

    public String getGetPendingWebhookDeliveriesQuery() {
        return "SELECT DELIVERY_ID, SUBSCRIPTION_ID, EVENT_ID, STATUS, ATTEMPT_COUNT, NEXT_RETRY_AT, CREATED_AT, UPDATED_AT, DELIVERED_AT "
                +
                "FROM WEBHOOK_DELIVERY WHERE STATUS = 'pending' AND (NEXT_RETRY_AT IS NULL OR NEXT_RETRY_AT <= CURRENT_TIMESTAMP) "
                +
                "ORDER BY CREATED_AT ASC LIMIT ?";
    }

    /**
     * Stuck in-flight rows: a worker claimed the row but never released it (e.g.
     * JVM crashed
     * after {@code claimWebhookDelivery} but before releaseWebhookDelivery/update).
     * We treat
     * any {@code in_flight} row whose {@code UPDATED_AT} is older than {@code ?}
     * seconds as
     * available for reclaim.
     *
     * The single {@code ?} placeholder is interpreted as "seconds before now".
     */
    public String getGetStuckInFlightWebhookDeliveriesQuery() {
        return "SELECT DELIVERY_ID, SUBSCRIPTION_ID, EVENT_ID, STATUS, ATTEMPT_COUNT, NEXT_RETRY_AT, CREATED_AT, UPDATED_AT, DELIVERED_AT "
                +
                "FROM WEBHOOK_DELIVERY WHERE STATUS = 'in_flight' AND UPDATED_AT <= ? "
                +
                "ORDER BY UPDATED_AT ASC LIMIT ?";
    }

    public String getReleaseWebhookDeliveryQuery() {
        return "UPDATE WEBHOOK_DELIVERY SET STATUS = 'pending', ATTEMPT_COUNT = ?, NEXT_RETRY_AT = ?, " +
                "UPDATED_AT = CURRENT_TIMESTAMP WHERE DELIVERY_ID = ? AND STATUS = 'in_flight'";
    }

    // EVENT Queries
    public String getAddEventQuery() {
        return "INSERT INTO EVENT (EVENT_ID, ORG_ID, GROUP_ID, TOPIC_ID, PAYLOAD, CREATED_AT) " +
                "VALUES (?, ?, ?, ?, ?, ?)";
    }

    public String getGetEventByIdQuery() {
        return "SELECT EVENT_ID, ORG_ID, GROUP_ID, TOPIC_ID, PAYLOAD, CREATED_AT FROM EVENT WHERE EVENT_ID = ? AND ORG_ID = ?";
    }

    public String getAddEventPurposeQuery() {
        return "INSERT INTO EVENT_PURPOSE (EVENT_ID, PURPOSE_NAME) VALUES (?, ?)";
    }

    public String getGetEventPurposesQuery() {
        return "SELECT PURPOSE_NAME FROM EVENT_PURPOSE WHERE EVENT_ID = ?";
    }

    public String getHasActiveEventsForTopicQuery() {
        return "SELECT 1 FROM EVENT WHERE TOPIC_ID = ? LIMIT 1";
    }

    public String getGetEventPayloadQuery() {
        return "SELECT PAYLOAD FROM EVENT WHERE EVENT_ID = ?";
    }

    /**
     * Base SELECT for paginated event search/list. The service/builder appends an
     * optional
     * search WHERE clause and the pagination clause; no dialect override is needed.
     */
    public String getListEventsBaseQuery() {
        return "SELECT e.EVENT_ID, e.ORG_ID, e.GROUP_ID, e.TOPIC_ID, e.PAYLOAD, e.CREATED_AT " +
                "FROM EVENT e WHERE e.ORG_ID = ?";
    }

    public String getCountEventsBaseQuery() {
        return "SELECT COUNT(*) FROM EVENT e WHERE e.ORG_ID = ?";
    }

    public String getGetPurposesByEventIdsTemplate() {
        return "SELECT EVENT_ID, PURPOSE_NAME FROM EVENT_PURPOSE WHERE EVENT_ID IN (%s)";
    }

    // The dispatch context now pulls topic metadata (TOPIC_ID, TOPIC_NAME) so the
    // worker
    // can stamp every webhook payload with the topic that fired without an extra
    // DAO call.
    // INNER JOIN on TOPIC is safe because EVENT.TOPIC_ID is NOT NULL (enforced at
    // insert);
    // rows whose EVENT was deleted are still returned with PAYLOAD=NULL, which the
    // worker
    // already filters out via isDeliverable(...).
    private static final String DISPATCH_SELECT = "SELECT d.DELIVERY_ID, d.SUBSCRIPTION_ID, d.EVENT_ID, d.STATUS, " +
            "d.ATTEMPT_COUNT, d.NEXT_RETRY_AT, d.CREATED_AT, d.UPDATED_AT, d.DELIVERED_AT, " +
            "s.ORG_ID, s.CALLBACK_URL, s.SHARED_SECRET, e.PAYLOAD, e.TOPIC_ID AS TOPIC_ID, " +
            "t.NAME AS TOPIC_NAME ";

    public String getGetPendingWebhookDispatchContextsQuery() {
        return DISPATCH_SELECT +
                "FROM WEBHOOK_DELIVERY d " +
                "JOIN SUBSCRIPTION s ON d.SUBSCRIPTION_ID = s.SUBSCRIPTION_ID " +
                "LEFT JOIN EVENT e ON d.EVENT_ID = e.EVENT_ID " +
                "JOIN TOPIC t ON e.TOPIC_ID = t.TOPIC_ID " +
                "WHERE d.STATUS = 'pending' AND s.STATUS = 'active' AND (d.NEXT_RETRY_AT IS NULL OR d.NEXT_RETRY_AT <= CURRENT_TIMESTAMP) "
                +
                "ORDER BY d.CREATED_AT ASC LIMIT ?";
    }

    public String getGetStuckInFlightWebhookDispatchContextsQuery() {
        return DISPATCH_SELECT +
                "FROM WEBHOOK_DELIVERY d " +
                "JOIN SUBSCRIPTION s ON d.SUBSCRIPTION_ID = s.SUBSCRIPTION_ID " +
                "LEFT JOIN EVENT e ON d.EVENT_ID = e.EVENT_ID " +
                "JOIN TOPIC t ON e.TOPIC_ID = t.TOPIC_ID " +
                "WHERE d.STATUS = 'in_flight' AND s.STATUS = 'active' AND d.UPDATED_AT <= ? " +
                "ORDER BY d.UPDATED_AT ASC LIMIT ?";
    }

    // WEBHOOK_DELIVERY_ACK Queries
    public String getAddWebhookDeliveryAckQuery() {
        return "INSERT INTO WEBHOOK_DELIVERY_ACK (ACK_ID, DELIVERY_ID, COMPLETED_AT, COMPLETION_STATUS, COMPLETION_EVIDENCE) "
                +
                "VALUES (?, ?, ?, ?, ?)";
    }

    public String getGetWebhookDeliveryAckByDeliveryIdQuery() {
        return "SELECT ACK_ID, DELIVERY_ID, COMPLETED_AT, COMPLETION_STATUS, COMPLETION_EVIDENCE " +
                "FROM WEBHOOK_DELIVERY_ACK WHERE DELIVERY_ID = ?";
    }

    // WEBHOOK_DELIVERY_AUDIT Queries
    public String getAddWebhookDeliveryAuditQuery() {
        return "INSERT INTO WEBHOOK_DELIVERY_AUDIT (AUDIT_ID, EVENT_ID, DELIVERY_ID, ORG_ID, RESPONSE_CODE, CREATED_AT, ATTEMPT_AT) "
                +
                "VALUES (?, ?, ?, ?, ?, ?, ?)";
    }

    public String getGetWebhookDeliveryAuditsByDeliveryIdQuery() {
        return "SELECT AUDIT_ID, EVENT_ID, DELIVERY_ID, ORG_ID, RESPONSE_CODE, CREATED_AT, ATTEMPT_AT " +
                "FROM WEBHOOK_DELIVERY_AUDIT WHERE DELIVERY_ID = ? AND ORG_ID = ? ORDER BY ATTEMPT_AT ASC";
    }

    public String getGetWebhookDeliveryAuditsByDeliveryIdWithoutOrgIdQuery() {
        return "SELECT AUDIT_ID, EVENT_ID, DELIVERY_ID, ORG_ID, RESPONSE_CODE, CREATED_AT, ATTEMPT_AT FROM WEBHOOK_DELIVERY_AUDIT WHERE DELIVERY_ID = ? ORDER BY ATTEMPT_AT ASC";
    }

    // POLL_DELIVERY Queries
    public String getAddPollDeliveryQuery() {
        return "INSERT INTO POLL_DELIVERY (DELIVERY_ID, SUBSCRIPTION_ID, EVENT_ID, STATUS, CREATED_AT, COMPLETED_AT) " +
                "VALUES (?, ?, ?, ?, ?, ?)";
    }

    public String getGetPollDeliveryByIdAndOrgQuery() {
        return "SELECT p.DELIVERY_ID, p.SUBSCRIPTION_ID, p.EVENT_ID, p.STATUS, p.CREATED_AT, p.COMPLETED_AT " +
                "FROM POLL_DELIVERY p JOIN SUBSCRIPTION s ON p.SUBSCRIPTION_ID = s.SUBSCRIPTION_ID " +
                "WHERE p.DELIVERY_ID = ? AND s.ORG_ID = ?";
    }

    public String getGetPendingPollDeliveriesQuery() {
        return "SELECT p.DELIVERY_ID, p.SUBSCRIPTION_ID, p.EVENT_ID, p.STATUS, p.CREATED_AT, p.COMPLETED_AT " +
                "FROM POLL_DELIVERY p JOIN SUBSCRIPTION s ON p.SUBSCRIPTION_ID = s.SUBSCRIPTION_ID " +
                "WHERE s.ORG_ID = ? AND s.GROUP_ID = ? AND s.DELIVERY_MODE = 'poll' AND s.STATUS = 'active' AND p.STATUS = 'pending' "
                +
                "ORDER BY p.CREATED_AT ASC LIMIT ?";
    }

    public String getGetPendingSubscriptionsForRecoveryQuery() {
        return "SELECT SUBSCRIPTION_ID, ORG_ID, GROUP_ID, TOPIC_ID, PURPOSE_FILTER_MODE, PURPOSE_SET_HASH, DELIVERY_MODE, "
                +
                "CALLBACK_URL, SHARED_SECRET, STATUS, CREATED_AT, UPDATED_AT " +
                "FROM SUBSCRIPTION WHERE STATUS = 'pending' AND DELIVERY_MODE = 'webhook' AND UPDATED_AT <= ?";
    }

    /**
     * Updates a poll delivery status directly by delivery ID.
     * <p>
     * <b>For internal background worker use only.</b> Delivery IDs are UUIDs
     * generated
     * internally and are never user-supplied. No ORG_ID scoping is applied at the
     * query
     * level; tenant isolation is enforced upstream when the delivery context is
     * fetched.
     */
    public String getUpdatePollDeliveryStatusQuery() {
        return "UPDATE POLL_DELIVERY SET STATUS = ?, COMPLETED_AT = ? WHERE DELIVERY_ID = ?";
    }

    public String getUpdatePollDeliveryStatusByEventAndGroupQuery() {
        return "UPDATE POLL_DELIVERY SET STATUS = ?, COMPLETED_AT = CURRENT_TIMESTAMP " +
                "WHERE EVENT_ID = ? AND SUBSCRIPTION_ID IN (" +
                "SELECT SUBSCRIPTION_ID FROM SUBSCRIPTION WHERE ORG_ID = ? AND GROUP_ID = ?)";
    }

    /**
     * Atomically claims a poll delivery row by flipping STATUS from 'pending' to
     * 'acknowledged'.
     * <p>
     * <b>For internal background worker use only.</b> No ORG_ID scoping — see
     * {@link #getUpdatePollDeliveryStatusQuery()} for rationale.
     */
    public String getClaimPollDeliveryQuery() {
        return "UPDATE POLL_DELIVERY SET STATUS = 'acknowledged', COMPLETED_AT = CURRENT_TIMESTAMP " +
                "WHERE DELIVERY_ID = ? AND STATUS = 'pending'";
    }

    /**
     * Atomically claims a pending webhook delivery row by flipping STATUS from
     * {@code 'pending'} to {@code 'in_flight'}.
     * <p>
     * <b>For internal background worker use only.</b> No ORG_ID scoping — the
     * background
     * delivery worker operates across all tenants by design.
     * <p>
     * The {@code STATUS = 'pending'} guard ensures exactly-one-worker semantics: a
     * second
     * concurrent worker calling this method for the same row will find STATUS
     * already
     * {@code 'in_flight'} and the UPDATE will affect 0 rows, so it correctly
     * returns false.
     */
    public String getClaimWebhookDeliveryQuery() {
        return "UPDATE WEBHOOK_DELIVERY SET STATUS = 'in_flight', UPDATED_AT = CURRENT_TIMESTAMP " +
                "WHERE DELIVERY_ID = ? AND STATUS = 'pending'";
    }

    /**
     * Atomically reclaims a <em>stuck</em> in-flight webhook delivery row whose
     * {@code UPDATED_AT} is older than the supplied cutoff timestamp.
     * <p>
     * A stuck row is one whose worker crashed (or was interrupted) after calling
     * {@link #getClaimWebhookDeliveryQuery()} but before releasing the row. This
     * guard
     * uses {@code UPDATED_AT <= ?} rather than {@code STATUS = 'in_flight'} alone
     * so that
     * a row whose timestamp was just refreshed by an <em>active</em> worker is
     * never
     * double-claimed.
     */
    public String getClaimStuckWebhookDeliveryQuery() {
        return "UPDATE WEBHOOK_DELIVERY SET STATUS = 'in_flight', UPDATED_AT = CURRENT_TIMESTAMP " +
                "WHERE DELIVERY_ID = ? AND STATUS = 'in_flight' AND UPDATED_AT <= ?";
    }

    public String getUpdatePollDeliveryStatusGuardedQuery() {
        return "UPDATE POLL_DELIVERY SET STATUS = ?, COMPLETED_AT = ? WHERE DELIVERY_ID = ? AND STATUS = ?";
    }

    // ORG_DELIVERY Queries (UNION BASE)
    public String getGetOrgDeliveriesUnionBaseQuery() {
        return "SELECT d.DELIVERY_ID, d.EVENT_ID, d.SUBSCRIPTION_ID, e.GROUP_ID AS GROUP_ID, t.NAME AS TOPIC_NAME, d.STATUS AS CURRENT_STATUS, '"
                + DeliveryMode.WEBHOOK.getValue() + "' AS DELIVERY_MODE, "
                + "e.CREATED_AT AS OCCURRED_AT, d.CREATED_AT AS DELIVERY_CREATED_AT, e.PAYLOAD AS PAYLOAD " +
                "FROM WEBHOOK_DELIVERY d " +
                "JOIN EVENT e ON d.EVENT_ID = e.EVENT_ID " +
                "JOIN TOPIC t ON e.TOPIC_ID = t.TOPIC_ID " +
                "JOIN SUBSCRIPTION s ON d.SUBSCRIPTION_ID = s.SUBSCRIPTION_ID " +
                "WHERE s.ORG_ID = ? " +
                "UNION ALL " +
                "SELECT p.DELIVERY_ID, p.EVENT_ID, p.SUBSCRIPTION_ID, e.GROUP_ID AS GROUP_ID, t.NAME AS TOPIC_NAME, p.STATUS AS CURRENT_STATUS, '"
                + DeliveryMode.POLL.getValue() + "' AS DELIVERY_MODE, "
                + "e.CREATED_AT AS OCCURRED_AT, p.CREATED_AT AS DELIVERY_CREATED_AT, e.PAYLOAD AS PAYLOAD " +
                "FROM POLL_DELIVERY p " +
                "JOIN EVENT e ON p.EVENT_ID = e.EVENT_ID " +
                "JOIN TOPIC t ON e.TOPIC_ID = t.TOPIC_ID " +
                "JOIN SUBSCRIPTION s ON p.SUBSCRIPTION_ID = s.SUBSCRIPTION_ID " +
                "WHERE s.ORG_ID = ?";
    }

    public String getGetSubscriptionDeliveriesUnionBaseQuery() {
        return "SELECT d.DELIVERY_ID, d.EVENT_ID, d.SUBSCRIPTION_ID, e.GROUP_ID AS GROUP_ID, t.NAME AS TOPIC_NAME, d.STATUS AS CURRENT_STATUS, '"
                + DeliveryMode.WEBHOOK.getValue() + "' AS DELIVERY_MODE, "
                + "e.CREATED_AT AS OCCURRED_AT, d.CREATED_AT AS DELIVERY_CREATED_AT, e.PAYLOAD "
                + "FROM WEBHOOK_DELIVERY d "
                + "JOIN EVENT e ON d.EVENT_ID = e.EVENT_ID "
                + "JOIN TOPIC t ON e.TOPIC_ID = t.TOPIC_ID "
                + "JOIN SUBSCRIPTION s ON d.SUBSCRIPTION_ID = s.SUBSCRIPTION_ID "
                + "WHERE d.SUBSCRIPTION_ID = ? AND s.ORG_ID = ? "
                + "UNION ALL "
                + "SELECT p.DELIVERY_ID, p.EVENT_ID, p.SUBSCRIPTION_ID, e.GROUP_ID AS GROUP_ID, t.NAME AS TOPIC_NAME, p.STATUS AS CURRENT_STATUS, '"
                + DeliveryMode.POLL.getValue() + "' AS DELIVERY_MODE, "
                + "e.CREATED_AT AS OCCURRED_AT, p.CREATED_AT AS DELIVERY_CREATED_AT, e.PAYLOAD "
                + "FROM POLL_DELIVERY p "
                + "JOIN EVENT e ON p.EVENT_ID = e.EVENT_ID "
                + "JOIN TOPIC t ON e.TOPIC_ID = t.TOPIC_ID "
                + "JOIN SUBSCRIPTION s ON p.SUBSCRIPTION_ID = s.SUBSCRIPTION_ID "
                + "WHERE p.SUBSCRIPTION_ID = ? AND s.ORG_ID = ?";
    }

    public String getGetSubscriptionDeliveryByIdQuery() {
        return "SELECT d.DELIVERY_ID, d.EVENT_ID, d.SUBSCRIPTION_ID, e.GROUP_ID AS GROUP_ID, t.NAME AS TOPIC_NAME, d.STATUS AS CURRENT_STATUS, '"
                + DeliveryMode.WEBHOOK.getValue() + "' AS DELIVERY_MODE, "
                + "e.CREATED_AT AS OCCURRED_AT, d.CREATED_AT AS DELIVERY_CREATED_AT, e.PAYLOAD AS PAYLOAD "
                + "FROM WEBHOOK_DELIVERY d JOIN EVENT e ON d.EVENT_ID = e.EVENT_ID JOIN TOPIC t ON e.TOPIC_ID = t.TOPIC_ID JOIN SUBSCRIPTION s ON d.SUBSCRIPTION_ID = s.SUBSCRIPTION_ID WHERE d.SUBSCRIPTION_ID = ? AND d.DELIVERY_ID = ? AND s.ORG_ID = ? "
                + "UNION ALL "
                + "SELECT p.DELIVERY_ID, p.EVENT_ID, p.SUBSCRIPTION_ID, e.GROUP_ID AS GROUP_ID, t.NAME AS TOPIC_NAME, p.STATUS AS CURRENT_STATUS, '"
                + DeliveryMode.POLL.getValue() + "' AS DELIVERY_MODE, "
                + "e.CREATED_AT AS OCCURRED_AT, p.CREATED_AT AS DELIVERY_CREATED_AT, e.PAYLOAD AS PAYLOAD "
                + "FROM POLL_DELIVERY p JOIN EVENT e ON p.EVENT_ID = e.EVENT_ID JOIN TOPIC t ON e.TOPIC_ID = t.TOPIC_ID JOIN SUBSCRIPTION s ON p.SUBSCRIPTION_ID = s.SUBSCRIPTION_ID WHERE p.SUBSCRIPTION_ID = ? AND p.DELIVERY_ID = ? AND s.ORG_ID = ?";
    }

    public String getGetOrgDeliveryByIdQuery() {
        return "SELECT * FROM (" + getGetOrgDeliveriesUnionBaseQuery() + ") AS u WHERE DELIVERY_ID = ?";
    }

    public String getPaginationClause(String orderByColumn) {
        if (orderByColumn != null && !orderByColumn.matches("^[a-zA-Z0-9_.,\\s]+$")) {
            throw new IllegalArgumentException("Invalid sort column: " + orderByColumn);
        }
        return " ORDER BY " + orderByColumn + " LIMIT ? OFFSET ?";
    }
}
