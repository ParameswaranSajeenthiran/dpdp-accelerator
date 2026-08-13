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
import org.wso2.dpdp.accelerator.event.notifications.common.constants.EventNotificationCommonConstants;
import org.wso2.dpdp.accelerator.event.notifications.common.enums.DeliveryStatus;
import org.wso2.dpdp.accelerator.event.notifications.common.enums.PollStatus;
import org.wso2.dpdp.accelerator.event.notifications.common.enums.PurposeFilterMode;
import org.wso2.dpdp.accelerator.event.notifications.common.enums.SubscriptionStatus;
import org.wso2.dpdp.accelerator.event.notifications.common.exception.EventNotificationDataAccessException;
import org.wso2.dpdp.accelerator.event.notifications.common.exception.EventNotificationDuplicateResourceException;
import org.wso2.dpdp.accelerator.event.notifications.common.exception.EventNotificationInvalidStateException;
import org.wso2.dpdp.accelerator.event.notifications.common.util.DBUtils;
import org.wso2.dpdp.accelerator.event.notifications.common.util.PurposeOverlapUtils;
import org.wso2.dpdp.accelerator.event.notifications.dao.PaginatedDAOResult;
import org.wso2.dpdp.accelerator.event.notifications.dao.SubscriptionDAO;
import org.wso2.dpdp.accelerator.event.notifications.dao.model.Subscription;
import org.wso2.dpdp.accelerator.event.notifications.dao.queries.EventNotificationCommonDBQueries;
import org.wso2.dpdp.accelerator.event.notifications.dao.queries.EventNotificationQueryFactory;
import org.wso2.dpdp.accelerator.event.notifications.dao.queries.SubscriptionQueryBuilder;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

@Component(service = SubscriptionDAO.class, immediate = true)
public class SubscriptionDAOImpl implements SubscriptionDAO {

    private EventNotificationCommonDBQueries getQueries(Connection conn) {
        return EventNotificationQueryFactory.getQueryProvider(conn);
    }

    private EventNotificationCommonDBQueries getQueries() {
        return EventNotificationQueryFactory.getQueryProvider();
    }

    @Override
    public void addSubscription(Subscription subscription) {
        if (subscription == null) {
            return;
        }
        PurposeFilterMode newMode = PurposeFilterMode.fromValueOrDefault(subscription.getPurposeFilterMode(),
                PurposeFilterMode.ALL);
        Set<String> newSet = PurposeOverlapUtils.canonicalize(subscription.getPurposes());
        String purposeSetHash = PurposeOverlapUtils.computePurposeSetHash(newMode, subscription.getPurposes());
        subscription.setPurposeSetHash(purposeSetHash);

        try (Connection conn = DBUtils.getConnection()) {
            boolean originalAutoCommit = conn.getAutoCommit();
            try {
                conn.setAutoCommit(false);

                EventNotificationCommonDBQueries queries = getQueries(conn);
                try (PreparedStatement topicLockPs = conn
                        .prepareStatement(queries.getLockTopicForSubscriptionQuery())) {
                    topicLockPs.setString(1, subscription.getTopicId());
                    topicLockPs.setString(2, subscription.getOrgId());
                    try (ResultSet rs = topicLockPs.executeQuery()) {
                        // Lock the parent TOPIC row so concurrent subscription creates on the same
                        // topic serialize. SELECT ... FOR UPDATE on SUBSCRIPTION cannot protect the
                        // case where no matching subscription exists yet.
                        rs.next();
                    }
                }

                // Re-check topic status under the same lock. The service-layer pre-check ran before
                // we acquired the row lock, so a concurrent deregisterTopic on the same topic could
                // have committed between then and now. Without this re-check we would happily INSERT
                // a subscription under a deregistered topic.
                try (PreparedStatement statusPs = conn
                        .prepareStatement(queries.getGetTopicStatusForSubscriptionQuery())) {
                    statusPs.setString(1, subscription.getTopicId());
                    statusPs.setString(2, subscription.getOrgId());
                    try (ResultSet rs = statusPs.executeQuery()) {
                        String currentStatus = rs.next() ? rs.getString(1) : null;
                        if (currentStatus == null
                                || !SubscriptionStatus.ACTIVE.getValue().equalsIgnoreCase(currentStatus)) {
                            throw new EventNotificationInvalidStateException(
                                    EventNotificationCommonConstants.ERROR_TOPIC_NOT_ACTIVE);
                        }
                    }
                }

                try (PreparedStatement lockPs = conn.prepareStatement(queries.getLockActiveSubscriptionsQuery())) {
                    lockPs.setString(1, subscription.getOrgId());
                    lockPs.setString(2, subscription.getGroupId());
                    lockPs.setString(3, subscription.getTopicId());
                    try (ResultSet rs = lockPs.executeQuery()) {
                        while (rs.next()) {
                            String existingId = rs.getString("SUBSCRIPTION_ID");
                            String existingModeStr = rs.getString("PURPOSE_FILTER_MODE");
                            PurposeFilterMode existingMode = PurposeFilterMode.fromValueOrDefault(existingModeStr,
                                    PurposeFilterMode.ALL);
                            List<String> existingPurposes = getPurposesBySubscriptionId(existingId, conn);
                            Set<String> existingSet = PurposeOverlapUtils.canonicalize(existingPurposes);
                            if (PurposeOverlapUtils.overlaps(newMode, newSet, existingMode, existingSet)) {
                                throw new EventNotificationDuplicateResourceException(
                                        EventNotificationCommonConstants.ERROR_SUBSCRIPTION_OVERLAPPING_PURPOSES);
                            }
                        }
                    }
                }

                try (PreparedStatement ps = conn.prepareStatement(queries.getAddSubscriptionQuery())) {
                    ps.setString(1, subscription.getSubscriptionId());
                    ps.setString(2, subscription.getOrgId());
                    ps.setString(3, subscription.getGroupId());
                    ps.setString(4, subscription.getTopicId());
                    ps.setString(5, subscription.getPurposeFilterMode());
                    ps.setString(6, subscription.getPurposeSetHash());
                    ps.setString(7, subscription.getDeliveryMode());
                    ps.setString(8, subscription.getCallbackUrl());
                    ps.setString(9, subscription.getSharedSecret());
                    ps.setString(10, subscription.getStatus());
                    ps.executeUpdate();

                    if (subscription.getPurposes() != null && !subscription.getPurposes().isEmpty()) {
                        try (PreparedStatement purposePs = conn
                                .prepareStatement(queries.getAddSubscriptionPurposesQuery())) {
                            for (String purpose : subscription.getPurposes()) {
                                purposePs.setString(1, subscription.getSubscriptionId());
                                purposePs.setString(2, purpose.trim());
                                purposePs.addBatch();
                            }
                            purposePs.executeBatch();
                        }
                    }

                    conn.commit();
                }
            } catch (SQLException e) {
                try {
                    conn.rollback();
                } catch (SQLException rollbackEx) {
                    e.addSuppressed(rollbackEx);
                }
                if (e.getSQLState() != null && e.getSQLState().startsWith("23")) {
                    throw new EventNotificationDuplicateResourceException(
                            EventNotificationCommonConstants.ERROR_DUPLICATE_SUBSCRIPTION, e);
                }
                throw e;
            } finally {
                try {
                    conn.setAutoCommit(originalAutoCommit);
                } catch (SQLException ignored) {
                }
            }
        } catch (SQLException e) {
            if (e.getSQLState() != null && e.getSQLState().startsWith("23")) {
                throw new EventNotificationDuplicateResourceException(
                        EventNotificationCommonConstants.ERROR_DUPLICATE_SUBSCRIPTION, e);
            }
            throw new EventNotificationDataAccessException(
                    String.format(EventNotificationCommonConstants.ERROR_ADDING_SUBSCRIPTION,
                            (subscription != null ? subscription.getSubscriptionId() : "null")),
                    e);
        }
    }

    @Override
    public Optional<Subscription> getSubscriptionById(String subscriptionId, String orgId) {
        try (Connection conn = DBUtils.getConnection();
                PreparedStatement ps = conn.prepareStatement(getQueries(conn).getGetSubscriptionByIdQuery())) {
            ps.setString(1, subscriptionId);
            ps.setString(2, orgId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    Subscription sub = mapSubscription(rs);
                    sub.setPurposes(getPurposesBySubscriptionId(subscriptionId, orgId));
                    return Optional.of(sub);
                }
            }
            return Optional.empty();
        } catch (SQLException e) {
            throw new EventNotificationDataAccessException(
                    String.format(EventNotificationCommonConstants.ERROR_GETTING_SUBSCRIPTION_BY_ID, subscriptionId),
                    e);
        }
    }

    @Override
    public boolean updateSubscriptionStatus(String subscriptionId, String orgId, String status) {
        try (Connection conn = DBUtils.getConnection();
                PreparedStatement ps = conn.prepareStatement(getQueries(conn).getUpdateSubscriptionStatusQuery())) {
            ps.setString(1, status);
            ps.setString(2, subscriptionId);
            ps.setString(3, orgId);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            throw new EventNotificationDataAccessException(
                    String.format(EventNotificationCommonConstants.ERROR_UPDATING_SUBSCRIPTION_STATUS, subscriptionId),
                    e);
        }
    }

    @Override
    public boolean updateSubscriptionStatus(String subscriptionId, String orgId, String expectedStatus,
            String newStatus) {
        if (expectedStatus == null || expectedStatus.trim().isEmpty()) {
            return updateSubscriptionStatus(subscriptionId, orgId, newStatus);
        }
        try (Connection conn = DBUtils.getConnection();
                PreparedStatement ps = conn
                        .prepareStatement(getQueries(conn).getUpdateSubscriptionStatusGuardedQuery())) {
            ps.setString(1, newStatus);
            ps.setString(2, subscriptionId);
            ps.setString(3, orgId);
            ps.setString(4, expectedStatus);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            throw new EventNotificationDataAccessException(
                    String.format(EventNotificationCommonConstants.ERROR_UPDATING_SUBSCRIPTION_STATUS, subscriptionId),
                    e);
        }
    }

    @Override
    public boolean deleteSubscriptionAtomic(String subscriptionId, String orgId, String expectedStatus) {
        try (Connection conn = DBUtils.getConnection();
                PreparedStatement ps = conn.prepareStatement(getQueries(conn).getDeleteSubscriptionAtomicQuery())) {
            ps.setString(1, subscriptionId);
            ps.setString(2, orgId);
            ps.setString(3, expectedStatus);
            ps.setString(4, subscriptionId);
            ps.setString(5, subscriptionId);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            throw new EventNotificationDataAccessException(
                    String.format(EventNotificationCommonConstants.ERROR_DELETING_SUBSCRIPTION, subscriptionId),
                    e);
        }
    }

    @Override
    public PaginatedDAOResult<Subscription> listSubscriptions(String orgId, String status, String purposes,
            String search, int limit, int offset, String sort) {
        List<Subscription> subscriptions = new ArrayList<>();
        SubscriptionQueryBuilder builder = new SubscriptionQueryBuilder(orgId)
                .setStatus(status)
                .setSearch(search)
                .setPurposes(purposes)
                .setSort(sort);

        int total = 0;
        try (Connection conn = DBUtils.getConnection()) {
            EventNotificationCommonDBQueries queries = getQueries(conn);
            String sortColumn = builder.resolveSortColumn();
            SubscriptionQueryBuilder.QueryResult countResult = builder.buildCountQuery();
            SubscriptionQueryBuilder.QueryResult selectResult = builder
                    .buildSelectQuery(queries.getPaginationClause(sortColumn));

            try (PreparedStatement countPs = conn.prepareStatement(countResult.getSql())) {
                List<Object> countParams = countResult.getParameters();
                for (int i = 0; i < countParams.size(); i++) {
                    countPs.setObject(i + 1, countParams.get(i));
                }
                try (ResultSet rs = countPs.executeQuery()) {
                    if (rs.next()) {
                        total = rs.getInt(1);
                    }
                }
            }

            try (PreparedStatement ps = conn.prepareStatement(selectResult.getSql())) {
                List<Object> selectParams = selectResult.getParameters();
                for (int i = 0; i < selectParams.size(); i++) {
                    ps.setObject(i + 1, selectParams.get(i));
                }
                ps.setInt(selectParams.size() + 1, limit);
                ps.setInt(selectParams.size() + 2, offset);

                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        subscriptions.add(mapSubscription(rs));
                    }
                }
            }

            if (!subscriptions.isEmpty()) {
                List<String> subIds = new ArrayList<>();
                for (Subscription s : subscriptions) {
                    subIds.add(s.getSubscriptionId());
                }
                Map<String, List<String>> purposeMap = getPurposesBySubscriptionIds(subIds);
                for (Subscription s : subscriptions) {
                    s.setPurposes(purposeMap.getOrDefault(s.getSubscriptionId(), Collections.emptyList()));
                }
            }

            return new PaginatedDAOResult<>(subscriptions, total);
        } catch (SQLException e) {
            throw new EventNotificationDataAccessException(
                    String.format(EventNotificationCommonConstants.ERROR_LISTING_SUBSCRIPTIONS, orgId), e);
        }
    }

    @Override
    public List<Subscription> getSubscriptionsByOrgAndTopic(Connection conn, String orgId, String topicId) {
        if (conn == null) {
            return SubscriptionDAO.super.getSubscriptionsByOrgAndTopic(orgId, topicId);
        }
        return getSubscriptionsByOrgAndTopic(conn, orgId, topicId, SubscriptionStatus.ACTIVE.getValue());
    }

    @Override
    public List<Subscription> getLiveSubscriptionsByOrgAndTopic(Connection conn, String orgId, String topicId) {
        List<Subscription> list = new ArrayList<>();
        try (PreparedStatement ps = conn
                .prepareStatement(getQueries(conn).getGetLiveSubscriptionsByOrgAndTopicQuery())) {
            ps.setString(1, orgId);
            ps.setString(2, topicId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(mapSubscription(rs));
                }
            }
            if (!list.isEmpty()) {
                List<String> subIds = new ArrayList<>();
                for (Subscription s : list) {
                    subIds.add(s.getSubscriptionId());
                }
                Map<String, List<String>> purposeMap = getPurposesBySubscriptionIds(conn, subIds);
                for (Subscription s : list) {
                    s.setPurposes(purposeMap.getOrDefault(s.getSubscriptionId(), Collections.emptyList()));
                }
            }
            return list;
        } catch (SQLException e) {
            throw new EventNotificationDataAccessException(
                    String.format(EventNotificationCommonConstants.ERROR_GETTING_SUBSCRIPTIONS_BY_ORG_AND_TOPIC, orgId,
                            topicId),
                    e);
        }
    }

    @Override
    public List<Subscription> getSubscriptionsByOrgAndTopic(String orgId, String topicId, String status) {
        try (Connection conn = DBUtils.getConnection()) {
            return getSubscriptionsByOrgAndTopic(conn, orgId, topicId, status);
        } catch (SQLException e) {
            throw new EventNotificationDataAccessException(
                    String.format(EventNotificationCommonConstants.ERROR_GETTING_SUBSCRIPTIONS_BY_ORG_AND_TOPIC, orgId,
                            topicId),
                    e);
        }
    }

    public List<Subscription> getSubscriptionsByOrgAndTopic(Connection conn, String orgId, String topicId, String status) {
        List<Subscription> list = new ArrayList<>();
        String targetStatus = (status != null && !status.trim().isEmpty()) ? status.trim()
                : SubscriptionStatus.ACTIVE.getValue();
        try (PreparedStatement ps = conn.prepareStatement(getQueries(conn).getGetSubscriptionsByOrgAndTopicQuery())) {
            ps.setString(1, orgId);
            ps.setString(2, topicId);
            ps.setString(3, targetStatus);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(mapSubscription(rs));
                }
            }

            if (!list.isEmpty()) {
                List<String> subIds = new ArrayList<>();
                for (Subscription s : list) {
                    subIds.add(s.getSubscriptionId());
                }
                Map<String, List<String>> purposeMap = getPurposesBySubscriptionIds(conn, subIds);
                for (Subscription s : list) {
                    s.setPurposes(purposeMap.getOrDefault(s.getSubscriptionId(), Collections.emptyList()));
                }
            }
            return list;
        } catch (SQLException e) {
            throw new EventNotificationDataAccessException(
                    String.format(EventNotificationCommonConstants.ERROR_GETTING_SUBSCRIPTIONS_BY_ORG_AND_TOPIC, orgId,
                            topicId),
                    e);
        }
    }

    @Override
    public List<String> getPurposesBySubscriptionId(String subscriptionId, String orgId) {
        List<String> purposes = new ArrayList<>();
        try (Connection conn = DBUtils.getConnection();
                PreparedStatement ps = conn.prepareStatement(getQueries(conn).getGetSubscriptionPurposesQuery())) {
            ps.setString(1, subscriptionId);
            ps.setString(2, orgId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    purposes.add(rs.getString("PURPOSE_NAME"));
                }
            }
            return purposes;
        } catch (SQLException e) {
            throw new EventNotificationDataAccessException(
                    String.format(EventNotificationCommonConstants.ERROR_GETTING_PURPOSES_BY_SUBSCRIPTION_ID,
                            subscriptionId),
                    e);
        }
    }

    @Override
    public long countActiveSubscriptionsForTopic(String orgId, String topicId) {
        try (Connection conn = DBUtils.getConnection();
                PreparedStatement ps = conn
                        .prepareStatement(getQueries(conn).getCountActiveSubscriptionsForTopicQuery())) {
            ps.setString(1, orgId);
            ps.setString(2, topicId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getLong(1);
                }
            }
            return 0;
        } catch (SQLException e) {
            throw new EventNotificationDataAccessException(
                    String.format(EventNotificationCommonConstants.ERROR_GETTING_SUBSCRIPTIONS_BY_ORG_AND_TOPIC, orgId,
                            topicId),
                    e);
        }
    }

    @Override
    public Map<String, List<String>> getPurposesBySubscriptionIds(List<String> subscriptionIds) {
        try (Connection conn = DBUtils.getConnection()) {
            return getPurposesBySubscriptionIds(conn, subscriptionIds);
        } catch (SQLException e) {
            throw new EventNotificationDataAccessException(
                    EventNotificationCommonConstants.ERROR_GETTING_PURPOSES_BY_BATCH_SUBSCRIPTION_IDS, e);
        }
    }

    public Map<String, List<String>> getPurposesBySubscriptionIds(Connection conn, List<String> subscriptionIds) {
        if (subscriptionIds == null || subscriptionIds.isEmpty()) {
            return Collections.emptyMap();
        }

        Map<String, List<String>> map = new HashMap<>();
        String placeholders = String.join(",", Collections.nCopies(subscriptionIds.size(), "?"));

        try {
            String sql = String.format(getQueries(conn).getGetSubscriptionPurposesByIdsTemplate(), placeholders);
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                for (int i = 0; i < subscriptionIds.size(); i++) {
                    ps.setString(i + 1, subscriptionIds.get(i));
                }
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        String subId = rs.getString("SUBSCRIPTION_ID");
                        String purpose = rs.getString("PURPOSE_NAME");
                        map.computeIfAbsent(subId, k -> new ArrayList<>()).add(purpose);
                    }
                }
                return map;
            }
        } catch (SQLException e) {
            throw new EventNotificationDataAccessException(
                    EventNotificationCommonConstants.ERROR_GETTING_PURPOSES_BY_BATCH_SUBSCRIPTION_IDS, e);
        }
    }

    @Override
    public boolean hasPendingOrInFlightDeliveries(String subscriptionId, String orgId) {
        try (Connection conn = DBUtils.getConnection();
                PreparedStatement ps = conn
                        .prepareStatement(getQueries(conn).getHasPendingOrInFlightDeliveriesForSubscriptionQuery())) {
            ps.setString(1, subscriptionId);
            ps.setString(2, orgId);
            ps.setString(3, DeliveryStatus.PENDING.getValue());
            ps.setString(4, DeliveryStatus.IN_FLIGHT.getValue());
            ps.setString(5, subscriptionId);
            ps.setString(6, orgId);
            ps.setString(7, PollStatus.PENDING.getValue());
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException e) {
            throw new EventNotificationDataAccessException(
                    String.format(EventNotificationCommonConstants.ERROR_CHECKING_PENDING_DELIVERIES_FOR_SUBSCRIPTION,
                            subscriptionId),
                    e);
        }
    }

    @Override
    public List<Subscription> getPendingSubscriptionsForRecovery(Timestamp updatedBefore, int limit) {
        List<Subscription> list = new ArrayList<>();
        try (Connection conn = DBUtils.getConnection();
                PreparedStatement ps = conn
                        .prepareStatement(getQueries(conn).getGetPendingSubscriptionsForRecoveryQuery())) {
            ps.setTimestamp(1, updatedBefore);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next() && list.size() < limit) {
                    Subscription sub = mapSubscription(rs);
                    sub.setPurposes(getPurposesBySubscriptionId(sub.getSubscriptionId(), sub.getOrgId()));
                    list.add(sub);
                }
            }
            return list;
        } catch (SQLException e) {
            throw new EventNotificationDataAccessException(
                    EventNotificationCommonConstants.ERROR_GETTING_PENDING_SUBSCRIPTIONS_FOR_RECOVERY, e);
        }
    }

    private List<String> getPurposesBySubscriptionId(String subscriptionId, Connection conn) throws SQLException {
        List<String> purposes = new ArrayList<>();
        try (PreparedStatement ps = conn
                .prepareStatement(getQueries(conn).getGetPurposesBySubscriptionIdWithoutOrgIdQuery())) {
            ps.setString(1, subscriptionId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    purposes.add(rs.getString("PURPOSE_NAME"));
                }
            }
        }
        return purposes;
    }

    private Subscription mapSubscription(ResultSet rs) throws SQLException {
        return new Subscription(
                rs.getString("SUBSCRIPTION_ID"),
                rs.getString("ORG_ID"),
                rs.getString("GROUP_ID"),
                rs.getString("TOPIC_ID"),
                rs.getString("PURPOSE_FILTER_MODE"),
                null,
                rs.getString("PURPOSE_SET_HASH"),
                rs.getString("DELIVERY_MODE"),
                rs.getString("CALLBACK_URL"),
                rs.getString("SHARED_SECRET"),
                rs.getString("STATUS"),
                rs.getTimestamp("CREATED_AT"),
                rs.getTimestamp("UPDATED_AT"));
    }
}
