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

package org.wso2.dpdp.accelerator.event.notifications.service.dto;

import java.sql.Timestamp;
import java.util.List;

/** Event payload returned by the short-polling endpoint. */
public class EventPollingEventDTO {

    private String deliveryId;
    private String eventId;
    private String subscriptionId;
    private String topic;
    private String payload;
    private List<String> purposes;
    private Timestamp occurredAt;

    public EventPollingEventDTO() {
    }

    public EventPollingEventDTO(String deliveryId, String eventId, String subscriptionId, String topic,
            String payload, List<String> purposes, Timestamp occurredAt) {
        this.deliveryId = deliveryId;
        this.eventId = eventId;
        this.subscriptionId = subscriptionId;
        this.topic = topic;
        this.payload = payload;
        this.purposes = purposes;
        this.occurredAt = occurredAt;
    }

    public String getDeliveryId() {

        return deliveryId;
    }

    public void setDeliveryId(String deliveryId) {

        this.deliveryId = deliveryId;
    }

    public String getEventId() {

        return eventId;
    }

    public void setEventId(String eventId) {

        this.eventId = eventId;
    }

    public String getSubscriptionId() {

        return subscriptionId;
    }

    public void setSubscriptionId(String subscriptionId) {

        this.subscriptionId = subscriptionId;
    }

    public String getTopic() {

        return topic;
    }

    public void setTopic(String topic) {

        this.topic = topic;
    }

    public String getPayload() {

        return payload;
    }

    public void setPayload(String payload) {

        this.payload = payload;
    }

    public List<String> getPurposes() {

        return purposes;
    }

    public void setPurposes(List<String> purposes) {

        this.purposes = purposes;
    }

    public Timestamp getOccurredAt() {

        return occurredAt;
    }

    public void setOccurredAt(Timestamp occurredAt) {

        this.occurredAt = occurredAt;
    }
}
