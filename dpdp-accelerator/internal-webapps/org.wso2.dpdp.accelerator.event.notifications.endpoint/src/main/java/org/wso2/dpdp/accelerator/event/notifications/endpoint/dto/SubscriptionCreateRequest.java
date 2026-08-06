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

import javax.validation.Valid;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;

public class SubscriptionCreateRequest {

    @NotBlank(message = "topic is required")
    private String topic;

    @NotNull(message = "filter is required")
    @Valid
    private PurposeFilter filter;

    @NotNull(message = "delivery is required")
    @Valid
    private DeliveryConfig delivery;

    public SubscriptionCreateRequest() {
    }

    public SubscriptionCreateRequest(String topic, PurposeFilter filter, DeliveryConfig delivery) {
        this.topic = topic;
        this.filter = filter;
        this.delivery = delivery;
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

    public DeliveryConfig getDelivery() {
        return delivery;
    }

    public void setDelivery(DeliveryConfig delivery) {
        this.delivery = delivery;
    }
}
