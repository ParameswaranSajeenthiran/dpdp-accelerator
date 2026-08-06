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

@JsonInclude(JsonInclude.Include.NON_NULL)
public class SubscriptionResponse {

    private String subscriptionId;
    private String topic;
    private PurposeFilter filter;
    private DeliveryConfigOut delivery;
    private String status;
    private Long createdAt;
    private Long updatedAt;
    private Boolean alreadyExists;
    private String message;

    public SubscriptionResponse() {
    }

    public SubscriptionResponse(String subscriptionId, String topic, PurposeFilter filter,
            DeliveryConfigOut delivery, String status,
            Long createdAt, Long updatedAt) {
        this.subscriptionId = subscriptionId;
        this.topic = topic;
        this.filter = filter;
        this.delivery = delivery;
        this.status = status;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
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

    public PurposeFilter getFilter() {
        return filter;
    }

    public void setFilter(PurposeFilter filter) {
        this.filter = filter;
    }

    public DeliveryConfigOut getDelivery() {
        return delivery;
    }

    public void setDelivery(DeliveryConfigOut delivery) {
        this.delivery = delivery;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Long getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Long createdAt) {
        this.createdAt = createdAt;
    }

    public Long getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Long updatedAt) {
        this.updatedAt = updatedAt;
    }

    public Boolean getAlreadyExists() {
        return alreadyExists;
    }

    public void setAlreadyExists(Boolean alreadyExists) {
        this.alreadyExists = alreadyExists;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }
}
