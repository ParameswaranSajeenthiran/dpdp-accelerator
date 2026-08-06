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

package org.wso2.dpdp.accelerator.event.notifications.service.queries;

import java.util.ArrayList;
import java.util.List;

public class SubscriptionQueryBuilder {

    private final String countSql;
    private final String selectSql;
    private final List<Object> parameters;

    private SubscriptionQueryBuilder(String countSql, String selectSql, List<Object> parameters) {
        this.countSql = countSql;
        this.selectSql = selectSql;
        this.parameters = parameters;
    }

    public String getCountSql() {
        return countSql;
    }

    public String getSelectSql() {
        return selectSql;
    }

    public List<Object> getParameters() {
        return parameters;
    }

    public static SubscriptionQueryBuilder build(String orgId, String status, String purposesStr,
                                                 String search, int limit, int offset, String sort) {
        List<Object> params = new ArrayList<>();
        StringBuilder whereClause = new StringBuilder(" WHERE ORG_ID = ? ");
        params.add(orgId);

        if (status != null && !status.trim().isEmpty() && !"all".equalsIgnoreCase(status.trim())) {
            whereClause.append("AND LOWER(STATUS) = ? ");
            params.add(status.trim().toLowerCase());
        }

        if (purposesStr != null && !purposesStr.trim().isEmpty()) {
            String[] purposes = purposesStr.split(",");
            StringBuilder purposeSubClause = new StringBuilder("AND SUBSCRIPTION_ID IN (SELECT SUBSCRIPTION_ID FROM SUBSCRIPTION_PURPOSE WHERE ");
            for (int i = 0; i < purposes.length; i++) {
                if (i > 0) {
                    purposeSubClause.append(" OR ");
                }
                purposeSubClause.append("LOWER(PURPOSE_NAME) = ?");
                params.add(purposes[i].trim().toLowerCase());
            }
            purposeSubClause.append(") ");
            whereClause.append(purposeSubClause.toString());
        }

        if (search != null && !search.trim().isEmpty()) {
            whereClause.append("AND (LOWER(SUBSCRIPTION_ID) LIKE ? OR LOWER(GROUP_ID) LIKE ? OR LOWER(TOPIC_ID) LIKE ? OR LOWER(CALLBACK_URL) LIKE ?) ");
            String term = "%" + search.trim().toLowerCase() + "%";
            params.add(term);
            params.add(term);
            params.add(term);
            params.add(term);
        }

        String countQuery = "SELECT COUNT(*) FROM SUBSCRIPTION" + whereClause.toString();

        String orderBy = "ORDER BY CREATED_AT DESC";
        if (sort != null && !sort.trim().isEmpty()) {
            if ("asc".equalsIgnoreCase(sort.trim()) || "created_at:asc".equalsIgnoreCase(sort.trim())) {
                orderBy = "ORDER BY CREATED_AT ASC";
            }
        }

        String selectQuery = "SELECT SUBSCRIPTION_ID, ORG_ID, GROUP_ID, TOPIC_ID, STATUS, PURPOSE_FILTER_MODE, " +
                "CALLBACK_URL, SHARED_SECRET, CREATED_AT, UPDATED_AT, DELIVERY_MODE FROM SUBSCRIPTION" +
                whereClause.toString() + " " + orderBy + " LIMIT ? OFFSET ?";

        return new SubscriptionQueryBuilder(countQuery, selectQuery, params);
    }
}
