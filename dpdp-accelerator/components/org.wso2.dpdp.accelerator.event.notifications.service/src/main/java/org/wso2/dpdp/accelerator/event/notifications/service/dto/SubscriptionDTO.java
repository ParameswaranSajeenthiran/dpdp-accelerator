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

import org.wso2.dpdp.accelerator.event.notifications.common.enums.SubscriptionStatus;

/**
 * Data Transfer Object representing an Event Subscription at the service layer.
 */
public class SubscriptionDTO {

    private String subscriptionId;
    private String groupId;
    private String topic;
    private FilterDTO filter;
    private DeliveryConfigDTO delivery;
    private SubscriptionStatus status;
    private Long createdAt;
    private Long updatedAt;
    private Boolean alreadyExists;
    private String message;

    public SubscriptionDTO() {
    }

    public SubscriptionDTO(String subscriptionId, String topic, FilterDTO filter, DeliveryConfigDTO delivery,
            SubscriptionStatus status, Long createdAt, Long updatedAt, Boolean alreadyExists, String message) {
        this(subscriptionId, null, topic, filter, delivery, status, createdAt, updatedAt, alreadyExists, message);
    }

    public SubscriptionDTO(String subscriptionId, String groupId, String topic, FilterDTO filter, DeliveryConfigDTO delivery,
            SubscriptionStatus status, Long createdAt, Long updatedAt, Boolean alreadyExists, String message) {
        this.subscriptionId = subscriptionId;
        this.groupId = groupId;
        this.topic = topic;
        this.filter = filter;
        this.delivery = delivery;
        this.status = status;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.alreadyExists = alreadyExists;
        this.message = message;
    }

    public String getSubscriptionId() {
        return subscriptionId;
    }

    public void setSubscriptionId(String subscriptionId) {
        this.subscriptionId = subscriptionId;
    }

    public String getGroupId() {
        return groupId;
    }

    public void setGroupId(String groupId) {
        this.groupId = groupId;
    }

    public String getTopic() {
        return topic;
    }

    public void setTopic(String topic) {
        this.topic = topic;
    }

    public FilterDTO getFilter() {
        return filter;
    }

    public void setFilter(FilterDTO filter) {
        this.filter = filter;
    }

    public DeliveryConfigDTO getDelivery() {
        return delivery;
    }

    public void setDelivery(DeliveryConfigDTO delivery) {
        this.delivery = delivery;
    }

    public SubscriptionStatus getStatus() {
        return status;
    }

    public void setStatus(SubscriptionStatus status) {
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