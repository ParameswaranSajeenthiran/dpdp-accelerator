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
 * MySQL dialect query provider for DPDP Event Notification Framework.
 */
public class EventNotificationMysqlDBQueries extends EventNotificationCommonDBQueries {

    /**
     * Filters on the stored generated column {@code ACTIVE_NAME = LOWER(?)} so MySQL uses
     * {@code UQ_TOPIC_ORG_ACTIVE_NAME} for a single-row index seek instead of falling back to
     * a range scan over {@code UQ_TOPIC_ORG_ID}. Without this override, {@code LOWER(NAME) = LOWER(?)}
     * bypasses the index, causing InnoDB to gap-lock every topic row in the org — which creates an
     * AB-BA deadlock with concurrent {@code SUBSCRIPTION_TOPIC} inserts (FK_ST_TOPIC shared lock).
     */
    @Override
    public String getActiveTopicByOrgAndNameForUpdateQuery() {
        return "SELECT TOPIC_ID, ORG_ID, NAME, DESCRIPTION, STATUS, INITIATED_BY " +
                "FROM TOPIC WHERE ORG_ID = ? AND ACTIVE_NAME = LOWER(?) AND STATUS = " +
                SQL_TOPIC_ACTIVE + " FOR UPDATE";
    }
}

