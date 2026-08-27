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

import org.wso2.dpdp.accelerator.event.notifications.common.enums.Initiator;
import java.sql.Timestamp;

public class Topic {

    private String topicId;
    private String orgId;
    private String name;
    private String description;
    private String status;
    private String initiatedBy;
    private Timestamp createdAt;
    private Timestamp updatedAt;

    public Topic() {
    }

    public Topic(String topicId, String orgId, String name, String description, String status) {
        this.topicId = topicId;
        this.orgId = orgId;
        this.name = name;
        this.description = description;
        this.status = status;
        this.initiatedBy = Initiator.USER.getValue();
    }

    public Topic(String topicId, String orgId, String name, String description, String status, String initiatedBy) {
        this.topicId = topicId;
        this.orgId = orgId;
        this.name = name;
        this.description = description;
        this.status = status;
        this.initiatedBy = initiatedBy;
    }

    public Topic(String topicId, String orgId, String name, String description, String status, Timestamp createdAt,
            Timestamp updatedAt) {
        this.topicId = topicId;
        this.orgId = orgId;
        this.name = name;
        this.description = description;
        this.status = status;
        this.initiatedBy = Initiator.USER.getValue();
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public Topic(String topicId, String orgId, String name, String description, String status, String initiatedBy,
            Timestamp createdAt, Timestamp updatedAt) {
        this.topicId = topicId;
        this.orgId = orgId;
        this.name = name;
        this.description = description;
        this.status = status;
        this.initiatedBy = initiatedBy;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public String getTopicId() {
        return topicId;
    }

    public void setTopicId(String topicId) {
        this.topicId = topicId;
    }

    public String getOrgId() {
        return orgId;
    }

    public void setOrgId(String orgId) {
        this.orgId = orgId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getInitiatedBy() {
        return initiatedBy;
    }

    public void setInitiatedBy(String initiatedBy) {
        this.initiatedBy = initiatedBy;
    }

    public Timestamp getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Timestamp createdAt) {
        this.createdAt = createdAt;
    }

    public Timestamp getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Timestamp updatedAt) {
        this.updatedAt = updatedAt;
    }
}
