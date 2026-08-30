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

/**
 * Aggregated dispatch context for a single webhook delivery. The DAO joins
 * {@code WEBHOOK_DELIVERY} with {@code SUBSCRIPTION}, {@code EVENT}, and
 * {@code TOPIC} to project the delivery row alongside the URL, shared secret,
 * JSON payload, and the topic the event was published under. The worker uses
 * these fields to build a single outbound POST without further DAO calls.
 */
public class WebhookDeliveryDispatchContext {

    private final WebhookDelivery delivery;
    private final String orgId;
    private final String groupId;
    private final String callbackUrl;
    private final String sharedSecret;
    private final String payload;
    private final Timestamp claimedAt;
    private final String topic;

    public WebhookDeliveryDispatchContext(WebhookDelivery delivery, String orgId, String groupId, String callbackUrl,
            String sharedSecret, String payload, Timestamp claimedAt, String topic) {
        this.delivery = delivery;
        this.orgId = orgId;
        this.groupId = groupId;
        this.callbackUrl = callbackUrl;
        this.sharedSecret = sharedSecret;
        this.payload = payload;
        this.claimedAt = claimedAt;
        this.topic = topic;
    }

    public WebhookDelivery getDelivery() {
        return delivery;
    }

    public String getOrgId() {
        return orgId;
    }

    public String getGroupId() {
        return groupId;
    }

    public String getCallbackUrl() {
        return callbackUrl;
    }

    public String getSharedSecret() {
        return sharedSecret;
    }

    public String getPayload() {
        return payload;
    }

    public Timestamp getClaimedAt() {
        return claimedAt;
    }

    public String getTopic() {
        return topic;
    }
}
