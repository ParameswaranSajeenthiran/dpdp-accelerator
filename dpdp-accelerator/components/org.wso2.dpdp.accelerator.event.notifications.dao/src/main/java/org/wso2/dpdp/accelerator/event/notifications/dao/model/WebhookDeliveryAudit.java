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

public class WebhookDeliveryAudit {

    private String auditId;
    private String eventId;
    private String deliveryId;
    private String orgId;
    private String responseCode;
    private Timestamp createdAt;
    private Timestamp attemptAt;

    public WebhookDeliveryAudit() {
    }

    public WebhookDeliveryAudit(String auditId, String eventId, String deliveryId, String orgId, String responseCode, Timestamp createdAt, Timestamp attemptAt) {
        this.auditId = auditId;
        this.eventId = eventId;
        this.deliveryId = deliveryId;
        this.orgId = orgId;
        this.responseCode = responseCode;
        this.createdAt = createdAt;
        this.attemptAt = attemptAt;
    }

    public String getAuditId() {
        return auditId;
    }

    public void setAuditId(String auditId) {
        this.auditId = auditId;
    }

    public String getEventId() {
        return eventId;
    }

    public void setEventId(String eventId) {
        this.eventId = eventId;
    }

    public String getDeliveryId() {
        return deliveryId;
    }

    public void setDeliveryId(String deliveryId) {
        this.deliveryId = deliveryId;
    }

    public String getOrgId() {
        return orgId;
    }

    public void setOrgId(String orgId) {
        this.orgId = orgId;
    }

    public String getResponseCode() {
        return responseCode;
    }

    public void setResponseCode(String responseCode) {
        this.responseCode = responseCode;
    }

    public Timestamp getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Timestamp createdAt) {
        this.createdAt = createdAt;
    }

    public Timestamp getAttemptAt() {
        return attemptAt;
    }

    public void setAttemptAt(Timestamp attemptAt) {
        this.attemptAt = attemptAt;
    }
}
