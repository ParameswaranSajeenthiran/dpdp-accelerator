/*
 * Copyright (c) 2026, WSO2 LLC. (https://www.wso2.com).
 *
 * WSO2 LLC. licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.wso2.dpdp.accelerator.complaint.mgt.dao.queries;

import org.wso2.dpdp.accelerator.complaint.mgt.dao.constants.ComplaintDBColumns;

import java.util.ArrayList;
import java.util.List;

/** Helper builder for constructing the dynamic COMPLAINT_EVENT search/list and count queries. */
public class ComplaintEventQueryBuilder {

    private final String orgId;
    private final String complaintId;
    private final ComplaintCommonDBQueries queries;
    private Long since;
    private Long until;
    private Boolean isPublic;
    private String order;

    public ComplaintEventQueryBuilder(String orgId, String complaintId, ComplaintCommonDBQueries queries) {
        this.orgId = orgId;
        this.complaintId = complaintId;
        this.queries = queries;
    }

    public ComplaintEventQueryBuilder setSince(Long since) {
        this.since = since;
        return this;
    }

    public ComplaintEventQueryBuilder setUntil(Long until) {
        this.until = until;
        return this;
    }

    public ComplaintEventQueryBuilder setIsPublic(Boolean isPublic) {
        this.isPublic = isPublic;
        return this;
    }

    public ComplaintEventQueryBuilder setOrder(String order) {
        this.order = order;
        return this;
    }

    public QueryResult buildSelectQuery(int limit, int offset) {
        StringBuilder sql = new StringBuilder(queries.getListComplaintEventsBaseQuery());
        List<Object> params = buildWhereClauseAndParams(sql);
        boolean desc = "desc".equalsIgnoreCase(order);
        sql.append("ORDER BY ").append(ComplaintDBColumns.ACTION_TIME).append(desc ? " DESC" : " ASC")
                .append(" LIMIT ? OFFSET ?");
        params.add(limit);
        params.add(offset);
        return new QueryResult(sql.toString(), params);
    }

    public QueryResult buildCountQuery() {
        StringBuilder sql = new StringBuilder(queries.getCountComplaintEventsBaseQuery());
        List<Object> params = buildWhereClauseAndParams(sql);
        return new QueryResult(sql.toString(), params);
    }

    private List<Object> buildWhereClauseAndParams(StringBuilder sql) {
        List<Object> params = new ArrayList<>();
        params.add(orgId);
        params.add(complaintId);

        if (since != null) {
            sql.append("AND ").append(ComplaintDBColumns.ACTION_TIME).append(" > ? ");
            params.add(since);
        }
        if (until != null) {
            sql.append("AND ").append(ComplaintDBColumns.ACTION_TIME).append(" <= ? ");
            params.add(until);
        }
        if (isPublic != null) {
            sql.append("AND ").append(ComplaintDBColumns.IS_PUBLIC).append(" = ? ");
            params.add(isPublic);
        }
        return params;
    }
}
