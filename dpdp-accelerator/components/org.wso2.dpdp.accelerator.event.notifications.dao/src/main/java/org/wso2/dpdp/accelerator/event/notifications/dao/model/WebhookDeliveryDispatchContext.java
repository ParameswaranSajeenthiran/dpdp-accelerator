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
 * {@code WEBHOOK_DELIVERY} with {@code SUBSCRIPTION} and {@code EVENT} to project
 * the delivery row alongside the URL, shared secret, and JSON payload the worker
 * needs to make a single outbound POST. This avoids the worker making three
 * separate round-trips per delivery.
 */
public class WebhookDeliveryDispatchContext {

    private final WebhookDelivery delivery;
    private final String orgId;
    private final String callbackUrl;
    private final String sharedSecret;
    private final String payload;
    private final Timestamp claimedAt;

    public WebhookDeliveryDispatchContext(WebhookDelivery delivery, String orgId, String callbackUrl,
            String sharedSecret, String payload, Timestamp claimedAt) {
        this.delivery = delivery;
        this.orgId = orgId;
        this.callbackUrl = callbackUrl;
        this.sharedSecret = sharedSecret;
        this.payload = payload;
        this.claimedAt = claimedAt;
    }

    public WebhookDelivery getDelivery() {
        return delivery;
    }

    public String getOrgId() {
        return orgId;
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
}
