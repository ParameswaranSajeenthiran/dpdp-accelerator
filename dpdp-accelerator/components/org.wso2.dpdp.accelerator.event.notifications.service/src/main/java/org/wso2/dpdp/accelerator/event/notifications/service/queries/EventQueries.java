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

public class EventQueries {

    private EventQueries() {
    }

    public static final String ADD_EVENT =
            "INSERT INTO EVENT (EVENT_ID, ORG_ID, GROUP_ID, TOPIC_ID, PAYLOAD, CREATED_AT) VALUES (?, ?, ?, ?, ?, ?)";

    public static final String GET_EVENT_BY_ID =
            "SELECT EVENT_ID, ORG_ID, GROUP_ID, TOPIC_ID, PAYLOAD, CREATED_AT FROM EVENT WHERE EVENT_ID = ?";

    public static final String ADD_EVENT_PURPOSE =
            "INSERT INTO EVENT_PURPOSE (EVENT_ID, PURPOSE_NAME) VALUES (?, ?)";

    public static final String GET_EVENT_PURPOSES =
            "SELECT PURPOSE_NAME FROM EVENT_PURPOSE WHERE EVENT_ID = ?";
}
