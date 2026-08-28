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

package org.wso2.dpdp.accelerator.event.notifications.service.dto;

import java.util.List;
import java.util.Map;

/**
 * Request body for {@code POST /events}.
 *
 * <p>Payload is held as a {@code Map<String, Object>} so Jackson can bind
 * arbitrary JSON. The service layer serializes this back to a JSON string
 * before persisting it on the {@code EVENT} row.</p>
 */
public class EventCreateDTO {

    private String topic;
    private List<String> purposes;
    private Map<String, Object> payload;

    public EventCreateDTO() {
    }

    public EventCreateDTO(String topic, List<String> purposes, Map<String, Object> payload) {
        this.topic = topic;
        this.purposes = purposes;
        this.payload = payload;
    }

    public String getTopic() {
        return topic;
    }

    public void setTopic(String topic) {
        this.topic = topic;
    }

    public List<String> getPurposes() {
        return purposes;
    }

    public void setPurposes(List<String> purposes) {
        this.purposes = purposes;
    }

    public Map<String, Object> getPayload() {
        return payload;
    }

    public void setPayload(Map<String, Object> payload) {
        this.payload = payload;
    }
}
