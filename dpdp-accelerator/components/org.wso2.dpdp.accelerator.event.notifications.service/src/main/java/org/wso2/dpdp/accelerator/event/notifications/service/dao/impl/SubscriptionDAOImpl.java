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

import org.wso2.dpdp.accelerator.event.notifications.service.model.PaginatedResult;
import org.wso2.dpdp.accelerator.event.notifications.service.dao.AbstractDAO;
import org.wso2.dpdp.accelerator.event.notifications.service.dao.SubscriptionDAO;
import org.wso2.dpdp.accelerator.event.notifications.service.exception.DataAccessException;
import org.wso2.dpdp.accelerator.event.notifications.service.dao.mapper.SubscriptionRowMapper;
import org.wso2.dpdp.accelerator.event.notifications.service.dao.model.Subscription;
import org.wso2.dpdp.accelerator.event.notifications.service.queries.SubscriptionQueries;
import org.wso2.dpdp.accelerator.event.notifications.service.queries.SubscriptionQueryBuilder;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * JDBC implementation of {@link SubscriptionDAO} for managing Event Subscription data in the database.
 */
public class SubscriptionDAOImpl extends AbstractDAO implements SubscriptionDAO {

    @Override
    public boolean addSubscription(Subscription subscription) {
        try (Connection conn = getConnection()) {
            conn.setAutoCommit(false);
            try (PreparedStatement ps = conn.prepareStatement(SubscriptionQueries.ADD_SUBSCRIPTION)) {
                Timestamp now = Timestamp.from(Instant.now());
                ps.setString(1, subscription.getSubscriptionId());
                ps.setString(2, subscription.getOrgId());
                ps.setString(3, subscription.getGroupId());
                ps.setString(4, subscription.getTopicId());
                ps.setString(5, subscription.getStatus() != null ? subscription.getStatus() : "active");
                ps.setString(6, subscription.getPurposeFilterMode());
                ps.setString(7, subscription.getCallbackUrl());
                ps.setString(8, subscription.getSharedSecret());
                ps.setTimestamp(9, subscription.getCreatedAt() != null ? subscription.getCreatedAt() : now);
                ps.setTimestamp(10, subscription.getUpdatedAt() != null ? subscription.getUpdatedAt() : now);
                ps.setString(11, subscription.getDeliveryMode());

                int rows = ps.executeUpdate();
                if (rows == 0) {
                    conn.rollback();
                    return false;
                }
                if (subscription.getPurposes() != null && !subscription.getPurposes().isEmpty()) {
                    insertSubscriptionPurposes(conn, subscription.getSubscriptionId(), subscription.getPurposes());
                }
                conn.commit();
                return true;
            } catch (SQLException e) {
                rollback(conn);
                throw e;
            } finally {
                resetAutoCommit(conn);
            }
        } catch (SQLException e) {
            throw new DataAccessException("Failed to persist subscription [" + subscription.getSubscriptionId() + "]", e);
        }
    }

    @Override
    public Optional<Subscription> getSubscriptionById(String subscriptionId, String orgId) {
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(SubscriptionQueries.GET_SUBSCRIPTION_BY_ID_AND_ORG)) {
            ps.setString(1, subscriptionId);
            ps.setString(2, orgId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    Subscription sub = SubscriptionRowMapper.map(rs);
                    sub.setPurposes(getSubscriptionPurposes(subscriptionId));
                    return Optional.of(sub);
                }
            }
            return Optional.empty();
        } catch (SQLException e) {
            throw new DataAccessException("Error getting subscription by ID [" + subscriptionId + "]", e);
        }
    }

    @Override
    public List<Subscription> getActiveSubscriptionsForMatching(String orgId, String groupId, String topicId) {
        List<Subscription> subscriptions = new ArrayList<>();
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(SubscriptionQueries.GET_SUBSCRIPTIONS_FOR_MATCHING)) {
            ps.setString(1, orgId);
            ps.setString(2, groupId);
            ps.setString(3, topicId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    subscriptions.add(SubscriptionRowMapper.map(rs));
                }
            }
            populatePurposesBatch(conn, subscriptions);
            return subscriptions;
        } catch (SQLException e) {
            throw new DataAccessException("Error getting matching subscriptions for topic [" + topicId + "]", e);
        }
    }

    @Override
    public boolean updateSubscriptionStatus(String subscriptionId, String status) {
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(SubscriptionQueries.UPDATE_SUBSCRIPTION_STATUS)) {
            ps.setString(1, status);
            ps.setString(2, subscriptionId);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            throw new DataAccessException("Error updating subscription status for [" + subscriptionId + "]", e);
        }
    }

    private void insertSubscriptionPurposes(Connection conn, String subscriptionId, List<String> purposes)
            throws SQLException {
        if (purposes == null || purposes.isEmpty()) return;
        try (PreparedStatement ps = conn.prepareStatement(SubscriptionQueries.ADD_SUBSCRIPTION_PURPOSE)) {
            for (String purpose : purposes) {
                ps.setString(1, subscriptionId);
                ps.setString(2, purpose);
                ps.addBatch();
            }
            int[] results = ps.executeBatch();
            int inserted = 0;
            for (int r : results) {
                if (r >= 1 || r == PreparedStatement.SUCCESS_NO_INFO) {
                    inserted++;
                }
            }
            if (inserted != purposes.size()) {
                throw new SQLException(
                        "Subscription purpose batch insert incomplete: expected " + purposes.size()
                                + " but driver reported " + inserted);
            }
        }
    }

    private List<String> getSubscriptionPurposes(String subscriptionId) {
        List<String> purposes = new ArrayList<>();
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(SubscriptionQueries.GET_SUBSCRIPTION_PURPOSES)) {
            ps.setString(1, subscriptionId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    purposes.add(rs.getString("PURPOSE_NAME"));
                }
            }
            return purposes;
        } catch (SQLException e) {
            throw new DataAccessException("Error getting subscription purposes for [" + subscriptionId + "]", e);
        }
    }

    @Override
    public PaginatedResult<Subscription> listSubscriptions(String orgId, String status, String purposesStr,
            String search, int limit, int offset, String sort) {
        List<Subscription> list = new ArrayList<>();
        int total = 0;

        SubscriptionQueryBuilder builder = SubscriptionQueryBuilder.build(orgId, status, purposesStr, search, limit,
                offset, sort);

        try (Connection conn = getConnection()) {
            try (PreparedStatement countPs = conn.prepareStatement(builder.getCountSql())) {
                List<Object> params = builder.getParameters();
                for (int i = 0; i < params.size(); i++) {
                    countPs.setObject(i + 1, params.get(i));
                }
                try (ResultSet countRs = countPs.executeQuery()) {
                    if (countRs.next()) {
                        total = countRs.getInt(1);
                    }
                }
            }

            try (PreparedStatement ps = conn.prepareStatement(builder.getSelectSql())) {
                List<Object> params = builder.getParameters();
                int idx = 1;
                for (Object param : params) {
                    ps.setObject(idx++, param);
                }
                ps.setInt(idx++, limit);
                ps.setInt(idx, offset);

                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        list.add(SubscriptionRowMapper.map(rs));
                    }
                }
            }

            populatePurposesBatch(conn, list);
            return new PaginatedResult<>(list, total);
        } catch (SQLException e) {
            throw new DataAccessException("Error listing subscriptions for organization [" + orgId + "]", e);
        }
    }

    private void populatePurposesBatch(Connection conn, List<Subscription> subscriptions) throws SQLException {
        if (subscriptions == null || subscriptions.isEmpty()) return;

        Map<String, Subscription> subMap = new HashMap<>();
        StringBuilder inClause = new StringBuilder();
        for (int i = 0; i < subscriptions.size(); i++) {
            Subscription sub = subscriptions.get(i);
            subMap.put(sub.getSubscriptionId(), sub);
            inClause.append(i == 0 ? "?" : ", ?");
        }

        String sql = SubscriptionQueries.GET_SUBSCRIPTION_PURPOSES_BY_IDS_PREFIX + inClause + ")";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            int idx = 1;
            for (Subscription sub : subscriptions) {
                ps.setString(idx++, sub.getSubscriptionId());
            }
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String subId = rs.getString("SUBSCRIPTION_ID");
                    String purpose = rs.getString("PURPOSE_NAME");
                    Subscription sub = subMap.get(subId);
                    if (sub != null) {
                        if (sub.getPurposes() == null) {
                            sub.setPurposes(new ArrayList<>());
                        }
                        sub.getPurposes().add(purpose);
                    }
                }
            }
        }
    }

    @Override
    public boolean hasPendingOrInFlightDeliveries(String subscriptionId) {
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     SubscriptionQueries.HAS_PENDING_OR_IN_FLIGHT_DELIVERIES_FOR_SUBSCRIPTION)) {
            ps.setString(1, subscriptionId);
            ps.setString(2, subscriptionId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1) > 0;
                }
            }
            return false;
        } catch (SQLException e) {
            throw new DataAccessException(
                    "Error checking pending deliveries for subscription [" + subscriptionId + "]", e);
        }
    }

    @Override
    public Optional<Subscription> findDuplicateSubscription(String orgId, String groupId, String topicId,
            String purposeFilterMode, List<String> sortedPurposes) {
        List<Subscription> activeSubs = getActiveSubscriptionsForMatching(orgId, groupId, topicId);
        for (Subscription sub : activeSubs) {
            if (sub.getPurposeFilterMode().equalsIgnoreCase(purposeFilterMode)) {
                List<String> existingPurposes = sub.getPurposes() != null
                        ? new ArrayList<>(sub.getPurposes())
                        : Collections.emptyList();
                Collections.sort(existingPurposes);
                List<String> targetPurposes = sortedPurposes != null
                        ? new ArrayList<>(sortedPurposes)
                        : Collections.emptyList();
                Collections.sort(targetPurposes);
                if (existingPurposes.equals(targetPurposes)) {
                    return Optional.of(sub);
                }
            }
        }
        return Optional.empty();
    }
}
