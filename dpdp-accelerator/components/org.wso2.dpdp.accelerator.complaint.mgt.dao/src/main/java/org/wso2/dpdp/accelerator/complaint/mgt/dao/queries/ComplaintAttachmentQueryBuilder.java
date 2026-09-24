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
import java.util.Collections;
import java.util.List;

/** Helper builder for constructing the dynamic COMPLAINT_ATTACHMENT list queries. */
public class ComplaintAttachmentQueryBuilder {

    private final String orgId;
    private final ComplaintCommonDBQueries queries;
    private List<String> complaintIds = Collections.emptyList();

    public ComplaintAttachmentQueryBuilder(String orgId, ComplaintCommonDBQueries queries) {
        this.orgId = orgId;
        this.queries = queries;
    }

    public ComplaintAttachmentQueryBuilder setComplaintIds(List<String> complaintIds) {
        this.complaintIds = complaintIds != null ? complaintIds : Collections.emptyList();
        return this;
    }

    /**
     * Metadata for every attachment of the given complaints in one round trip, ordered so each
     * complaint's attachments come back oldest first. Callers must not pass an empty id list -
     * {@code IN ()} is not valid SQL.
     */
    public QueryResult buildListMetadataByComplaintsQuery() {
        if (complaintIds.isEmpty()) {
            throw new IllegalStateException("At least one complaint ID is required");
        }
        StringBuilder sql = new StringBuilder(queries.getListAttachmentMetadataBaseQuery());
        List<Object> params = new ArrayList<>();
        params.add(orgId);
        sql.append("AND ").append(ComplaintDBColumns.COMPLAINT_ID).append(" IN (")
                .append(String.join(", ", Collections.nCopies(complaintIds.size(), "?")))
                .append(") ORDER BY ").append(ComplaintDBColumns.COMPLAINT_ID).append(" ASC, ")
                .append(ComplaintDBColumns.CREATED_TIME).append(" ASC");
        params.addAll(complaintIds);
        return new QueryResult(sql.toString(), params);
    }
}
