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

package org.wso2.dpdp.accelerator.event.notifications.service.dao.impl;

import org.wso2.dpdp.accelerator.event.notifications.service.dao.AbstractDAO;
import org.wso2.dpdp.accelerator.event.notifications.service.dao.DeliveryDAO;
import org.wso2.dpdp.accelerator.event.notifications.service.dao.model.PollDelivery;
import org.wso2.dpdp.accelerator.event.notifications.service.dao.model.SubscriptionDeliverySummary;
import org.wso2.dpdp.accelerator.event.notifications.service.dao.model.WebhookDelivery;
import org.wso2.dpdp.accelerator.event.notifications.service.dao.model.WebhookDeliveryAudit;
import org.wso2.dpdp.accelerator.event.notifications.service.exception.DataAccessException;
import org.wso2.dpdp.accelerator.event.notifications.service.queries.DeliveryQueries;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * JDBC implementation of {@link DeliveryDAO} for managing delivery records and audit history.
 */
public class DeliveryDAOImpl extends AbstractDAO implements DeliveryDAO {

    @Override
    public boolean addWebhookDelivery(WebhookDelivery delivery) {
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(DeliveryQueries.ADD_WEBHOOK_DELIVERY)) {
            ps.setString(1, delivery.getDeliveryId());
            ps.setString(2, delivery.getSubscriptionId());
            ps.setString(3, delivery.getEventId());
            ps.setString(4, delivery.getStatus() != null ? delivery.getStatus() : "pending");
            ps.setInt(5, delivery.getAttemptCount());
            ps.setTimestamp(6, delivery.getNextRetryAt());
            ps.setTimestamp(7, delivery.getCreatedAt() != null ? delivery.getCreatedAt() : new java.sql.Timestamp(System.currentTimeMillis()));
            ps.setTimestamp(8, delivery.getUpdatedAt() != null ? delivery.getUpdatedAt() : new java.sql.Timestamp(System.currentTimeMillis()));
            ps.setTimestamp(9, delivery.getDeliveredAt());
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            throw new DataAccessException("Error adding webhook delivery [" + (delivery != null ? delivery.getDeliveryId() : "null") + "]", e);
        }
    }

    @Override
    public Optional<WebhookDelivery> getWebhookDeliveryById(String deliveryId, String orgId) {
        String sql = "SELECT d.DELIVERY_ID, d.SUBSCRIPTION_ID, d.EVENT_ID, d.STATUS, d.ATTEMPT_COUNT, d.NEXT_RETRY_AT, " +
                "d.CREATED_AT, d.UPDATED_AT, d.DELIVERED_AT FROM WEBHOOK_DELIVERY d " +
                "JOIN SUBSCRIPTION s ON d.SUBSCRIPTION_ID = s.SUBSCRIPTION_ID WHERE d.DELIVERY_ID = ? AND s.ORG_ID = ?";
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, deliveryId);
            ps.setString(2, orgId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(new WebhookDelivery(
                            rs.getString("DELIVERY_ID"),
                            rs.getString("SUBSCRIPTION_ID"),
                            rs.getString("EVENT_ID"),
                            rs.getString("STATUS"),
                            rs.getInt("ATTEMPT_COUNT"),
                            rs.getTimestamp("NEXT_RETRY_AT"),
                            rs.getTimestamp("CREATED_AT"),
                            rs.getTimestamp("UPDATED_AT"),
                            rs.getTimestamp("DELIVERED_AT")
                    ));
                }
            }
            return Optional.empty();
        } catch (SQLException e) {
            throw new DataAccessException("Error getting webhook delivery [" + deliveryId + "]", e);
        }
    }

    @Override
    public List<WebhookDelivery> getPendingWebhookDeliveries(int limit) {
        List<WebhookDelivery> list = new ArrayList<>();
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(DeliveryQueries.GET_PENDING_WEBHOOK_DELIVERIES)) {
            ps.setInt(1, limit);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(new WebhookDelivery(
                            rs.getString("DELIVERY_ID"),
                            rs.getString("SUBSCRIPTION_ID"),
                            rs.getString("EVENT_ID"),
                            rs.getString("STATUS"),
                            rs.getInt("ATTEMPT_COUNT"),
                            rs.getTimestamp("NEXT_RETRY_AT"),
                            rs.getTimestamp("CREATED_AT"),
                            rs.getTimestamp("UPDATED_AT"),
                            rs.getTimestamp("DELIVERED_AT")
                    ));
                }
            }
            return list;
        } catch (SQLException e) {
            throw new DataAccessException("Error getting pending webhook deliveries", e);
        }
    }

    @Override
    public boolean updateWebhookDeliveryStatus(WebhookDelivery delivery) {
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(DeliveryQueries.UPDATE_WEBHOOK_DELIVERY_STATUS)) {
            ps.setString(1, delivery.getStatus());
            ps.setInt(2, delivery.getAttemptCount());
            ps.setTimestamp(3, delivery.getNextRetryAt());
            ps.setTimestamp(4, delivery.getDeliveredAt());
            ps.setString(5, delivery.getDeliveryId());
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            throw new DataAccessException("Error updating webhook delivery status for [" + (delivery != null ? delivery.getDeliveryId() : "null") + "]", e);
        }
    }

    @Override
    public boolean addWebhookDeliveryAudit(WebhookDeliveryAudit audit) {
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(DeliveryQueries.ADD_WEBHOOK_DELIVERY_AUDIT)) {
            ps.setString(1, audit.getAuditId());
            ps.setString(2, audit.getEventId());
            ps.setString(3, audit.getDeliveryId());
            ps.setString(4, audit.getOrgId());
            ps.setString(5, audit.getResponseCode());
            ps.setTimestamp(6, audit.getCreatedAt() != null ? audit.getCreatedAt() : new java.sql.Timestamp(System.currentTimeMillis()));
            ps.setTimestamp(7, audit.getAttemptAt() != null ? audit.getAttemptAt() : new java.sql.Timestamp(System.currentTimeMillis()));
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            throw new DataAccessException("Error adding delivery audit [" + (audit != null ? audit.getAuditId() : "null") + "]", e);
        }
    }

    @Override
    public boolean addPollDelivery(PollDelivery delivery) {
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(DeliveryQueries.ADD_POLL_DELIVERY)) {
            ps.setString(1, delivery.getDeliveryId());
            ps.setString(2, delivery.getSubscriptionId());
            ps.setString(3, delivery.getEventId());
            ps.setString(4, delivery.getStatus() != null ? delivery.getStatus() : "pending");
            ps.setTimestamp(5, delivery.getCreatedAt() != null ? delivery.getCreatedAt() : new java.sql.Timestamp(System.currentTimeMillis()));
            ps.setTimestamp(6, delivery.getCompletedAt());
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            throw new DataAccessException("Error adding poll delivery [" + (delivery != null ? delivery.getDeliveryId() : "null") + "]", e);
        }
    }

    @Override
    public Optional<PollDelivery> getPollDeliveryById(String deliveryId, String orgId) {
        String sql = "SELECT p.DELIVERY_ID, p.SUBSCRIPTION_ID, p.EVENT_ID, p.STATUS, p.CREATED_AT, p.COMPLETED_AT " +
                "FROM POLL_DELIVERY p JOIN SUBSCRIPTION s ON p.SUBSCRIPTION_ID = s.SUBSCRIPTION_ID " +
                "WHERE p.DELIVERY_ID = ? AND s.ORG_ID = ?";
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, deliveryId);
            ps.setString(2, orgId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(new PollDelivery(
                            rs.getString("DELIVERY_ID"),
                            rs.getString("SUBSCRIPTION_ID"),
                            rs.getString("EVENT_ID"),
                            rs.getString("STATUS"),
                            rs.getTimestamp("CREATED_AT"),
                            rs.getTimestamp("COMPLETED_AT")
                    ));
                }
            }
            return Optional.empty();
        } catch (SQLException e) {
            throw new DataAccessException("Error getting poll delivery [" + deliveryId + "]", e);
        }
    }

    @Override
    public List<PollDelivery> getPendingPollDeliveries(String orgId, String groupId, int limit) {
        List<PollDelivery> deliveries = new ArrayList<>();
        String sql = "SELECT p.DELIVERY_ID, p.SUBSCRIPTION_ID, p.EVENT_ID, p.STATUS, p.CREATED_AT, p.COMPLETED_AT " +
                "FROM POLL_DELIVERY p JOIN SUBSCRIPTION s ON p.SUBSCRIPTION_ID = s.SUBSCRIPTION_ID " +
                "WHERE s.ORG_ID = ? AND s.GROUP_ID = ? AND s.DELIVERY_MODE = 'poll' AND p.STATUS = 'pending' " +
                "ORDER BY p.CREATED_AT ASC LIMIT ?";
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, orgId);
            ps.setString(2, groupId);
            ps.setInt(3, limit);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    deliveries.add(new PollDelivery(
                            rs.getString("DELIVERY_ID"),
                            rs.getString("SUBSCRIPTION_ID"),
                            rs.getString("EVENT_ID"),
                            rs.getString("STATUS"),
                            rs.getTimestamp("CREATED_AT"),
                            rs.getTimestamp("COMPLETED_AT")
                    ));
                }
            }
            return deliveries;
        } catch (SQLException e) {
            throw new DataAccessException("Error getting pending poll deliveries for group [" + groupId + "]", e);
        }
    }

    @Override
    public boolean updatePollDeliveryStatus(String deliveryId, String status) {
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(DeliveryQueries.UPDATE_POLL_DELIVERY_STATUS)) {
            ps.setString(1, status);
            ps.setTimestamp(2, new java.sql.Timestamp(System.currentTimeMillis()));
            ps.setString(3, deliveryId);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            throw new DataAccessException("Error updating poll delivery status for [" + deliveryId + "]", e);
        }
    }

    @Override
    public boolean isDeliveryExistsForOrg(String deliveryId, String orgId) {
        return getWebhookDeliveryById(deliveryId, orgId).isPresent() || getPollDeliveryById(deliveryId, orgId).isPresent();
    }

    @Override
    public List<WebhookDeliveryAudit> getWebhookDeliveryAudits(String deliveryId) {
        List<WebhookDeliveryAudit> list = new ArrayList<>();
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(DeliveryQueries.GET_WEBHOOK_DELIVERY_AUDITS_BY_DELIVERY_ID)) {
            ps.setString(1, deliveryId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(new WebhookDeliveryAudit(
                            rs.getString("AUDIT_ID"),
                            rs.getString("EVENT_ID"),
                            rs.getString("DELIVERY_ID"),
                            rs.getString("ORG_ID"),
                            rs.getString("RESPONSE_CODE"),
                            rs.getTimestamp("CREATED_AT"),
                            rs.getTimestamp("ATTEMPT_AT")
                    ));
                }
            }
            return list;
        } catch (SQLException e) {
            throw new DataAccessException("Error getting audits for delivery [" + deliveryId + "]", e);
        }
    }

    @Override
    public List<SubscriptionDeliverySummary> listSubscriptionDeliveries(String subscriptionId, int limit, int offset, int[] totalOut) {
        List<SubscriptionDeliverySummary> list = new ArrayList<>();

        String countSql = "SELECT COUNT(*) FROM (" +
                "SELECT d.DELIVERY_ID FROM WEBHOOK_DELIVERY d WHERE d.SUBSCRIPTION_ID = ? " +
                "UNION ALL " +
                "SELECT p.DELIVERY_ID FROM POLL_DELIVERY p WHERE p.SUBSCRIPTION_ID = ?" +
                ") AS total_count";

        String sql = "SELECT * FROM (" +
                "SELECT d.DELIVERY_ID, d.EVENT_ID, t.NAME AS TOPIC_NAME, d.STATUS AS CURRENT_STATUS, 'WEBHOOK' AS DELIVERY_MODE, " +
                "e.CREATED_AT AS OCCURRED_AT, d.CREATED_AT AS DELIVERY_CREATED_AT " +
                "FROM WEBHOOK_DELIVERY d " +
                "JOIN EVENT e ON d.EVENT_ID = e.EVENT_ID " +
                "JOIN TOPIC t ON e.TOPIC_ID = t.TOPIC_ID " +
                "WHERE d.SUBSCRIPTION_ID = ? " +
                "UNION ALL " +
                "SELECT p.DELIVERY_ID, p.EVENT_ID, t.NAME AS TOPIC_NAME, p.STATUS AS CURRENT_STATUS, 'POLL' AS DELIVERY_MODE, " +
                "e.CREATED_AT AS OCCURRED_AT, p.CREATED_AT AS DELIVERY_CREATED_AT " +
                "FROM POLL_DELIVERY p " +
                "JOIN EVENT e ON p.EVENT_ID = e.EVENT_ID " +
                "JOIN TOPIC t ON e.TOPIC_ID = t.TOPIC_ID " +
                "WHERE p.SUBSCRIPTION_ID = ? " +
                ") AS combined_deliveries ORDER BY OCCURRED_AT DESC, DELIVERY_CREATED_AT DESC LIMIT ? OFFSET ?";

        try (Connection conn = getConnection()) {
            try (PreparedStatement countPs = conn.prepareStatement(countSql)) {
                countPs.setString(1, subscriptionId);
                countPs.setString(2, subscriptionId);
                try (ResultSet countRs = countPs.executeQuery()) {
                    if (countRs.next() && totalOut != null && totalOut.length > 0) {
                        totalOut[0] = countRs.getInt(1);
                    }
                }
            }

            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, subscriptionId);
                ps.setString(2, subscriptionId);
                ps.setInt(3, limit);
                ps.setInt(4, offset);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        list.add(new SubscriptionDeliverySummary(
                                rs.getString("DELIVERY_ID"),
                                rs.getString("EVENT_ID"),
                                rs.getString("TOPIC_NAME"),
                                rs.getString("CURRENT_STATUS"),
                                rs.getString("DELIVERY_MODE"),
                                rs.getTimestamp("OCCURRED_AT"),
                                rs.getTimestamp("DELIVERY_CREATED_AT")
                        ));
                    }
                }
            }
            return list;
        } catch (SQLException e) {
            throw new DataAccessException("Error listing deliveries for subscription [" + subscriptionId + "]", e);
        }
    }

    @Override
    public Optional<SubscriptionDeliverySummary> getSubscriptionDeliveryById(String subscriptionId, String deliveryId) {
        String sql = "SELECT * FROM (" +
                "SELECT d.DELIVERY_ID, d.EVENT_ID, t.NAME AS TOPIC_NAME, d.STATUS AS CURRENT_STATUS, 'WEBHOOK' AS DELIVERY_MODE, " +
                "e.CREATED_AT AS OCCURRED_AT, d.CREATED_AT AS DELIVERY_CREATED_AT " +
                "FROM WEBHOOK_DELIVERY d " +
                "JOIN EVENT e ON d.EVENT_ID = e.EVENT_ID " +
                "JOIN TOPIC t ON e.TOPIC_ID = t.TOPIC_ID " +
                "WHERE d.SUBSCRIPTION_ID = ? AND d.DELIVERY_ID = ? " +
                "UNION ALL " +
                "SELECT p.DELIVERY_ID, p.EVENT_ID, t.NAME AS TOPIC_NAME, p.STATUS AS CURRENT_STATUS, 'POLL' AS DELIVERY_MODE, " +
                "e.CREATED_AT AS OCCURRED_AT, p.CREATED_AT AS DELIVERY_CREATED_AT " +
                "FROM POLL_DELIVERY p " +
                "JOIN EVENT e ON p.EVENT_ID = e.EVENT_ID " +
                "JOIN TOPIC t ON e.TOPIC_ID = t.TOPIC_ID " +
                "WHERE p.SUBSCRIPTION_ID = ? AND p.DELIVERY_ID = ? " +
                ") AS single_delivery LIMIT 1";

        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, subscriptionId);
            ps.setString(2, deliveryId);
            ps.setString(3, subscriptionId);
            ps.setString(4, deliveryId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(new SubscriptionDeliverySummary(
                            rs.getString("DELIVERY_ID"),
                            rs.getString("EVENT_ID"),
                            rs.getString("TOPIC_NAME"),
                            rs.getString("CURRENT_STATUS"),
                            rs.getString("DELIVERY_MODE"),
                            rs.getTimestamp("OCCURRED_AT"),
                            rs.getTimestamp("DELIVERY_CREATED_AT")
                    ));
                }
            }
            return Optional.empty();
        } catch (SQLException e) {
            throw new DataAccessException("Error getting delivery [" + deliveryId + "] for subscription [" + subscriptionId + "]", e);
        }
    }

    @Override
    public List<SubscriptionDeliverySummary> listOrgDeliveries(String orgId, String statusFilter, String subscriptionIdFilter, String purposesFilter, String search, int limit, int offset, int[] totalOut) {
        List<SubscriptionDeliverySummary> list = new ArrayList<>();
        StringBuilder whereClause = new StringBuilder(" WHERE 1=1 ");
        List<Object> whereParams = new ArrayList<>();

        if (statusFilter != null && !statusFilter.trim().isEmpty() && !"all".equalsIgnoreCase(statusFilter.trim())) {
            whereClause.append("AND LOWER(CURRENT_STATUS) = ? ");
            whereParams.add(statusFilter.trim().toLowerCase());
        }
        if (subscriptionIdFilter != null && !subscriptionIdFilter.trim().isEmpty() && !"all".equalsIgnoreCase(subscriptionIdFilter.trim())) {
            whereClause.append("AND LOWER(SUBSCRIPTION_ID) = ? ");
            whereParams.add(subscriptionIdFilter.trim().toLowerCase());
        }
        if (purposesFilter != null && !purposesFilter.trim().isEmpty()) {
            String[] purps = purposesFilter.split(",");
            StringBuilder purpClause = new StringBuilder("AND (");
            for (int i = 0; i < purps.length; i++) {
                if (i > 0) purpClause.append(" OR ");
                purpClause.append("EXISTS (SELECT 1 FROM SUBSCRIPTION_PURPOSE sp WHERE sp.SUBSCRIPTION_ID = combined_deliveries.SUBSCRIPTION_ID AND LOWER(sp.PURPOSE_NAME) = ?) ");
                purpClause.append("OR EXISTS (SELECT 1 FROM EVENT_PURPOSE ep WHERE ep.EVENT_ID = combined_deliveries.EVENT_ID AND LOWER(ep.PURPOSE_NAME) = ?) ");
                whereParams.add(purps[i].trim().toLowerCase());
                whereParams.add(purps[i].trim().toLowerCase());
            }
            purpClause.append(") ");
            whereClause.append(purpClause.toString());
        }
        if (search != null && !search.trim().isEmpty()) {
            whereClause.append("AND (DELIVERY_ID LIKE ? OR EVENT_ID LIKE ? OR SUBSCRIPTION_ID LIKE ? OR TOPIC_NAME LIKE ? OR CURRENT_STATUS LIKE ? ");
            whereClause.append("OR EXISTS (SELECT 1 FROM SUBSCRIPTION_PURPOSE sp WHERE sp.SUBSCRIPTION_ID = combined_deliveries.SUBSCRIPTION_ID AND LOWER(sp.PURPOSE_NAME) LIKE ?) ");
            whereClause.append("OR EXISTS (SELECT 1 FROM EVENT_PURPOSE ep WHERE ep.EVENT_ID = combined_deliveries.EVENT_ID AND LOWER(ep.PURPOSE_NAME) LIKE ?) ) ");
            String term = "%" + search.trim().toLowerCase() + "%";
            whereParams.add(term);
            whereParams.add(term);
            whereParams.add(term);
            whereParams.add(term);
            whereParams.add(term);
            whereParams.add(term);
            whereParams.add(term);
        }

        String baseUnion = DeliveryQueries.GET_ORG_DELIVERIES_UNION_BASE;
        String countSql = "SELECT COUNT(*) FROM (" + baseUnion + ") AS combined_deliveries " + whereClause.toString();
        String sql = "SELECT * FROM (" + baseUnion + ") AS combined_deliveries " + whereClause.toString() +
                " ORDER BY OCCURRED_AT DESC, DELIVERY_CREATED_AT DESC LIMIT ? OFFSET ?";

        try (Connection conn = getConnection()) {
            try (PreparedStatement countPs = conn.prepareStatement(countSql)) {
                int pIdx = 1;
                countPs.setString(pIdx++, orgId);
                countPs.setString(pIdx++, orgId);
                for (Object param : whereParams) {
                    countPs.setObject(pIdx++, param);
                }
                try (ResultSet countRs = countPs.executeQuery()) {
                    if (countRs.next() && totalOut != null && totalOut.length > 0) {
                        totalOut[0] = countRs.getInt(1);
                    }
                }
            }

            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                int pIdx = 1;
                ps.setString(pIdx++, orgId);
                ps.setString(pIdx++, orgId);
                for (Object param : whereParams) {
                    ps.setObject(pIdx++, param);
                }
                ps.setInt(pIdx++, limit);
                ps.setInt(pIdx, offset);

                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        list.add(new SubscriptionDeliverySummary(
                                rs.getString("DELIVERY_ID"),
                                rs.getString("EVENT_ID"),
                                rs.getString("SUBSCRIPTION_ID"),
                                rs.getString("TOPIC_NAME"),
                                rs.getString("CURRENT_STATUS"),
                                rs.getString("DELIVERY_MODE"),
                                rs.getTimestamp("OCCURRED_AT"),
                                rs.getTimestamp("DELIVERY_CREATED_AT"),
                                rs.getString("PAYLOAD")
                        ));
                    }
                }
            }
            return list;
        } catch (SQLException e) {
            throw new DataAccessException("Error listing deliveries for org [" + orgId + "]", e);
        }
    }

    @Override
    public Optional<SubscriptionDeliverySummary> getOrgDeliveryById(String orgId, String deliveryId) {
        String baseUnion = DeliveryQueries.GET_ORG_DELIVERIES_UNION_BASE;
        String sql = "SELECT * FROM (" + baseUnion + ") AS single_delivery WHERE DELIVERY_ID = ? LIMIT 1";

        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, orgId);
            ps.setString(2, orgId);
            ps.setString(3, deliveryId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(new SubscriptionDeliverySummary(
                            rs.getString("DELIVERY_ID"),
                            rs.getString("EVENT_ID"),
                            rs.getString("SUBSCRIPTION_ID"),
                            rs.getString("TOPIC_NAME"),
                            rs.getString("CURRENT_STATUS"),
                            rs.getString("DELIVERY_MODE"),
                            rs.getTimestamp("OCCURRED_AT"),
                            rs.getTimestamp("DELIVERY_CREATED_AT"),
                            rs.getString("PAYLOAD")
                    ));
                }
            }
            return Optional.empty();
        } catch (SQLException e) {
            throw new DataAccessException("Error getting delivery [" + deliveryId + "] for org [" + orgId + "]", e);
        }
    }
}
