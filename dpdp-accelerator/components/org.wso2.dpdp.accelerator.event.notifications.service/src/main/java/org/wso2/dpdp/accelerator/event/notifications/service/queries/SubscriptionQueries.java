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

public class SubscriptionQueries {

    private SubscriptionQueries() {
    }

    public static final String ADD_SUBSCRIPTION =
            "INSERT INTO SUBSCRIPTION (SUBSCRIPTION_ID, ORG_ID, GROUP_ID, TOPIC_ID, STATUS, PURPOSE_FILTER_MODE, " +
            "CALLBACK_URL, SHARED_SECRET, CREATED_AT, UPDATED_AT, DELIVERY_MODE) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

    public static final String GET_SUBSCRIPTION_BY_ID =
            "SELECT SUBSCRIPTION_ID, ORG_ID, GROUP_ID, TOPIC_ID, STATUS, PURPOSE_FILTER_MODE, " +
            "CALLBACK_URL, SHARED_SECRET, CREATED_AT, UPDATED_AT, DELIVERY_MODE FROM SUBSCRIPTION WHERE SUBSCRIPTION_ID = ?";

    public static final String GET_SUBSCRIPTION_BY_ID_AND_ORG =
            "SELECT SUBSCRIPTION_ID, ORG_ID, GROUP_ID, TOPIC_ID, STATUS, PURPOSE_FILTER_MODE, " +
            "CALLBACK_URL, SHARED_SECRET, CREATED_AT, UPDATED_AT, DELIVERY_MODE FROM SUBSCRIPTION " +
            "WHERE SUBSCRIPTION_ID = ? AND ORG_ID = ?";

    public static final String GET_SUBSCRIPTIONS_FOR_MATCHING =
            "SELECT SUBSCRIPTION_ID, ORG_ID, GROUP_ID, TOPIC_ID, STATUS, PURPOSE_FILTER_MODE, " +
            "CALLBACK_URL, SHARED_SECRET, CREATED_AT, UPDATED_AT, DELIVERY_MODE FROM SUBSCRIPTION " +
            "WHERE ORG_ID = ? AND GROUP_ID = ? AND TOPIC_ID = ? AND STATUS IN ('active', 'stale', 'pending')";

    public static final String UPDATE_SUBSCRIPTION_STATUS =
            "UPDATE SUBSCRIPTION SET STATUS = ?, UPDATED_AT = CURRENT_TIMESTAMP WHERE SUBSCRIPTION_ID = ?";

    public static final String ADD_SUBSCRIPTION_PURPOSE =
            "INSERT INTO SUBSCRIPTION_PURPOSE (SUBSCRIPTION_ID, PURPOSE_NAME) VALUES (?, ?)";

    public static final String GET_SUBSCRIPTION_PURPOSES =
            "SELECT PURPOSE_NAME FROM SUBSCRIPTION_PURPOSE WHERE SUBSCRIPTION_ID = ?";

    public static final String GET_SUBSCRIPTION_PURPOSES_BY_IDS_PREFIX =
            "SELECT SUBSCRIPTION_ID, PURPOSE_NAME FROM SUBSCRIPTION_PURPOSE WHERE SUBSCRIPTION_ID IN (";

    public static final String HAS_PENDING_OR_IN_FLIGHT_DELIVERIES_FOR_SUBSCRIPTION =
            "SELECT COUNT(*) FROM (" +
            "SELECT DELIVERY_ID FROM WEBHOOK_DELIVERY WHERE SUBSCRIPTION_ID = ? AND STATUS IN ('pending', 'in_flight') " +
            "UNION ALL " +
            "SELECT DELIVERY_ID FROM POLL_DELIVERY WHERE SUBSCRIPTION_ID = ? AND STATUS = 'pending'" +
            ") AS pending_count";

    public static final String GET_ACTIVE_PULL_SUBSCRIPTION_BY_GROUP =
            "SELECT SUBSCRIPTION_ID, ORG_ID, GROUP_ID, TOPIC_ID, STATUS, PURPOSE_FILTER_MODE, " +
            "CALLBACK_URL, SHARED_SECRET, CREATED_AT, UPDATED_AT, DELIVERY_MODE FROM SUBSCRIPTION " +
            "WHERE ORG_ID = ? AND GROUP_ID = ? AND STATUS = 'active' AND LOWER(DELIVERY_MODE) = 'poll' LIMIT 1";
}
