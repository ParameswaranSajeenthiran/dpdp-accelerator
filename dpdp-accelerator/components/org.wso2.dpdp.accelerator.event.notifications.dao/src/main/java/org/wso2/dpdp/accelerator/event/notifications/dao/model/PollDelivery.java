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

package org.wso2.dpdp.accelerator.event.notifications.dao.model;

import java.sql.Timestamp;

public class PollDelivery {

    private String deliveryId;
    private String subscriptionId;
    private String eventId;
    private String status;
    private String errorCode;
    private String errorDetail;
    private Timestamp createdAt;
    private Timestamp completedAt;

    public PollDelivery() {
    }

    public PollDelivery(String deliveryId, String subscriptionId, String eventId, String status, Timestamp createdAt, Timestamp completedAt) {
        this(deliveryId, subscriptionId, eventId, status, null, createdAt, completedAt);
    }

    public PollDelivery(String deliveryId, String subscriptionId, String eventId, String status, String errorDetail,
            Timestamp createdAt, Timestamp completedAt) {
        this(deliveryId, subscriptionId, eventId, status, null, errorDetail, createdAt, completedAt);
    }

    public PollDelivery(String deliveryId, String subscriptionId, String eventId, String status, String errorCode,
            String errorDetail, Timestamp createdAt, Timestamp completedAt) {
        this.deliveryId = deliveryId;
        this.subscriptionId = subscriptionId;
        this.eventId = eventId;
        this.status = status;
        this.errorCode = errorCode;
        this.errorDetail = errorDetail;
        this.createdAt = createdAt;
        this.completedAt = completedAt;
    }

    public String getDeliveryId() {
        return deliveryId;
    }

    public void setDeliveryId(String deliveryId) {
        this.deliveryId = deliveryId;
    }

    public String getSubscriptionId() {
        return subscriptionId;
    }

    public void setSubscriptionId(String subscriptionId) {
        this.subscriptionId = subscriptionId;
    }

    public String getEventId() {
        return eventId;
    }

    public void setEventId(String eventId) {
        this.eventId = eventId;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getErrorDetail() {

        return errorDetail;
    }

    public String getErrorCode() {

        return errorCode;
    }

    public void setErrorCode(String errorCode) {

        this.errorCode = errorCode;
    }

    public void setErrorDetail(String errorDetail) {

        this.errorDetail = errorDetail;
    }

    public Timestamp getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Timestamp createdAt) {
        this.createdAt = createdAt;
    }

    public Timestamp getCompletedAt() {
        return completedAt;
    }

    public void setCompletedAt(Timestamp completedAt) {
        this.completedAt = completedAt;
    }
}
