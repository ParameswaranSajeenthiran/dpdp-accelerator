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

import java.util.List;

public class SubscriptionListResponse {

    private List<SubscriptionResponse> subscriptions;
    private int total;
    private int limit;
    private int offset;
    private int count;

    public SubscriptionListResponse() {
    }

    public SubscriptionListResponse(List<SubscriptionResponse> subscriptions, int total, int limit, int offset,
            int count) {
        this.subscriptions = subscriptions;
        this.total = total;
        this.limit = limit;
        this.offset = offset;
        this.count = count;
    }

    public List<SubscriptionResponse> getSubscriptions() {
        return subscriptions;
    }

    public void setSubscriptions(List<SubscriptionResponse> subscriptions) {
        this.subscriptions = subscriptions;
    }

    public int getTotal() {
        return total;
    }

    public void setTotal(int total) {
        this.total = total;
    }

    public int getLimit() {
        return limit;
    }

    public void setLimit(int limit) {
        this.limit = limit;
    }

    public int getOffset() {
        return offset;
    }

    public void setOffset(int offset) {
        this.offset = offset;
    }

    public int getCount() {
        return count;
    }

    public void setCount(int count) {
        this.count = count;
    }
}
