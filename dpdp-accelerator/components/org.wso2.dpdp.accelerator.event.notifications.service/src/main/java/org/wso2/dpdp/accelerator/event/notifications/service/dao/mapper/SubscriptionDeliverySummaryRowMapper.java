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

package org.wso2.dpdp.accelerator.event.notifications.service.dao.mapper;

import org.wso2.dpdp.accelerator.event.notifications.service.dao.model.SubscriptionDeliverySummary;

import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * Maps JDBC {@link ResultSet} rows to {@link SubscriptionDeliverySummary} domain models.
 * Uses a single fixed-column projection; payload is read but tolerated when missing.
 */
public final class SubscriptionDeliverySummaryRowMapper {

    private SubscriptionDeliverySummaryRowMapper() {
    }

    public static SubscriptionDeliverySummary map(ResultSet rs) throws SQLException {
        String deliveryId = rs.getString("DELIVERY_ID");
        String eventId = rs.getString("EVENT_ID");
        String subscriptionId = rs.getString("SUBSCRIPTION_ID");
        String topicName = rs.getString("TOPIC_NAME");
        String currentStatus = rs.getString("CURRENT_STATUS");
        String deliveryMode = rs.getString("DELIVERY_MODE");
        java.sql.Timestamp occurredAt = rs.getTimestamp("OCCURRED_AT");
        java.sql.Timestamp createdAt = rs.getTimestamp("DELIVERY_CREATED_AT");
        String payload = safeGetString(rs, "PAYLOAD");
        return new SubscriptionDeliverySummary(deliveryId, eventId, subscriptionId, topicName, currentStatus,
                deliveryMode, occurredAt, createdAt, payload);
    }

    private static String safeGetString(ResultSet rs, String columnLabel) throws SQLException {
        try {
            return rs.getString(columnLabel);
        } catch (SQLException e) {
            return null;
        }
    }
}
