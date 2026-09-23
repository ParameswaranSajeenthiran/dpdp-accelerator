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

/**
 * SQLite dialect query provider for DPDP Event Notification Framework.
 */
public class EventNotificationSqliteDBQueries extends EventNotificationCommonDBQueries {

    @Override
    public String getActiveTopicByOrgAndNameForUpdateQuery() {
        return getGetTopicByOrgAndNameQuery();
    }

    @Override
    public String getLockSubscriptionsForTopicsQuery(int count) {
        return "SELECT SUBSCRIPTION_ID, ORG_ID, GROUP_ID, PURPOSE_FILTER_MODE, PURPOSE_SET_HASH, DELIVERY_MODE, " +
               "CALLBACK_URL, SHARED_SECRET, STATUS, CREATED_AT, UPDATED_AT " +
               "FROM SUBSCRIPTION s WHERE ORG_ID = ? AND GROUP_ID = ? AND EXISTS (SELECT 1 FROM SUBSCRIPTION_TOPIC st " +
               "WHERE st.SUBSCRIPTION_ID = s.SUBSCRIPTION_ID AND st.ORG_ID = s.ORG_ID AND st.TOPIC_ID IN (" +
               String.join(",", java.util.Collections.nCopies(count, "?")) + ")) " +
               "AND STATUS IN (" + SQL_SUBSCRIPTION_ACTIVE + ", " + SQL_SUBSCRIPTION_PENDING + ", "
               + SQL_SUBSCRIPTION_STALE + ") ORDER BY SUBSCRIPTION_ID";
    }

    @Override
    public String getActiveSubscriptionsForFanOutQuery() {
        return "SELECT SUBSCRIPTION_ID, ORG_ID, GROUP_ID, PURPOSE_FILTER_MODE, PURPOSE_SET_HASH, " +
               "DELIVERY_MODE, CALLBACK_URL, SHARED_SECRET, STATUS, CREATED_AT, UPDATED_AT " +
               "FROM SUBSCRIPTION s WHERE ORG_ID = ? AND EXISTS (SELECT 1 FROM SUBSCRIPTION_TOPIC st WHERE st.SUBSCRIPTION_ID = s.SUBSCRIPTION_ID AND st.ORG_ID = s.ORG_ID AND st.TOPIC_ID = ?) AND STATUS = " + SQL_SUBSCRIPTION_ACTIVE;
    }

}
