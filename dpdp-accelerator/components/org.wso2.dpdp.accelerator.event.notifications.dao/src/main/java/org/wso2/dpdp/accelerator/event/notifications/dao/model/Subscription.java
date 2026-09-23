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

import java.util.Collections;

import java.util.ArrayList;

import java.sql.Timestamp;
import java.util.List;

public class Subscription {

    private String subscriptionId;
    private String orgId;
    private String groupId;
    private List<String> topicIds = new ArrayList<>();
    private List<String> topicNames = new ArrayList<>();
    private String purposeFilterMode;
    private List<String> purposes;
    private String purposeSetHash;
    private String deliveryMode;
    private String callbackUrl;
    private String sharedSecret;
    private String status;
    private Timestamp createdAt;
    private Timestamp updatedAt;

    public Subscription() {
    }

    public String getSubscriptionId() {
        return subscriptionId;
    }

    public void setSubscriptionId(String subscriptionId) {
        this.subscriptionId = subscriptionId;
    }

    public String getOrgId() {
        return orgId;
    }

    public void setOrgId(String orgId) {
        this.orgId = orgId;
    }

    public String getGroupId() {
        return groupId;
    }

    public void setGroupId(String groupId) {
        this.groupId = groupId;
    }


    public List<String> getTopicIds() {
        return Collections.unmodifiableList(topicIds);
    }

    public List<String> getTopicNames() {
        return Collections.unmodifiableList(topicNames);
    }

    public void setTopicNames(List<String> topicNames) {
        this.topicNames = new ArrayList<>(topicNames);
    }

    public void setTopicIds(List<String> topicIds) {
        this.topicIds = new ArrayList<>(topicIds);
    }

    public String getPurposeFilterMode() {
        return purposeFilterMode;
    }

    public void setPurposeFilterMode(String purposeFilterMode) {
        this.purposeFilterMode = purposeFilterMode;
    }

    public List<String> getPurposes() {
        return purposes;
    }

    public void setPurposes(List<String> purposes) {
        this.purposes = purposes;
    }

    public String getPurposeSetHash() {
        return purposeSetHash;
    }

    public void setPurposeSetHash(String purposeSetHash) {
        this.purposeSetHash = purposeSetHash;
    }

    public String getDeliveryMode() {
        return deliveryMode;
    }

    public void setDeliveryMode(String deliveryMode) {
        this.deliveryMode = deliveryMode;
    }

    public String getCallbackUrl() {
        return callbackUrl;
    }

    public void setCallbackUrl(String callbackUrl) {
        this.callbackUrl = callbackUrl;
    }

    public String getSharedSecret() {
        return sharedSecret;
    }

    public void setSharedSecret(String sharedSecret) {
        this.sharedSecret = sharedSecret;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
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
