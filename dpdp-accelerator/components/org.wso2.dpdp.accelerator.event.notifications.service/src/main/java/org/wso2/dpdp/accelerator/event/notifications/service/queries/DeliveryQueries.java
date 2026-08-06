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

package org.wso2.dpdp.accelerator.event.notifications.service.queries;

public class DeliveryQueries {

    private DeliveryQueries() {
    }

    // WEBHOOK_DELIVERY Queries
    public static final String ADD_WEBHOOK_DELIVERY =
            "INSERT INTO WEBHOOK_DELIVERY (DELIVERY_ID, SUBSCRIPTION_ID, EVENT_ID, STATUS, ATTEMPT_COUNT, " +
            "NEXT_RETRY_AT, CREATED_AT, UPDATED_AT, DELIVERED_AT) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";

    public static final String GET_WEBHOOK_DELIVERY_BY_ID =
            "SELECT DELIVERY_ID, SUBSCRIPTION_ID, EVENT_ID, STATUS, ATTEMPT_COUNT, NEXT_RETRY_AT, CREATED_AT, UPDATED_AT, DELIVERED_AT " +
            "FROM WEBHOOK_DELIVERY WHERE DELIVERY_ID = ?";

    public static final String UPDATE_WEBHOOK_DELIVERY_STATUS =
            "UPDATE WEBHOOK_DELIVERY SET STATUS = ?, ATTEMPT_COUNT = ?, NEXT_RETRY_AT = ?, DELIVERED_AT = ?, " +
            "UPDATED_AT = CURRENT_TIMESTAMP WHERE DELIVERY_ID = ?";

    public static final String GET_PENDING_WEBHOOK_DELIVERIES =
            "SELECT DELIVERY_ID, SUBSCRIPTION_ID, EVENT_ID, STATUS, ATTEMPT_COUNT, NEXT_RETRY_AT, CREATED_AT, UPDATED_AT, DELIVERED_AT " +
            "FROM WEBHOOK_DELIVERY WHERE STATUS = 'pending' AND (NEXT_RETRY_AT IS NULL OR NEXT_RETRY_AT <= CURRENT_TIMESTAMP) " +
            "ORDER BY CREATED_AT ASC LIMIT ?";

    // WEBHOOK_DELIVERY_ACK Queries
    public static final String ADD_WEBHOOK_DELIVERY_ACK =
            "INSERT INTO WEBHOOK_DELIVERY_ACK (ACK_ID, DELIVERY_ID, COMPLETED_AT, COMPLETION_STATUS, COMPLETION_EVIDENCE) " +
            "VALUES (?, ?, ?, ?, ?)";

    public static final String GET_WEBHOOK_DELIVERY_ACK_BY_DELIVERY_ID =
            "SELECT ACK_ID, DELIVERY_ID, COMPLETED_AT, COMPLETION_STATUS, COMPLETION_EVIDENCE " +
            "FROM WEBHOOK_DELIVERY_ACK WHERE DELIVERY_ID = ?";

    // WEBHOOK_DELIVERY_AUDIT Queries
    public static final String ADD_WEBHOOK_DELIVERY_AUDIT =
            "INSERT INTO WEBHOOK_DELIVERY_AUDIT (AUDIT_ID, EVENT_ID, DELIVERY_ID, ORG_ID, RESPONSE_CODE, CREATED_AT, ATTEMPT_AT) " +
            "VALUES (?, ?, ?, ?, ?, ?, ?)";

    public static final String GET_WEBHOOK_DELIVERY_AUDITS_BY_DELIVERY_ID =
            "SELECT AUDIT_ID, EVENT_ID, DELIVERY_ID, ORG_ID, RESPONSE_CODE, CREATED_AT, ATTEMPT_AT " +
            "FROM WEBHOOK_DELIVERY_AUDIT WHERE DELIVERY_ID = ? ORDER BY ATTEMPT_AT ASC";

    // POLL_DELIVERY Queries
    public static final String ADD_POLL_DELIVERY =
            "INSERT INTO POLL_DELIVERY (DELIVERY_ID, SUBSCRIPTION_ID, EVENT_ID, STATUS, CREATED_AT, COMPLETED_AT) " +
            "VALUES (?, ?, ?, ?, ?, ?)";

    public static final String GET_PENDING_POLL_DELIVERIES =
            "SELECT DELIVERY_ID, SUBSCRIPTION_ID, EVENT_ID, STATUS, CREATED_AT, COMPLETED_AT " +
            "FROM POLL_DELIVERY WHERE SUBSCRIPTION_ID = ? AND STATUS = 'pending'";

    public static final String GET_POLL_DELIVERY_BY_ID =
            "SELECT DELIVERY_ID, SUBSCRIPTION_ID, EVENT_ID, STATUS, CREATED_AT, COMPLETED_AT " +
            "FROM POLL_DELIVERY WHERE DELIVERY_ID = ?";

    public static final String UPDATE_POLL_DELIVERY_STATUS =
            "UPDATE POLL_DELIVERY SET STATUS = ?, COMPLETED_AT = ? WHERE DELIVERY_ID = ?";

    // ORG_DELIVERY Queries
    public static final String GET_ORG_DELIVERIES_UNION_BASE =
            "SELECT d.DELIVERY_ID, d.EVENT_ID, d.SUBSCRIPTION_ID, t.NAME AS TOPIC_NAME, d.STATUS AS CURRENT_STATUS, 'WEBHOOK' AS DELIVERY_MODE, " +
            "e.CREATED_AT AS OCCURRED_AT, d.CREATED_AT AS DELIVERY_CREATED_AT, e.PAYLOAD AS PAYLOAD " +
            "FROM WEBHOOK_DELIVERY d " +
            "JOIN EVENT e ON d.EVENT_ID = e.EVENT_ID " +
            "JOIN TOPIC t ON e.TOPIC_ID = t.TOPIC_ID " +
            "JOIN SUBSCRIPTION s ON d.SUBSCRIPTION_ID = s.SUBSCRIPTION_ID " +
            "WHERE s.ORG_ID = ? " +
            "UNION ALL " +
            "SELECT p.DELIVERY_ID, p.EVENT_ID, p.SUBSCRIPTION_ID, t.NAME AS TOPIC_NAME, p.STATUS AS CURRENT_STATUS, 'POLL' AS DELIVERY_MODE, " +
            "e.CREATED_AT AS OCCURRED_AT, p.CREATED_AT AS DELIVERY_CREATED_AT, e.PAYLOAD AS PAYLOAD " +
            "FROM POLL_DELIVERY p " +
            "JOIN EVENT e ON p.EVENT_ID = e.EVENT_ID " +
            "JOIN TOPIC t ON e.TOPIC_ID = t.TOPIC_ID " +
            "JOIN SUBSCRIPTION s ON p.SUBSCRIPTION_ID = s.SUBSCRIPTION_ID " +
            "WHERE s.ORG_ID = ?";
}
