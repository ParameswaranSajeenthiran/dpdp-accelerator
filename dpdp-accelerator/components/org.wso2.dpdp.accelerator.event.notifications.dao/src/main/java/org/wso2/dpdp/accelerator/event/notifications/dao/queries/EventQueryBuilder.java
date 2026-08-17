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

import java.util.ArrayList;
import java.util.List;

/**
 * Helper builder for constructing dynamic event search and count queries.
 * Mirrors {@link SubscriptionQueryBuilder} but is scoped to the EVENT table only.
 */
public class EventQueryBuilder {

    private final String orgId;
    private String search;
    private String topic;
    private String status;
    private String groupId;
    private String purposes;

    public EventQueryBuilder(String orgId) {
        this.orgId = orgId;
    }

    public EventQueryBuilder setSearch(String search) {
        this.search = search;
        return this;
    }

    public EventQueryBuilder setTopic(String topic) {
        this.topic = topic;
        return this;
    }

    public EventQueryBuilder setStatus(String status) {
        this.status = status;
        return this;
    }

    public EventQueryBuilder setGroupId(String groupId) {
        this.groupId = groupId;
        return this;
    }

    public EventQueryBuilder setPurposes(String purposes) {
        this.purposes = purposes;
        return this;
    }

    /**
     * Escapes characters that have special meaning inside a SQL LIKE pattern
     * so user input is treated as literal text. Mirrors the helper on
     * {@link SubscriptionQueryBuilder}.
     */
    public static String escapeLikePattern(String text) {
        if (text == null) {
            return "";
        }
        return text.replace("\\", "\\\\")
                   .replace("%", "\\%")
                   .replace("_", "\\_");
    }

    /**
     * Fixed sort column for events. {@code createdAt} is the only timestamp
     * on the EVENT row today so a direction toggle is unnecessary.
     */
    public String resolveSortColumn() {
        return "e.CREATED_AT DESC";
    }

    public QueryResult buildSelectQuery(String baseSelect, String paginationClause) {
        StringBuilder sql = new StringBuilder(baseSelect);
        List<Object> params = buildWhereClauseAndParams(sql);
        if (paginationClause != null && !paginationClause.trim().isEmpty()) {
            sql.append(paginationClause);
        }
        return new QueryResult(sql.toString(), params);
    }

    public QueryResult buildCountQuery(String countSelectBase) {
        StringBuilder sql = new StringBuilder(countSelectBase);
        List<Object> params = buildWhereClauseAndParams(sql);
        return new QueryResult(sql.toString(), params);
    }

    private List<Object> buildWhereClauseAndParams(StringBuilder sql) {
        List<Object> params = new ArrayList<>();
        params.add(orgId);

        if (topic != null && !topic.trim().isEmpty() && !"all".equalsIgnoreCase(topic.trim())) {
            sql.append(" AND LOWER(t.NAME) = ?");
            params.add(topic.trim().toLowerCase());
        }

        if (groupId != null && !groupId.trim().isEmpty()) {
            sql.append(" AND e.GROUP_ID = ?");
            params.add(groupId.trim());
        }

        if (status != null && !status.trim().isEmpty() && !"all".equalsIgnoreCase(status.trim())) {
            sql.append(" AND (EXISTS (SELECT 1 FROM WEBHOOK_DELIVERY wd WHERE wd.EVENT_ID = e.EVENT_ID AND LOWER(wd.STATUS) = ?) "
                    + "OR EXISTS (SELECT 1 FROM POLL_DELIVERY pd WHERE pd.EVENT_ID = e.EVENT_ID AND LOWER(pd.STATUS) = ?))");
            String statusParam = status.trim().toLowerCase();
            params.add(statusParam);
            params.add(statusParam);
        }

        if (purposes != null && !purposes.trim().isEmpty()) {
            String[] tokens = purposes.split(",");
            List<String> valid = new ArrayList<>();
            for (String token : tokens) {
                if (token != null && !token.trim().isEmpty()) {
                    valid.add(token.trim().toLowerCase());
                }
            }
            if (!valid.isEmpty()) {
                sql.append(" AND e.EVENT_ID IN (SELECT ep.EVENT_ID FROM EVENT_PURPOSE ep WHERE LOWER(ep.PURPOSE_NAME) IN (");
                for (int i = 0; i < valid.size(); i++) {
                    sql.append(i == 0 ? "?" : ", ?");
                    params.add(valid.get(i));
                }
                sql.append("))");
            }
        }

        if (search != null && !search.trim().isEmpty()) {
            sql.append(" AND (LOWER(e.EVENT_ID) LIKE ? OR LOWER(e.GROUP_ID) LIKE ? "
                    + "OR LOWER(t.NAME) LIKE ? OR LOWER(e.PAYLOAD) LIKE ?)");
            String term = "%" + escapeLikePattern(search.trim()).toLowerCase() + "%";
            params.add(term);
            params.add(term);
            params.add(term);
            params.add(term);
        }
        return params;
    }

    public static class QueryResult {
        private final String sql;
        private final List<Object> parameters;

        public QueryResult(String sql, List<Object> parameters) {
            this.sql = sql;
            this.parameters = parameters;
        }

        public String getSql() {
            return sql;
        }

        public List<Object> getParameters() {
            return parameters;
        }
    }
}