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

import org.wso2.dpdp.accelerator.event.notifications.dao.constants.EventNotificationDBColumns;

import java.util.ArrayList;
import java.util.List;

/** Helper builder for dynamic topic search and count queries. */
public class TopicQueryBuilder {

    private final String orgId;
    private String status;
    private String search;
    private String sort;

    public TopicQueryBuilder(String orgId) {
        this.orgId = orgId;
    }

    public TopicQueryBuilder setStatus(String status) {
        this.status = status;
        return this;
    }

    public TopicQueryBuilder setSearch(String search) {
        this.search = search;
        return this;
    }

    public TopicQueryBuilder setSort(String sort) {
        this.sort = sort;
        return this;
    }

    public String resolveSortColumn() {
        if ("-name".equalsIgnoreCase(sort)) {
            return EventNotificationDBColumns.NAME + " DESC";
        } else if ("status".equalsIgnoreCase(sort)) {
            return EventNotificationDBColumns.STATUS + " ASC";
        } else if ("-status".equalsIgnoreCase(sort)) {
            return EventNotificationDBColumns.STATUS + " DESC";
        }
        return EventNotificationDBColumns.NAME + " ASC";
    }

    public QueryResult buildSelectQuery(String paginationClause) {
        StringBuilder sql = new StringBuilder(
                "SELECT " + EventNotificationDBColumns.TOPIC_ID + ", "
                        + EventNotificationDBColumns.ORG_ID + ", "
                        + EventNotificationDBColumns.NAME + ", "
                        + EventNotificationDBColumns.DESCRIPTION + ", "
                        + EventNotificationDBColumns.STATUS + ", "
                        + EventNotificationDBColumns.INITIATED_BY + " FROM TOPIC WHERE "
                        + EventNotificationDBColumns.ORG_ID + " = ?");
        List<Object> parameters = buildWhereClauseAndParameters(sql);
        if (paginationClause != null && !paginationClause.trim().isEmpty()) {
            sql.append(paginationClause);
        }
        return new QueryResult(sql.toString(), parameters);
    }

    public QueryResult buildCountQuery() {
        StringBuilder sql = new StringBuilder("SELECT COUNT(*) FROM TOPIC WHERE "
                + EventNotificationDBColumns.ORG_ID + " = ?");
        List<Object> parameters = buildWhereClauseAndParameters(sql);
        return new QueryResult(sql.toString(), parameters);
    }

    private List<Object> buildWhereClauseAndParameters(StringBuilder sql) {
        List<Object> parameters = new ArrayList<>();
        parameters.add(orgId);

        if (status != null && !status.trim().isEmpty()) {
            sql.append(" AND LOWER(").append(EventNotificationDBColumns.STATUS).append(") = LOWER(?)");
            parameters.add(status.trim());
        }

        if (search != null && !search.trim().isEmpty()) {
            sql.append(" AND (LOWER(").append(EventNotificationDBColumns.TOPIC_ID).append(") LIKE ?")
                    .append(" OR LOWER(").append(EventNotificationDBColumns.NAME).append(") LIKE ?")
                    .append(" OR LOWER(").append(EventNotificationDBColumns.DESCRIPTION).append(") LIKE ?)");
            String term = "%" + QueryBuilderUtils.escapeLikePattern(search.trim()).toLowerCase() + "%";
            parameters.add(term);
            parameters.add(term);
            parameters.add(term);
        }
        return parameters;
    }
}
