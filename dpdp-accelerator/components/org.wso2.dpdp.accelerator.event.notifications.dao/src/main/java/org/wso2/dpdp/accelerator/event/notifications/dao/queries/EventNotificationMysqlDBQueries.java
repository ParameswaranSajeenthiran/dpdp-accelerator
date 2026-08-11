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

    @Override
    public String getGetPendingWebhookDeliveriesQuery() {
        return "SELECT DELIVERY_ID, SUBSCRIPTION_ID, EVENT_ID, STATUS, ATTEMPT_COUNT, NEXT_RETRY_AT, CREATED_AT, UPDATED_AT, DELIVERED_AT " +
               "FROM WEBHOOK_DELIVERY WHERE STATUS = 'pending' AND (NEXT_RETRY_AT IS NULL OR NEXT_RETRY_AT <= CURRENT_TIMESTAMP) " +
               "ORDER BY CREATED_AT ASC LIMIT ?";
    }
}
