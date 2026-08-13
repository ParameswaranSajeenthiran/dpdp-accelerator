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
import org.wso2.dpdp.accelerator.event.notifications.common.enums.Initiator;
import org.wso2.dpdp.accelerator.event.notifications.common.enums.TopicStatus;
import org.wso2.dpdp.accelerator.event.notifications.common.exception.EventNotificationDataAccessException;
import org.wso2.dpdp.accelerator.event.notifications.common.exception.EventNotificationDuplicateResourceException;
import org.wso2.dpdp.accelerator.event.notifications.common.exception.EventNotificationInvalidStateException;
import org.wso2.dpdp.accelerator.event.notifications.common.util.DBUtils;
import org.wso2.dpdp.accelerator.event.notifications.dao.PaginatedDAOResult;
import org.wso2.dpdp.accelerator.event.notifications.dao.TopicDAO;
import org.wso2.dpdp.accelerator.event.notifications.dao.model.Topic;
import org.wso2.dpdp.accelerator.event.notifications.dao.queries.EventNotificationCommonDBQueries;
import org.wso2.dpdp.accelerator.event.notifications.dao.queries.EventNotificationQueryFactory;
import org.wso2.dpdp.accelerator.event.notifications.dao.queries.SubscriptionQueryBuilder;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

@Component(service = TopicDAO.class, immediate = true)
public class TopicDAOImpl implements TopicDAO {

    private EventNotificationCommonDBQueries getQueries(Connection conn) {
        return EventNotificationQueryFactory.getQueryProvider(conn);
    }

    private static String escapeLikePattern(String text) {
        return SubscriptionQueryBuilder.escapeLikePattern(text);
    }

    @Override
    public boolean addTopic(Topic topic) {
        Objects.requireNonNull(topic, EventNotificationCommonConstants.ERROR_TOPIC_NULL);
        try (Connection conn = DBUtils.getConnection()) {
            boolean originalAutoCommit = conn.getAutoCommit();
            try {
                conn.setAutoCommit(false);
                try (PreparedStatement checkPs = conn
                        .prepareStatement(getQueries(conn).getGetTopicByOrgAndNameQuery())) {
                    checkPs.setString(1, topic.getOrgId());
                    checkPs.setString(2, topic.getName());
                    try (ResultSet rs = checkPs.executeQuery()) {
                        if (rs.next() && TopicStatus.ACTIVE.getValue().equalsIgnoreCase(rs.getString("STATUS"))) {
                            throw new EventNotificationDuplicateResourceException(
                                    String.format(EventNotificationCommonConstants.ERROR_TOPIC_ALREADY_EXISTS,
                                            topic.getName()));
                        }
                    }
                }

                try (PreparedStatement ps = conn.prepareStatement(getQueries(conn).getAddTopicQuery())) {
                    ps.setString(1, topic.getTopicId());
                    ps.setString(2, topic.getOrgId());
                    ps.setString(3, topic.getName());
                    ps.setString(4, topic.getDescription());
                    ps.setString(5, topic.getStatus());
                    ps.setString(6,
                            topic.getInitiatedBy() != null ? topic.getInitiatedBy() : Initiator.USER.getValue());
                    boolean created = ps.executeUpdate() > 0;
                    conn.commit();
                    return created;
                }
            } catch (SQLException e) {
                try {
                    conn.rollback();
                } catch (SQLException rollbackEx) {
                    e.addSuppressed(rollbackEx);
                }
                if (e.getSQLState() != null && e.getSQLState().startsWith("23")) {
                    throw new EventNotificationDuplicateResourceException(
                            String.format(EventNotificationCommonConstants.ERROR_TOPIC_ALREADY_EXISTS,
                                    topic.getName()), e);
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
                    String.format(EventNotificationCommonConstants.ERROR_ADDING_TOPIC, topic.getName()), e);
        }
    }

    @Override
    public Optional<Topic> getTopicById(String topicId, String orgId) {
        try (Connection conn = DBUtils.getConnection();
                PreparedStatement ps = conn.prepareStatement(getQueries(conn).getGetTopicByIdQuery())) {
            ps.setString(1, topicId);
            ps.setString(2, orgId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapTopic(rs));
                }
            }
            return Optional.empty();
        } catch (SQLException e) {
            throw new EventNotificationDataAccessException(
                    String.format(EventNotificationCommonConstants.ERROR_GETTING_TOPIC_BY_ID, topicId), e);
        }
    }

    @Override
    public Optional<Topic> getTopicByOrgAndName(String orgId, String name) {
        try (Connection conn = DBUtils.getConnection()) {
            return getTopicByOrgAndName(conn, orgId, name);
        } catch (SQLException e) {
            throw new EventNotificationDataAccessException(
                    String.format(EventNotificationCommonConstants.ERROR_GETTING_TOPIC_BY_ORG_AND_NAME, orgId, name), e);
        }
    }

    @Override
    public Optional<Topic> getTopicByOrgAndName(Connection conn, String orgId, String name) {
        if (conn == null) {
            return getTopicByOrgAndName(orgId, name);
        }
        try (PreparedStatement ps = conn.prepareStatement(getQueries(conn).getGetTopicByOrgAndNameQuery())) {
            ps.setString(1, orgId);
            ps.setString(2, name);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapTopic(rs));
                }
            }
            return Optional.empty();
        } catch (SQLException e) {
            throw new EventNotificationDataAccessException(
                    String.format(EventNotificationCommonConstants.ERROR_GETTING_TOPIC_BY_ORG_AND_NAME, orgId, name),
                    e);
        }
    }

    @Override
    public boolean updateTopicStatus(String topicId, String orgId, TopicStatus status) {
        Objects.requireNonNull(status, EventNotificationCommonConstants.ERROR_TOPIC_STATUS_NULL);
        try (Connection conn = DBUtils.getConnection();
                PreparedStatement ps = conn.prepareStatement(getQueries(conn).getUpdateTopicStatusQuery())) {
            ps.setString(1, status.getValue());
            ps.setString(2, topicId);
            ps.setString(3, orgId);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            throw new EventNotificationDataAccessException(
                    String.format(EventNotificationCommonConstants.ERROR_UPDATING_TOPIC_STATUS, topicId), e);
        }
    }

    @Override
    public boolean deregisterTopicAtomic(String topicId, String orgId) {
        try (Connection conn = DBUtils.getConnection()) {
            boolean originalAutoCommit = conn.getAutoCommit();
            try {
                conn.setAutoCommit(false);

                EventNotificationCommonDBQueries queries = getQueries(conn);

                // Lock the topic row so concurrent subscription creates serialize with us.
                try (PreparedStatement topicLockPs = conn
                        .prepareStatement(queries.getLockTopicForSubscriptionQuery())) {
                    topicLockPs.setString(1, topicId);
                    topicLockPs.setString(2, orgId);
                    try (ResultSet rs = topicLockPs.executeQuery()) {
                        if (!rs.next()) {
                            // Topic was deleted between the service-layer pre-check and now.
                            return false;
                        }
                    }
                }

                // Count non-deleted subscriptions under the topic lock.
                long activeCount;
                try (PreparedStatement countPs = conn
                        .prepareStatement(queries.getCountActiveSubscriptionsForTopicQuery())) {
                    countPs.setString(1, orgId);
                    countPs.setString(2, topicId);
                    try (ResultSet rs = countPs.executeQuery()) {
                        activeCount = rs.next() ? rs.getLong(1) : 0;
                    }
                }

                if (activeCount > 0) {
                    throw new EventNotificationInvalidStateException(
                            EventNotificationCommonConstants.ERROR_TOPIC_HAS_ACTIVE_SUBSCRIPTIONS);
                }

                int updated;
                try (PreparedStatement ps = conn.prepareStatement(queries.getUpdateTopicStatusQuery())) {
                    ps.setString(1, TopicStatus.DEREGISTERED.getValue());
                    ps.setString(2, topicId);
                    ps.setString(3, orgId);
                    updated = ps.executeUpdate();
                }

                conn.commit();
                return updated > 0;
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
                    String.format(EventNotificationCommonConstants.ERROR_DEREGISTERING_TOPIC, topicId), e);
        }
    }

    @Override
    public PaginatedDAOResult<Topic> listTopics(String orgId, String status, String search, int limit, int offset,
            String sort) {
        List<Topic> topics = new ArrayList<>();
        StringBuilder sql = new StringBuilder(
                "SELECT TOPIC_ID, ORG_ID, NAME, DESCRIPTION, STATUS, INITIATED_BY FROM TOPIC WHERE ORG_ID = ?");
        StringBuilder countSql = new StringBuilder("SELECT COUNT(*) FROM TOPIC WHERE ORG_ID = ?");

        List<Object> params = new ArrayList<>();
        params.add(orgId);

        if (status != null && !status.trim().isEmpty()) {
            sql.append(" AND STATUS = ?");
            countSql.append(" AND STATUS = ?");
            params.add(status.trim());
        }

        if (search != null && !search.trim().isEmpty()) {
            sql.append(" AND (LOWER(NAME) LIKE ? OR LOWER(DESCRIPTION) LIKE ?)");
            countSql.append(" AND (LOWER(NAME) LIKE ? OR LOWER(DESCRIPTION) LIKE ?)");
            String term = "%" + escapeLikePattern(search.trim()).toLowerCase() + "%";
            params.add(term);
            params.add(term);
        }

        String sortColumn;
        if ("-name".equalsIgnoreCase(sort)) {
            sortColumn = "NAME DESC";
        } else if ("status".equalsIgnoreCase(sort)) {
            sortColumn = "STATUS ASC";
        } else if ("-status".equalsIgnoreCase(sort)) {
            sortColumn = "STATUS DESC";
        } else {
            sortColumn = "NAME ASC";
        }

        int total = 0;
        try (Connection conn = DBUtils.getConnection()) {
            sql.append(getQueries(conn).getPaginationClause(sortColumn));
            try (PreparedStatement countPs = conn.prepareStatement(countSql.toString())) {
                for (int i = 0; i < params.size(); i++) {
                    countPs.setObject(i + 1, params.get(i));
                }
                try (ResultSet rs = countPs.executeQuery()) {
                    if (rs.next()) {
                        total = rs.getInt(1);
                    }
                }
            }

            try (PreparedStatement ps = conn.prepareStatement(sql.toString())) {
                for (int i = 0; i < params.size(); i++) {
                    ps.setObject(i + 1, params.get(i));
                }
                ps.setInt(params.size() + 1, limit);
                ps.setInt(params.size() + 2, offset);

                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        topics.add(mapTopic(rs));
                    }
                }
            }
            return new PaginatedDAOResult<>(topics, total);
        } catch (SQLException e) {
            throw new EventNotificationDataAccessException(
                    String.format(EventNotificationCommonConstants.ERROR_LISTING_TOPICS, orgId), e);
        }
    }

    private Topic mapTopic(ResultSet rs) throws SQLException {
        return new Topic(
                rs.getString("TOPIC_ID"),
                rs.getString("ORG_ID"),
                rs.getString("NAME"),
                rs.getString("DESCRIPTION"),
                rs.getString("STATUS"),
                rs.getString("INITIATED_BY"));
    }
}
