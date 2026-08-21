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

package org.wso2.dpdp.accelerator.event.notifications.dao.impl;

import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.wso2.dpdp.accelerator.common.config.DPDPConfigurationService;
import org.wso2.dpdp.accelerator.event.notifications.common.constants.EventNotificationCommonConstants;
import org.wso2.dpdp.accelerator.event.notifications.common.enums.PollStatus;
import org.wso2.dpdp.accelerator.event.notifications.common.exception.EventNotificationDataAccessException;
import org.wso2.dpdp.accelerator.event.notifications.common.util.DBUtils;
import org.wso2.dpdp.accelerator.event.notifications.dao.DeliveryDAO;
import org.wso2.dpdp.accelerator.event.notifications.dao.model.PollDelivery;
import org.wso2.dpdp.accelerator.event.notifications.dao.model.SubscriptionDeliverySummary;
import org.wso2.dpdp.accelerator.event.notifications.dao.model.WebhookDelivery;
import org.wso2.dpdp.accelerator.event.notifications.dao.model.WebhookDeliveryAudit;
import org.wso2.dpdp.accelerator.event.notifications.dao.model.WebhookDeliveryDispatchContext;
import org.wso2.dpdp.accelerator.event.notifications.dao.queries.EventNotificationCommonDBQueries;
import org.wso2.dpdp.accelerator.event.notifications.dao.queries.EventNotificationQueryFactory;
import org.wso2.dpdp.accelerator.event.notifications.dao.queries.SubscriptionQueryBuilder;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

@Component(service = DeliveryDAO.class, immediate = true)
public class DeliveryDAOImpl implements DeliveryDAO {

    @Reference
    private DPDPConfigurationService configurationService;

    private EventNotificationCommonDBQueries getQueries(Connection conn) {
        return EventNotificationQueryFactory.getQueryProvider(conn);
    }

    private EventNotificationCommonDBQueries getQueries() {
        return EventNotificationQueryFactory.getQueryProvider();
    }

    private static String escapeLikePattern(String text) {
        return SubscriptionQueryBuilder.escapeLikePattern(text);
    }

    @Override
    public boolean addWebhookDelivery(Connection conn, WebhookDelivery delivery) {
        if (conn == null) {
            return DeliveryDAO.super.addWebhookDelivery(delivery);
        }
        try (PreparedStatement ps = conn.prepareStatement(getQueries(conn).getAddWebhookDeliveryQuery())) {
            ps.setString(1, delivery.getDeliveryId());
            ps.setString(2, delivery.getSubscriptionId());
            ps.setString(3, delivery.getEventId());
            ps.setString(4, delivery.getStatus());
            ps.setInt(5, delivery.getAttemptCount());
            ps.setTimestamp(6, delivery.getNextRetryAt());
            ps.setTimestamp(7, delivery.getCreatedAt());
            ps.setTimestamp(8, delivery.getUpdatedAt());
            ps.setTimestamp(9, delivery.getDeliveredAt());
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            throw new EventNotificationDataAccessException(
                    String.format(EventNotificationCommonConstants.ERROR_ADDING_WEBHOOK_DELIVERY,
                            (delivery != null ? delivery.getDeliveryId() : "null")),
                    e);
        }
    }

    @Override
    public Optional<WebhookDelivery> getWebhookDeliveryById(String deliveryId, String orgId) {
        try (Connection conn = DBUtils.getConnection();
                PreparedStatement ps = conn.prepareStatement(getQueries(conn).getGetWebhookDeliveryByIdAndOrgQuery())) {
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
                            rs.getTimestamp("DELIVERED_AT")));
                }
            }
            return Optional.empty();
        } catch (SQLException e) {
            throw new EventNotificationDataAccessException(
                    String.format(EventNotificationCommonConstants.ERROR_GETTING_WEBHOOK_DELIVERY, deliveryId), e);
        }
    }

    @Override
    public List<WebhookDelivery> getPendingWebhookDeliveries(int limit) {
        List<WebhookDelivery> list = new ArrayList<>();
        try (Connection conn = DBUtils.getConnection();
                PreparedStatement ps = conn.prepareStatement(getQueries(conn).getGetPendingWebhookDeliveriesQuery())) {
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
                            rs.getTimestamp("DELIVERED_AT")));
                }
            }
            return list;
        } catch (SQLException e) {
            throw new EventNotificationDataAccessException(
                    EventNotificationCommonConstants.ERROR_GETTING_PENDING_WEBHOOK_DELIVERIES, e);
        }
    }

    @Override
    public List<WebhookDeliveryDispatchContext> getPendingWebhookDispatchContexts(int limit) {
        return loadDispatchContexts(getQueries().getGetPendingWebhookDispatchContextsQuery(), limit);
    }

    @Override
    public List<WebhookDeliveryDispatchContext> getStuckInFlightWebhookDispatchContexts(int limit) {
        int threshold = getConfiguration().getEventNotificationStuckInFlightThresholdSeconds();
        Timestamp cutoff = new Timestamp(System.currentTimeMillis() - threshold * 1000L);
        return getStuckInFlightWebhookDispatchContexts(limit, cutoff);
    }

    @Override
    public List<WebhookDeliveryDispatchContext> getStuckInFlightWebhookDispatchContexts(int limit, Timestamp updatedBefore) {
        return loadDispatchContextsWithCutoff(getQueries().getGetStuckInFlightWebhookDispatchContextsQuery(), limit, updatedBefore);
    }

    private List<WebhookDeliveryDispatchContext> loadDispatchContexts(String sql, int limit) {
        List<WebhookDeliveryDispatchContext> list = new ArrayList<>();
        try (Connection conn = DBUtils.getConnection();
                PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, limit);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    WebhookDelivery delivery = new WebhookDelivery(
                            rs.getString("DELIVERY_ID"),
                            rs.getString("SUBSCRIPTION_ID"),
                            rs.getString("EVENT_ID"),
                            rs.getString("STATUS"),
                            rs.getInt("ATTEMPT_COUNT"),
                            rs.getTimestamp("NEXT_RETRY_AT"),
                            rs.getTimestamp("CREATED_AT"),
                            rs.getTimestamp("UPDATED_AT"),
                            rs.getTimestamp("DELIVERED_AT"));
                    list.add(new WebhookDeliveryDispatchContext(
                            delivery,
                            rs.getString("ORG_ID"),
                            rs.getString("CALLBACK_URL"),
                            rs.getString("SHARED_SECRET"),
                            rs.getString("PAYLOAD"),
                            rs.getTimestamp("UPDATED_AT"),
                            rs.getString("TOPIC_ID"),
                            rs.getString("TOPIC_NAME")));
                }
            }
            return list;
        } catch (SQLException e) {
            throw new EventNotificationDataAccessException(
                    EventNotificationCommonConstants.ERROR_GETTING_PENDING_WEBHOOK_DELIVERIES, e);
        }
    }

    private List<WebhookDeliveryDispatchContext> loadDispatchContextsWithCutoff(String sql, int limit, Timestamp cutoff) {
        List<WebhookDeliveryDispatchContext> list = new ArrayList<>();
        try (Connection conn = DBUtils.getConnection();
                PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setTimestamp(1, cutoff != null ? cutoff : new Timestamp(System.currentTimeMillis()));
            ps.setInt(2, limit);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    WebhookDelivery delivery = new WebhookDelivery(
                            rs.getString("DELIVERY_ID"),
                            rs.getString("SUBSCRIPTION_ID"),
                            rs.getString("EVENT_ID"),
                            rs.getString("STATUS"),
                            rs.getInt("ATTEMPT_COUNT"),
                            rs.getTimestamp("NEXT_RETRY_AT"),
                            rs.getTimestamp("CREATED_AT"),
                            rs.getTimestamp("UPDATED_AT"),
                            rs.getTimestamp("DELIVERED_AT"));
                    list.add(new WebhookDeliveryDispatchContext(
                            delivery,
                            rs.getString("ORG_ID"),
                            rs.getString("CALLBACK_URL"),
                            rs.getString("SHARED_SECRET"),
                            rs.getString("PAYLOAD"),
                            rs.getTimestamp("UPDATED_AT"),
                            rs.getString("TOPIC_ID"),
                            rs.getString("TOPIC_NAME")));
                }
            }
            return list;
        } catch (SQLException e) {
            throw new EventNotificationDataAccessException(
                    EventNotificationCommonConstants.ERROR_GETTING_PENDING_WEBHOOK_DELIVERIES, e);
        }
    }

    @Override
    public boolean updateWebhookDeliveryStatus(WebhookDelivery delivery) {
        try (Connection conn = DBUtils.getConnection()) {
            return updateWebhookDeliveryStatus(conn, delivery);
        } catch (SQLException e) {
            throw new EventNotificationDataAccessException(
                    String.format(EventNotificationCommonConstants.ERROR_UPDATING_WEBHOOK_DELIVERY_STATUS,
                            (delivery != null ? delivery.getDeliveryId() : "null")),
                    e);
        }
    }

    public boolean updateWebhookDeliveryStatus(Connection conn, WebhookDelivery delivery) {
        try (PreparedStatement ps = conn.prepareStatement(getQueries(conn).getUpdateWebhookDeliveryStatusQuery())) {
            ps.setString(1, delivery.getStatus());
            ps.setInt(2, delivery.getAttemptCount());
            ps.setTimestamp(3, delivery.getNextRetryAt());
            ps.setTimestamp(4, delivery.getDeliveredAt());
            ps.setString(5, delivery.getDeliveryId());
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            throw new EventNotificationDataAccessException(
                    String.format(EventNotificationCommonConstants.ERROR_UPDATING_WEBHOOK_DELIVERY_STATUS,
                            (delivery != null ? delivery.getDeliveryId() : "null")),
                    e);
        }
    }

    @Override
    public boolean recordSuccessfulAttempt(WebhookDeliveryAudit audit, WebhookDelivery delivery) {
        try (Connection conn = DBUtils.getConnection()) {
            boolean originalAutoCommit = conn.getAutoCommit();
            conn.setAutoCommit(false);
            try {
                addWebhookDeliveryAudit(conn, audit);
                boolean updated = updateWebhookDeliveryStatus(conn, delivery);
                conn.commit();
                return updated;
            } catch (Exception e) {
                try {
                    conn.rollback();
                } catch (SQLException ignored) {
                }
                throw e;
            } finally {
                try {
                    conn.setAutoCommit(originalAutoCommit);
                } catch (SQLException ignored) {
                }
            }
        } catch (SQLException e) {
            throw new EventNotificationDataAccessException(
                    String.format(EventNotificationCommonConstants.ERROR_UPDATING_WEBHOOK_DELIVERY_STATUS,
                            (delivery != null ? delivery.getDeliveryId() : "null")),
                    e);
        }
    }

    @Override
    public boolean recordRetryableFailure(WebhookDeliveryAudit audit, String deliveryId, int attemptCount, Timestamp nextRetryAt) {
        try (Connection conn = DBUtils.getConnection()) {
            boolean originalAutoCommit = conn.getAutoCommit();
            conn.setAutoCommit(false);
            try {
                addWebhookDeliveryAudit(conn, audit);
                boolean released = releaseWebhookDelivery(conn, deliveryId, attemptCount, nextRetryAt);
                conn.commit();
                return released;
            } catch (Exception e) {
                try {
                    conn.rollback();
                } catch (SQLException ignored) {
                }
                throw e;
            } finally {
                try {
                    conn.setAutoCommit(originalAutoCommit);
                } catch (SQLException ignored) {
                }
            }
        } catch (SQLException e) {
            throw new EventNotificationDataAccessException(
                    String.format(EventNotificationCommonConstants.ERROR_UPDATING_WEBHOOK_DELIVERY_STATUS, deliveryId), e);
        }
    }

    @Override
    public boolean recordPermanentFailure(WebhookDeliveryAudit audit, WebhookDelivery delivery) {
        try (Connection conn = DBUtils.getConnection()) {
            boolean originalAutoCommit = conn.getAutoCommit();
            conn.setAutoCommit(false);
            try {
                addWebhookDeliveryAudit(conn, audit);
                boolean updated = updateWebhookDeliveryStatus(conn, delivery);
                conn.commit();
                return updated;
            } catch (Exception e) {
                try {
                    conn.rollback();
                } catch (SQLException ignored) {
                }
                throw e;
            } finally {
                try {
                    conn.setAutoCommit(originalAutoCommit);
                } catch (SQLException ignored) {
                }
            }
        } catch (SQLException e) {
            throw new EventNotificationDataAccessException(
                    String.format(EventNotificationCommonConstants.ERROR_UPDATING_WEBHOOK_DELIVERY_STATUS,
                            (delivery != null ? delivery.getDeliveryId() : "null")),
                    e);
        }
    }

    @Override
    public boolean addWebhookDeliveryAudit(WebhookDeliveryAudit audit) {
        try (Connection conn = DBUtils.getConnection()) {
            return addWebhookDeliveryAudit(conn, audit);
        } catch (SQLException e) {
            throw new EventNotificationDataAccessException(
                    String.format(EventNotificationCommonConstants.ERROR_ADDING_WEBHOOK_DELIVERY_AUDIT,
                            (audit != null ? audit.getDeliveryId() : "null")),
                    e);
        }
    }

    public boolean addWebhookDeliveryAudit(Connection conn, WebhookDeliveryAudit audit) {
        try (PreparedStatement ps = conn.prepareStatement(getQueries(conn).getAddWebhookDeliveryAuditQuery())) {
            ps.setString(1, audit.getAuditId());
            ps.setString(2, audit.getEventId());
            ps.setString(3, audit.getDeliveryId());
            ps.setString(4, audit.getOrgId());
            ps.setString(5, audit.getResponseCode());
            ps.setTimestamp(6, audit.getCreatedAt());
            ps.setTimestamp(7, audit.getAttemptAt());
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            throw new EventNotificationDataAccessException(
                    String.format(EventNotificationCommonConstants.ERROR_ADDING_WEBHOOK_DELIVERY_AUDIT,
                            (audit != null ? audit.getDeliveryId() : "null")),
                    e);
        }
    }

    @Override
    public List<WebhookDeliveryAudit> getWebhookDeliveryAudits(String deliveryId, String orgId) {
        List<WebhookDeliveryAudit> list = new ArrayList<>();
        try (Connection conn = DBUtils.getConnection();
                PreparedStatement ps = conn
                        .prepareStatement(getQueries(conn).getGetWebhookDeliveryAuditsByDeliveryIdQuery())) {
            ps.setString(1, deliveryId);
            ps.setString(2, orgId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(new WebhookDeliveryAudit(
                            rs.getString("AUDIT_ID"),
                            rs.getString("EVENT_ID"),
                            rs.getString("DELIVERY_ID"),
                            rs.getString("ORG_ID"),
                            rs.getString("RESPONSE_CODE"),
                            rs.getTimestamp("CREATED_AT"),
                            rs.getTimestamp("ATTEMPT_AT")));
                }
            }
            return list;
        } catch (SQLException e) {
            throw new EventNotificationDataAccessException(
                    String.format(EventNotificationCommonConstants.ERROR_GETTING_WEBHOOK_DELIVERY_AUDITS, deliveryId),
                    e);
        }
    }

    @Override
    public boolean addPollDelivery(PollDelivery delivery) {
        try (Connection conn = DBUtils.getConnection();
                PreparedStatement ps = conn.prepareStatement(getQueries(conn).getAddPollDeliveryQuery())) {
            ps.setString(1, delivery.getDeliveryId());
            ps.setString(2, delivery.getSubscriptionId());
            ps.setString(3, delivery.getEventId());
            ps.setString(4, delivery.getStatus());
            ps.setTimestamp(5, delivery.getCreatedAt());
            ps.setTimestamp(6, delivery.getCompletedAt());
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            throw new EventNotificationDataAccessException(
                    String.format(EventNotificationCommonConstants.ERROR_ADDING_POLL_DELIVERY,
                            (delivery != null ? delivery.getDeliveryId() : "null")),
                    e);
        }
    }

    @Override
    public Optional<PollDelivery> getPollDeliveryById(String deliveryId, String orgId) {
        try (Connection conn = DBUtils.getConnection();
                PreparedStatement ps = conn.prepareStatement(getQueries(conn).getGetPollDeliveryByIdAndOrgQuery())) {
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
                            rs.getTimestamp("COMPLETED_AT")));
                }
            }
            return Optional.empty();
        } catch (SQLException e) {
            throw new EventNotificationDataAccessException(
                    String.format(EventNotificationCommonConstants.ERROR_GETTING_POLL_DELIVERY, deliveryId), e);
        }
    }

    @Override
    public List<WebhookDelivery> getStuckInFlightWebhookDeliveries(int limit) {
        int threshold = getConfiguration().getEventNotificationStuckInFlightThresholdSeconds();
        Timestamp cutoff = new Timestamp(System.currentTimeMillis() - threshold * 1000L);
        return getStuckInFlightWebhookDeliveries(limit, cutoff);
    }

    @Override
    public List<WebhookDelivery> getStuckInFlightWebhookDeliveries(int limit, Timestamp updatedBefore) {
        List<WebhookDelivery> list = new ArrayList<>();
        try (Connection conn = DBUtils.getConnection();
                PreparedStatement ps = conn.prepareStatement(
                        getQueries(conn).getGetStuckInFlightWebhookDeliveriesQuery())) {
            ps.setTimestamp(1, updatedBefore != null ? updatedBefore : new Timestamp(System.currentTimeMillis()));
            ps.setInt(2, limit);
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
                            rs.getTimestamp("DELIVERED_AT")));
                }
            }
            return list;
        } catch (SQLException e) {
            throw new EventNotificationDataAccessException(
                    EventNotificationCommonConstants.ERROR_GETTING_PENDING_WEBHOOK_DELIVERIES, e);
        }
    }

    @Override
    public List<PollDelivery> getPendingPollDeliveries(String orgId, String groupId, int limit) {
        List<PollDelivery> candidates = new ArrayList<>();
        try (Connection conn = DBUtils.getConnection();
                PreparedStatement ps = conn.prepareStatement(getQueries(conn).getGetPendingPollDeliveriesQuery())) {
            ps.setString(1, orgId);
            ps.setString(2, groupId);
            ps.setInt(3, limit);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    candidates.add(new PollDelivery(
                            rs.getString("DELIVERY_ID"),
                            rs.getString("SUBSCRIPTION_ID"),
                            rs.getString("EVENT_ID"),
                            rs.getString("STATUS"),
                            rs.getTimestamp("CREATED_AT"),
                            rs.getTimestamp("COMPLETED_AT")));
                }
            }
            return candidates;
        } catch (SQLException e) {
            throw new EventNotificationDataAccessException(
                    String.format(EventNotificationCommonConstants.ERROR_GETTING_PENDING_POLL_DELIVERIES, groupId), e);
        }
    }

    @Override
    public void updatePollDeliveryStatuses(String orgId, String groupId, List<String> ackEventIds,
            List<String> errEventIds) {
        if (orgId == null || orgId.trim().isEmpty() || groupId == null || groupId.trim().isEmpty()) {
            throw new IllegalArgumentException(
                    "Organization ID and Group ID cannot be empty when updating poll delivery statuses.");
        }
        if ((ackEventIds == null || ackEventIds.isEmpty()) && (errEventIds == null || errEventIds.isEmpty())) {
            return;
        }

        try (Connection conn = DBUtils.getConnection()) {
            boolean originalAutoCommit = conn.getAutoCommit();
            try {
                conn.setAutoCommit(false);
                EventNotificationCommonDBQueries queries = getQueries(conn);

                try (PreparedStatement updatePs = conn
                        .prepareStatement(queries.getUpdatePollDeliveryStatusByEventAndGroupQuery())) {
                    if (ackEventIds != null && !ackEventIds.isEmpty()) {
                        for (String eventId : ackEventIds) {
                            if (eventId != null && !eventId.trim().isEmpty()) {
                                updatePs.setString(1, PollStatus.ACKNOWLEDGED.getValue());
                                updatePs.setString(2, eventId.trim());
                                updatePs.setString(3, orgId);
                                updatePs.setString(4, groupId);
                                updatePs.addBatch();
                            }
                        }
                        updatePs.executeBatch();
                    }

                    if (errEventIds != null && !errEventIds.isEmpty()) {
                        for (String eventId : errEventIds) {
                            if (eventId != null && !eventId.trim().isEmpty()) {
                                updatePs.setString(1, PollStatus.ERR.getValue());
                                updatePs.setString(2, eventId.trim());
                                updatePs.setString(3, orgId);
                                updatePs.setString(4, groupId);
                                updatePs.addBatch();
                            }
                        }
                        updatePs.executeBatch();
                    }

                    conn.commit();
                }
            } catch (SQLException e) {
                try {
                    conn.rollback();
                } catch (SQLException rollbackEx) {
                    e.addSuppressed(rollbackEx);
                }
                throw e;
            } finally {
                try {
                    conn.setAutoCommit(originalAutoCommit);
                } catch (SQLException ignored) {
                }
            }
        } catch (SQLException e) {
            throw new EventNotificationDataAccessException(
                    String.format(EventNotificationCommonConstants.ERROR_UPDATING_POLL_DELIVERY_STATUSES, groupId), e);
        }
    }

    @Override
    public boolean claimWebhookDelivery(String deliveryId) {
        try (Connection conn = DBUtils.getConnection();
                PreparedStatement ps = conn.prepareStatement(getQueries(conn).getClaimWebhookDeliveryQuery())) {
            ps.setString(1, deliveryId);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            throw new EventNotificationDataAccessException(
                    String.format(EventNotificationCommonConstants.ERROR_UPDATING_WEBHOOK_DELIVERY_STATUS, deliveryId), e);
        }
    }

    @Override
    public boolean claimStuckWebhookDelivery(String deliveryId, Timestamp updatedBefore) {
        try (Connection conn = DBUtils.getConnection();
                PreparedStatement ps = conn.prepareStatement(getQueries(conn).getClaimStuckWebhookDeliveryQuery())) {
            ps.setString(1, deliveryId);
            ps.setTimestamp(2, updatedBefore != null ? updatedBefore : new Timestamp(System.currentTimeMillis()));
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            throw new EventNotificationDataAccessException(
                    String.format(EventNotificationCommonConstants.ERROR_UPDATING_WEBHOOK_DELIVERY_STATUS, deliveryId), e);
        }
    }

    @Override
    public boolean releaseWebhookDelivery(String deliveryId, int attemptCount, Timestamp nextRetryAt) {
        try (Connection conn = DBUtils.getConnection()) {
            return releaseWebhookDelivery(conn, deliveryId, attemptCount, nextRetryAt);
        } catch (SQLException e) {
            throw new EventNotificationDataAccessException(
                    String.format(EventNotificationCommonConstants.ERROR_UPDATING_WEBHOOK_DELIVERY_STATUS, deliveryId), e);
        }
    }

    public boolean releaseWebhookDelivery(Connection conn, String deliveryId, int attemptCount, Timestamp nextRetryAt) {
        if (deliveryId == null || deliveryId.trim().isEmpty()) {
            return false;
        }
        try (PreparedStatement ps = conn.prepareStatement(getQueries(conn).getReleaseWebhookDeliveryQuery())) {
            ps.setInt(1, attemptCount);
            ps.setTimestamp(2, nextRetryAt);
            ps.setString(3, deliveryId.trim());
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            throw new EventNotificationDataAccessException(
                    String.format(EventNotificationCommonConstants.ERROR_UPDATING_WEBHOOK_DELIVERY_STATUS, deliveryId), e);
        }
    }

    @Override
    public boolean claimPollDelivery(String deliveryId) {
        try (Connection conn = DBUtils.getConnection();
                PreparedStatement ps = conn.prepareStatement(getQueries(conn).getClaimPollDeliveryQuery())) {
            ps.setString(1, deliveryId);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            throw new EventNotificationDataAccessException(
                    String.format(EventNotificationCommonConstants.ERROR_UPDATING_POLL_DELIVERY_STATUS, deliveryId), e);
        }
    }

    @Override
    public boolean updatePollDeliveryStatus(String deliveryId, String status) {
        try (Connection conn = DBUtils.getConnection();
                PreparedStatement ps = conn.prepareStatement(getQueries(conn).getUpdatePollDeliveryStatusQuery())) {
            ps.setString(1, status);
            ps.setTimestamp(2, new java.sql.Timestamp(System.currentTimeMillis()));
            ps.setString(3, deliveryId);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            throw new EventNotificationDataAccessException(
                    String.format(EventNotificationCommonConstants.ERROR_UPDATING_POLL_DELIVERY_STATUS, deliveryId), e);
        }
    }

    @Override
    public boolean updatePollDeliveryStatus(String deliveryId, String expectedStatus, String newStatus) {
        if (expectedStatus == null || expectedStatus.trim().isEmpty()) {
            return updatePollDeliveryStatus(deliveryId, newStatus);
        }
        try (Connection conn = DBUtils.getConnection();
                PreparedStatement ps = conn.prepareStatement(getQueries(conn).getUpdatePollDeliveryStatusGuardedQuery())) {
            ps.setString(1, newStatus);
            ps.setTimestamp(2, new java.sql.Timestamp(System.currentTimeMillis()));
            ps.setString(3, deliveryId);
            ps.setString(4, expectedStatus);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            throw new EventNotificationDataAccessException(
                    String.format(EventNotificationCommonConstants.ERROR_UPDATING_POLL_DELIVERY_STATUS, deliveryId), e);
        }
    }

    @Override
    public List<SubscriptionDeliverySummary> listSubscriptionDeliveries(String orgId, String subscriptionId, int limit,
            int offset, int[] totalOut) {
        List<SubscriptionDeliverySummary> list = new ArrayList<>();

        try (Connection conn = DBUtils.getConnection()) {
            EventNotificationCommonDBQueries queries = getQueries(conn);
            String baseSql = queries.getGetSubscriptionDeliveriesUnionBaseQuery();
            String countSql = "SELECT COUNT(*) FROM (" + baseSql + ") AS u";
            String pageSql = baseSql + queries.getPaginationClause("DELIVERY_CREATED_AT DESC");
            try (PreparedStatement countPs = conn.prepareStatement(countSql)) {
                countPs.setString(1, subscriptionId);
                countPs.setString(2, orgId);
                countPs.setString(3, subscriptionId);
                countPs.setString(4, orgId);
                try (ResultSet rs = countPs.executeQuery()) {
                    if (rs.next()) {
                        totalOut[0] = rs.getInt(1);
                    }
                }
            }

            try (PreparedStatement ps = conn.prepareStatement(pageSql)) {
                ps.setString(1, subscriptionId);
                ps.setString(2, orgId);
                ps.setString(3, subscriptionId);
                ps.setString(4, orgId);
                ps.setInt(5, limit);
                ps.setInt(6, offset);

                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        list.add(mapSummary(rs));
                    }
                }
            }
            return list;
        } catch (SQLException e) {
            throw new EventNotificationDataAccessException(
                    String.format(EventNotificationCommonConstants.ERROR_LISTING_DELIVERIES_FOR_SUBSCRIPTION,
                            subscriptionId),
                    e);
        }
    }

    @Override
    public Optional<SubscriptionDeliverySummary> getSubscriptionDeliveryById(String orgId, String subscriptionId, String deliveryId) {
        try (Connection conn = DBUtils.getConnection();
                PreparedStatement ps = conn.prepareStatement(getQueries(conn).getGetSubscriptionDeliveryByIdQuery())) {
            ps.setString(1, subscriptionId);
            ps.setString(2, deliveryId);
            ps.setString(3, orgId);
            ps.setString(4, subscriptionId);
            ps.setString(5, deliveryId);
            ps.setString(6, orgId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapSummary(rs));
                }
            }
            return Optional.empty();
        } catch (SQLException e) {
            throw new EventNotificationDataAccessException(
                    String.format(EventNotificationCommonConstants.ERROR_GETTING_SUBSCRIPTION_DELIVERY, deliveryId), e);
        }
    }

    @Override
    public List<SubscriptionDeliverySummary> listOrgDeliveries(String orgId, String statusFilter,
            String subscriptionIdFilter, String groupIdFilter, String purposesFilter, String search, int limit, int offset, int[] totalOut) {
        List<SubscriptionDeliverySummary> list = new ArrayList<>();
        StringBuilder outerWhere = new StringBuilder();
        List<Object> outerParams = new ArrayList<>();

        if (statusFilter != null && !statusFilter.trim().isEmpty()) {
            outerWhere.append(outerWhere.length() == 0 ? " WHERE " : " AND ");
            outerWhere.append("LOWER(CURRENT_STATUS) = ?");
            outerParams.add(statusFilter.trim().toLowerCase());
        }

        if (subscriptionIdFilter != null && !subscriptionIdFilter.trim().isEmpty()) {
            outerWhere.append(outerWhere.length() == 0 ? " WHERE " : " AND ");
            outerWhere.append("SUBSCRIPTION_ID = ?");
            outerParams.add(subscriptionIdFilter.trim());
        }

        if (groupIdFilter != null && !groupIdFilter.trim().isEmpty()) {
            outerWhere.append(outerWhere.length() == 0 ? " WHERE " : " AND ");
            outerWhere.append("GROUP_ID = ?");
            outerParams.add(groupIdFilter.trim());
        }

        if (purposesFilter != null && !purposesFilter.trim().isEmpty()) {
            String[] purposeTokens = purposesFilter.split(",");
            List<String> validTokens = new ArrayList<>();
            for (String token : purposeTokens) {
                if (token != null && !token.trim().isEmpty()) {
                    validTokens.add(token.trim().toLowerCase());
                }
            }
            if (!validTokens.isEmpty()) {
                outerWhere.append(outerWhere.length() == 0 ? " WHERE " : " AND ");
                outerWhere.append("EVENT_ID IN (SELECT EVENT_ID FROM EVENT_PURPOSE WHERE LOWER(PURPOSE_NAME) IN (");
                for (int i = 0; i < validTokens.size(); i++) {
                    outerWhere.append(i == 0 ? "?" : ", ?");
                    outerParams.add(validTokens.get(i));
                }
                outerWhere.append("))");
            }
        }

        if (search != null && !search.trim().isEmpty()) {
            outerWhere.append(outerWhere.length() == 0 ? " WHERE " : " AND ");
            outerWhere.append("(LOWER(DELIVERY_ID) LIKE ? OR LOWER(EVENT_ID) LIKE ? OR LOWER(GROUP_ID) LIKE ? OR LOWER(TOPIC_NAME) LIKE ?)");
            String term = "%" + escapeLikePattern(search.trim()).toLowerCase() + "%";
            outerParams.add(term);
            outerParams.add(term);
            outerParams.add(term);
            outerParams.add(term);
        }

        try (Connection conn = DBUtils.getConnection()) {
            EventNotificationCommonDBQueries queries = getQueries(conn);
            StringBuilder unionSql = new StringBuilder(queries.getGetOrgDeliveriesUnionBaseQuery());
            List<Object> unionParams = new ArrayList<>(Arrays.asList(orgId, orgId));

            String wrappedSql = "SELECT * FROM (" + unionSql + ") AS u" + outerWhere;
            String countSql = "SELECT COUNT(*) FROM (" + wrappedSql + ") AS c";
            String pageSql = wrappedSql + queries.getPaginationClause("DELIVERY_CREATED_AT DESC");

            try (PreparedStatement countPs = conn.prepareStatement(countSql)) {
                int paramIdx = 1;
                for (Object p : unionParams) {
                    countPs.setObject(paramIdx++, p);
                }
                for (Object p : outerParams) {
                    countPs.setObject(paramIdx++, p);
                }
                try (ResultSet rs = countPs.executeQuery()) {
                    if (rs.next()) {
                        totalOut[0] = rs.getInt(1);
                    }
                }
            }

            try (PreparedStatement ps = conn.prepareStatement(pageSql)) {
                int paramIdx = 1;
                for (Object p : unionParams) {
                    ps.setObject(paramIdx++, p);
                }
                for (Object p : outerParams) {
                    ps.setObject(paramIdx++, p);
                }
                ps.setInt(paramIdx++, limit);
                ps.setInt(paramIdx++, offset);

                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        list.add(mapSummary(rs));
                    }
                }
            }
            return list;
        } catch (SQLException e) {
            throw new EventNotificationDataAccessException(
                    String.format(EventNotificationCommonConstants.ERROR_LISTING_ORG_DELIVERIES, orgId), e);
        }
    }

    @Override
    public Optional<String> getEventPayload(String eventId) {
        if (eventId == null || eventId.trim().isEmpty()) {
            return Optional.empty();
        }
        try (Connection conn = DBUtils.getConnection();
                PreparedStatement ps = conn.prepareStatement(getQueries(conn).getGetEventPayloadQuery())) {
            ps.setString(1, eventId.trim());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.ofNullable(rs.getString("PAYLOAD"));
                }
            }
            return Optional.empty();
        } catch (SQLException e) {
            throw new EventNotificationDataAccessException(
                    String.format(EventNotificationCommonConstants.ERROR_GETTING_EVENT_PAYLOAD, eventId), e);
        }
    }

    @Override
    public Optional<SubscriptionDeliverySummary> getOrgDeliveryById(String orgId, String deliveryId) {
        try (Connection conn = DBUtils.getConnection();
                PreparedStatement ps = conn.prepareStatement(getQueries(conn).getGetOrgDeliveryByIdQuery())) {
            ps.setString(1, orgId);
            ps.setString(2, orgId);
            ps.setString(3, deliveryId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapSummary(rs));
                }
            }
            return Optional.empty();
        } catch (SQLException e) {
            throw new EventNotificationDataAccessException(
                    String.format(EventNotificationCommonConstants.ERROR_GETTING_ORG_DELIVERY, deliveryId), e);
        }
    }

    @Override
    public List<SubscriptionDeliverySummary> listEventDeliveries(String orgId, String eventId, int limit, int offset, int[] totalOut) {
        if (orgId == null || orgId.trim().isEmpty() || eventId == null || eventId.trim().isEmpty()) {
            return Collections.emptyList();
        }
        List<SubscriptionDeliverySummary> list = new ArrayList<>();
        StringBuilder outerWhere = new StringBuilder(" WHERE EVENT_ID = ?");
        List<Object> outerParams = new ArrayList<>(Collections.singletonList(eventId.trim()));

        try (Connection conn = DBUtils.getConnection()) {
            EventNotificationCommonDBQueries queries = getQueries(conn);
            StringBuilder unionSql = new StringBuilder(queries.getGetOrgDeliveriesUnionBaseQuery());
            List<Object> unionParams = new ArrayList<>(Arrays.asList(orgId, orgId));

            String wrappedSql = "SELECT * FROM (" + unionSql + ") AS u" + outerWhere;
            String countSql = "SELECT COUNT(*) FROM (" + wrappedSql + ") AS c";
            String pageSql = wrappedSql + queries.getPaginationClause("DELIVERY_CREATED_AT DESC");

            if (totalOut != null && totalOut.length > 0) {
                try (PreparedStatement countPs = conn.prepareStatement(countSql)) {
                    int paramIdx = 1;
                    for (Object p : unionParams) {
                        countPs.setObject(paramIdx++, p);
                    }
                    for (Object p : outerParams) {
                        countPs.setObject(paramIdx++, p);
                    }
                    try (ResultSet rs = countPs.executeQuery()) {
                        if (rs.next()) {
                            totalOut[0] = rs.getInt(1);
                        }
                    }
                }
            }

            try (PreparedStatement ps = conn.prepareStatement(pageSql)) {
                int paramIdx = 1;
                for (Object p : unionParams) {
                    ps.setObject(paramIdx++, p);
                }
                for (Object p : outerParams) {
                    ps.setObject(paramIdx++, p);
                }
                ps.setInt(paramIdx++, limit);
                ps.setInt(paramIdx++, offset);

                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        list.add(mapSummary(rs));
                    }
                }
            }
            return list;
        } catch (SQLException e) {
            throw new EventNotificationDataAccessException(
                    String.format(EventNotificationCommonConstants.ERROR_LISTING_ORG_DELIVERIES, orgId), e);
        }
    }

    private SubscriptionDeliverySummary mapSummary(ResultSet rs) throws SQLException {
        return new SubscriptionDeliverySummary(
                rs.getString("DELIVERY_ID"),
                rs.getString("EVENT_ID"),
                rs.getString("SUBSCRIPTION_ID"),
                rs.getString("GROUP_ID"),
                rs.getString("TOPIC_NAME"),
                rs.getString("CURRENT_STATUS"),
                rs.getString("DELIVERY_MODE"),
                rs.getTimestamp("OCCURRED_AT"),
                rs.getTimestamp("DELIVERY_CREATED_AT"),
                rs.getString("PAYLOAD"));
    }

    private DPDPConfigurationService getConfiguration() {
        if (configurationService == null) {
            return new org.wso2.dpdp.accelerator.common.config.DPDPConfigurationServiceImpl(false);
        }
        return configurationService;
    }
}
