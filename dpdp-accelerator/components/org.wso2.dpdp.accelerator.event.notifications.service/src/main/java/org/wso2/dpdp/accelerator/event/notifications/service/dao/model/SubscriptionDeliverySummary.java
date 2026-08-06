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

package org.wso2.dpdp.accelerator.event.notifications.service.dao.model;

import java.sql.Timestamp;

/**
 * Domain projection populated by the combined Webhook / Poll delivery history list queries.
 */
public class SubscriptionDeliverySummary {

    private final String deliveryId;
    private final String eventId;
    private final String subscriptionId;
    private final String topicName;
    private final String currentStatus;
    private final String deliveryMode;
    private final Timestamp occurredAt;
    private final Timestamp createdAt;
    private final String payload;

    public SubscriptionDeliverySummary(String deliveryId, String eventId, String topicName,
            String currentStatus, String deliveryMode, Timestamp occurredAt, Timestamp createdAt) {
        this(deliveryId, eventId, null, topicName, currentStatus, deliveryMode, occurredAt, createdAt, null);
    }

    public SubscriptionDeliverySummary(String deliveryId, String eventId, String subscriptionId, String topicName,
            String currentStatus, String deliveryMode, Timestamp occurredAt, Timestamp createdAt, String payload) {
        this.deliveryId = deliveryId;
        this.eventId = eventId;
        this.subscriptionId = subscriptionId;
        this.topicName = topicName;
        this.currentStatus = currentStatus;
        this.deliveryMode = deliveryMode;
        this.occurredAt = occurredAt;
        this.createdAt = createdAt;
        this.payload = payload;
    }

    public String getDeliveryId() { return deliveryId; }
    public String getEventId() { return eventId; }
    public String getSubscriptionId() { return subscriptionId; }
    public String getTopicName() { return topicName; }
    public String getCurrentStatus() { return currentStatus; }
    public String getDeliveryMode() { return deliveryMode; }
    public Timestamp getOccurredAt() { return occurredAt; }
    public Timestamp getCreatedAt() { return createdAt; }
    public String getPayload() { return payload; }
}
