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
 * Helper builder for constructing dynamic subscription search and count queries.
 */
public class SubscriptionQueryBuilder {

    private final String orgId;
    private String status;
    private String search;
    private String purposes;
    private String sort;

    public SubscriptionQueryBuilder(String orgId) {
        this.orgId = orgId;
    }

    public SubscriptionQueryBuilder setStatus(String status) {
        this.status = status;
        return this;
    }

    public SubscriptionQueryBuilder setSearch(String search) {
        this.search = search;
        return this;
    }

    public SubscriptionQueryBuilder setPurposes(String purposes) {
        this.purposes = purposes;
        return this;
    }

    public SubscriptionQueryBuilder setSort(String sort) {
        this.sort = sort;
        return this;
    }

    public static String escapeLikePattern(String text) {
        if (text == null) {
            return "";
        }
        return text.replace("\\", "\\\\")
                   .replace("%", "\\%")
                   .replace("_", "\\_");
    }

    public String resolveSortColumn() {
        if ("updatedAt".equalsIgnoreCase(sort)) {
            return "s.UPDATED_AT ASC";
        } else if ("createdAt".equalsIgnoreCase(sort)) {
            return "s.CREATED_AT ASC";
        } else if ("-createdAt".equalsIgnoreCase(sort)) {
            return "s.CREATED_AT DESC";
        } else {
            return "s.UPDATED_AT DESC";
        }
    }

    public QueryResult buildSelectQuery(String paginationClause) {
        StringBuilder sql = new StringBuilder(
                "SELECT DISTINCT s.SUBSCRIPTION_ID, s.ORG_ID, s.GROUP_ID, s.TOPIC_ID, s.PURPOSE_FILTER_MODE, s.PURPOSE_SET_HASH, " +
                "s.DELIVERY_MODE, s.CALLBACK_URL, s.SHARED_SECRET, s.STATUS, s.CREATED_AT, s.UPDATED_AT " +
                "FROM SUBSCRIPTION s " +
                "LEFT JOIN TOPIC t ON s.TOPIC_ID = t.TOPIC_ID " +
                "LEFT JOIN SUBSCRIPTION_PURPOSE sp ON s.SUBSCRIPTION_ID = sp.SUBSCRIPTION_ID " +
                "WHERE s.ORG_ID = ?"
        );
        List<Object> params = buildWhereClauseAndParams(sql);
        if (paginationClause != null && !paginationClause.trim().isEmpty()) {
            sql.append(paginationClause);
        }
        return new QueryResult(sql.toString(), params);
    }

    public QueryResult buildCountQuery() {
        StringBuilder sql = new StringBuilder(
                "SELECT COUNT(DISTINCT s.SUBSCRIPTION_ID) FROM SUBSCRIPTION s " +
                "LEFT JOIN TOPIC t ON s.TOPIC_ID = t.TOPIC_ID " +
                "LEFT JOIN SUBSCRIPTION_PURPOSE sp ON s.SUBSCRIPTION_ID = sp.SUBSCRIPTION_ID " +
                "WHERE s.ORG_ID = ?"
        );
        List<Object> params = buildWhereClauseAndParams(sql);
        return new QueryResult(sql.toString(), params);
    }

    private List<Object> buildWhereClauseAndParams(StringBuilder sql) {
        List<Object> params = new ArrayList<>();
        params.add(orgId);

        if (status != null && !status.trim().isEmpty()) {
            sql.append(" AND s.STATUS = ?");
            params.add(status.trim());
        }

        if (search != null && !search.trim().isEmpty()) {
            sql.append(" AND (LOWER(s.SUBSCRIPTION_ID) LIKE ? OR LOWER(s.GROUP_ID) LIKE ? OR LOWER(s.STATUS) LIKE ? OR LOWER(s.CALLBACK_URL) LIKE ? OR LOWER(t.NAME) LIKE ? OR LOWER(sp.PURPOSE_NAME) LIKE ?)");
            String term = "%" + escapeLikePattern(search.trim()).toLowerCase() + "%";
            params.add(term);
            params.add(term);
            params.add(term);
            params.add(term);
            params.add(term);
            params.add(term);
        }

        if (purposes != null && !purposes.trim().isEmpty()) {
            String[] purposeArr = purposes.split(",");
            List<String> validPurposes = new ArrayList<>();
            for (String p : purposeArr) {
                if (p != null && !p.trim().isEmpty()) {
                    validPurposes.add(p.trim().toLowerCase());
                }
            }
            if (!validPurposes.isEmpty()) {
                sql.append(" AND EXISTS (SELECT 1 FROM SUBSCRIPTION_PURPOSE sp2 WHERE sp2.SUBSCRIPTION_ID = s.SUBSCRIPTION_ID AND LOWER(sp2.PURPOSE_NAME) IN (");
                for (int i = 0; i < validPurposes.size(); i++) {
                    sql.append(i == 0 ? "?" : ", ?");
                }
                sql.append("))");
                params.addAll(validPurposes);
            }
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
