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
import org.wso2.dpdp.accelerator.event.notifications.service.dao.TopicDAO;
import org.wso2.dpdp.accelerator.event.notifications.service.dao.AbstractDAO;
import org.wso2.dpdp.accelerator.event.notifications.service.dao.mapper.TopicRowMapper;
import org.wso2.dpdp.accelerator.event.notifications.service.dao.model.Topic;
import org.wso2.dpdp.accelerator.event.notifications.service.queries.TopicQueries;
import org.wso2.dpdp.accelerator.event.notifications.service.exception.DataAccessException;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class TopicDAOImpl extends AbstractDAO implements TopicDAO {

    @Override
    public boolean addTopic(Topic topic) {
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(TopicQueries.ADD_TOPIC)) {
            ps.setString(1, topic.getTopicId());
            ps.setString(2, topic.getOrgId());
            ps.setString(3, topic.getName());
            ps.setString(4, topic.getDescription());
            ps.setString(5, topic.getStatus());
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            throw new DataAccessException("Failed to persist topic [" + topic.getTopicId() + "]", e);
        }
    }

    @Override
    public Optional<Topic> getTopicById(String topicId) {
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(TopicQueries.GET_TOPIC_BY_ID)) {
            ps.setString(1, topicId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(TopicRowMapper.map(rs));
                }
            }
            return Optional.empty();
        } catch (SQLException e) {
            throw new DataAccessException("Error reading topic [" + topicId + "]", e);
        }
    }

    @Override
    public Optional<Topic> getTopicByOrgAndName(String orgId, String name) {
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(TopicQueries.GET_TOPIC_BY_ORG_AND_NAME)) {
            ps.setString(1, orgId);
            ps.setString(2, name);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(TopicRowMapper.map(rs));
                }
            }
            return Optional.empty();
        } catch (SQLException e) {
            throw new DataAccessException("Error looking up topic by org [" + orgId + "] and name [" + name + "]", e);
        }
    }

    @Override
    public boolean updateTopicStatus(String topicId, String status) {
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(TopicQueries.UPDATE_TOPIC_STATUS)) {
            ps.setString(1, status);
            ps.setString(2, topicId);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            throw new DataAccessException("Error updating topic status for [" + topicId + "]", e);
        }
    }

    @Override
    public PaginatedResult<Topic> listTopics(String orgId, String status, String search, int limit, int offset,
            String sort) {
        List<Topic> topics = new ArrayList<>();
        int total = 0;

        StringBuilder countBase = new StringBuilder("SELECT COUNT(*) FROM TOPIC WHERE ORG_ID = ? ");
        StringBuilder selectSql = new StringBuilder("SELECT TOPIC_ID, ORG_ID, NAME, DESCRIPTION, STATUS FROM TOPIC WHERE ORG_ID = ? ");
        List<Object> params = new ArrayList<>();
        params.add(orgId);

        if (status != null && !status.trim().isEmpty()) {
            String clause = "AND STATUS = ? ";
            countBase.append(clause);
            selectSql.append(clause);
            params.add(status.trim());
        }

        if (search != null && !search.trim().isEmpty()) {
            String searchClause = "AND (LOWER(NAME) LIKE ? OR LOWER(DESCRIPTION) LIKE ? OR LOWER(STATUS) LIKE ?) ";
            countBase.append(searchClause);
            selectSql.append(searchClause);
            String term = "%" + search.trim().toLowerCase() + "%";
            params.add(term);
            params.add(term);
            params.add(term);
        }

        String orderBy = "NAME ASC";
        if (sort != null && !sort.trim().isEmpty()) {
            if ("name".equalsIgnoreCase(sort)) orderBy = "NAME ASC";
            else if ("-name".equalsIgnoreCase(sort)) orderBy = "NAME DESC";
        }
        selectSql.append("ORDER BY ").append(orderBy).append(" LIMIT ? OFFSET ?");

        try (Connection conn = getConnection()) {
            try (PreparedStatement countPs = conn.prepareStatement(countBase.toString())) {
                for (int i = 0; i < params.size(); i++) {
                    countPs.setObject(i + 1, params.get(i));
                }
                try (ResultSet countRs = countPs.executeQuery()) {
                    if (countRs.next()) {
                        total = countRs.getInt(1);
                    }
                }
            }

            try (PreparedStatement ps = conn.prepareStatement(selectSql.toString())) {
                int idx = 1;
                for (Object param : params) {
                    ps.setObject(idx++, param);
                }
                ps.setInt(idx++, limit);
                ps.setInt(idx, offset);

                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        topics.add(TopicRowMapper.map(rs));
                    }
                }
            }
            return new PaginatedResult<>(topics, total);
        } catch (SQLException e) {
            throw new DataAccessException("Error listing topics for org [" + orgId + "]", e);
        }
    }

    @Override
    public boolean hasActiveSubscriptions(String topicId) {
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(TopicQueries.HAS_ACTIVE_SUBSCRIPTIONS)) {
            ps.setString(1, topicId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1) > 0;
                }
            }
            return false;
        } catch (SQLException e) {
            throw new DataAccessException("Error checking active subscriptions for topic [" + topicId + "]", e);
        }
    }

    @Override
    public Map<String, String> getTopicNamesByIds(List<String> topicIds) {
        if (topicIds == null || topicIds.isEmpty()) {
            return Collections.emptyMap();
        }
        // Build: SELECT TOPIC_ID, NAME FROM TOPIC WHERE TOPIC_ID IN (?,?,?)
        String placeholders = String.join(",", Collections.nCopies(topicIds.size(), "?"));
        String sql = "SELECT TOPIC_ID, NAME FROM TOPIC WHERE TOPIC_ID IN (" + placeholders + ")";
        Map<String, String> result = new HashMap<>();
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            for (int i = 0; i < topicIds.size(); i++) {
                ps.setString(i + 1, topicIds.get(i));
            }
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    result.put(rs.getString("TOPIC_ID"), rs.getString("NAME"));
                }
            }
        } catch (SQLException e) {
            throw new DataAccessException("Error batch-fetching topic names", e);
        }
        return result;
    }
}
