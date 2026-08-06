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

package org.wso2.dpdp.accelerator.event.notifications.endpoint.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.ArrayList;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class SubscriptionEventHistoryResponse {

    private String deliveryId;
    private String eventId;
    private String topic;
    private String deliveryMode;
    private String currentStatus;
    private long occurredAt;
    private Long nextRetryAt;
    private String completionStatus;
    private String completionEvidence;
    private Object payload;
    private List<SubscriptionDeliveryAttempt> history = new ArrayList<>();

    public SubscriptionEventHistoryResponse() {
    }

    public SubscriptionEventHistoryResponse(String deliveryId, String eventId, String topic,
            String deliveryMode, String currentStatus, long occurredAt) {
        this(deliveryId, eventId, topic, deliveryMode, currentStatus, occurredAt, null);
    }

    public SubscriptionEventHistoryResponse(String deliveryId, String eventId, String topic,
            String deliveryMode, String currentStatus, long occurredAt, Object payload) {
        this.deliveryId = deliveryId;
        this.eventId = eventId;
        this.topic = topic;
        this.deliveryMode = deliveryMode;
        this.currentStatus = currentStatus;
        this.occurredAt = occurredAt;
        this.payload = payload;
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

    public String getTopic() {
        return topic;
    }

    public void setTopic(String topic) {
        this.topic = topic;
    }

    public String getDeliveryMode() {
        return deliveryMode;
    }

    public void setDeliveryMode(String deliveryMode) {
        this.deliveryMode = deliveryMode;
    }

    public String getCurrentStatus() {
        return currentStatus;
    }

    public void setCurrentStatus(String currentStatus) {
        this.currentStatus = currentStatus;
    }

    public long getOccurredAt() {
        return occurredAt;
    }

    public void setOccurredAt(long occurredAt) {
        this.occurredAt = occurredAt;
    }

    public Long getNextRetryAt() {
        return nextRetryAt;
    }

    public void setNextRetryAt(Long nextRetryAt) {
        this.nextRetryAt = nextRetryAt;
    }

    public String getCompletionStatus() {
        return completionStatus;
    }

    public void setCompletionStatus(String completionStatus) {
        this.completionStatus = completionStatus;
    }

    public String getCompletionEvidence() {
        return completionEvidence;
    }

    public void setCompletionEvidence(String completionEvidence) {
        this.completionEvidence = completionEvidence;
    }

    public List<SubscriptionDeliveryAttempt> getHistory() {
        return history;
    }

    public void setHistory(List<SubscriptionDeliveryAttempt> history) {
        this.history = history;
    }

    public Object getPayload() {
        return payload;
    }

    public void setPayload(Object payload) {
        this.payload = payload;
    }
}
