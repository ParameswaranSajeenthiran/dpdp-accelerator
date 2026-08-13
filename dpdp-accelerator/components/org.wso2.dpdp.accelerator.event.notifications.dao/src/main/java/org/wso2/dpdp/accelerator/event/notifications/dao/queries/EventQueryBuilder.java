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

    public EventQueryBuilder(String orgId) {
        this.orgId = orgId;
    }

    public EventQueryBuilder setSearch(String search) {
        this.search = search;
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

        if (search != null && !search.trim().isEmpty()) {
            sql.append(" AND (LOWER(e.EVENT_ID) LIKE ? OR LOWER(e.GROUP_ID) LIKE ? "
                    + "OR LOWER(e.TOPIC_ID) LIKE ? OR LOWER(e.PAYLOAD) LIKE ?)");
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