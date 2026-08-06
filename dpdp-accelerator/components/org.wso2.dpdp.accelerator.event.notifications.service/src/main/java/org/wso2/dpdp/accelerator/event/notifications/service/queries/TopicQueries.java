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
 
public class TopicQueries {
 
    private TopicQueries() {
    }
 
    public static final String ADD_TOPIC =
            "INSERT INTO TOPIC (TOPIC_ID, ORG_ID, NAME, DESCRIPTION, STATUS) VALUES (?, ?, ?, ?, ?)";

    public static final String GET_TOPIC_BY_ID =
            "SELECT TOPIC_ID, ORG_ID, NAME, DESCRIPTION, STATUS FROM TOPIC WHERE TOPIC_ID = ?";

    public static final String GET_TOPIC_BY_ORG_AND_NAME =
            "SELECT TOPIC_ID, ORG_ID, NAME, DESCRIPTION, STATUS FROM TOPIC WHERE ORG_ID = ? AND NAME = ?";
            
    public static final String UPDATE_TOPIC_STATUS =
            "UPDATE TOPIC SET STATUS = ? WHERE TOPIC_ID = ?";

    public static final String HAS_ACTIVE_SUBSCRIPTIONS =
            "SELECT COUNT(*) FROM SUBSCRIPTION WHERE TOPIC_ID = ? AND LOWER(STATUS) != 'deleted'";
}
