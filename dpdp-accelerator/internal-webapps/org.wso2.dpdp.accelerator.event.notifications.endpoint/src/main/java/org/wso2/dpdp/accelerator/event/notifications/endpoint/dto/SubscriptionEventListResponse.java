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

public class SubscriptionEventListResponse {

    private List<SubscriptionEventItem> content;
    private long totalElements;
    private int limit;
    private int offset;
    private int count;
    private int page;

    public SubscriptionEventListResponse() {
    }

    public SubscriptionEventListResponse(List<SubscriptionEventItem> content, long totalElements,
            int limit, int offset, int count) {
        this.content = content;
        this.totalElements = totalElements;
        this.limit = limit;
        this.offset = offset;
        this.count = count;
        this.page = limit > 0 ? offset / limit : 0;
    }

    public List<SubscriptionEventItem> getContent() {
        return content;
    }

    public void setContent(List<SubscriptionEventItem> content) {
        this.content = content;
    }

    public long getTotalElements() {
        return totalElements;
    }

    public void setTotalElements(long totalElements) {
        this.totalElements = totalElements;
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

    public int getPage() {
        return page;
    }

    public void setPage(int page) {
        this.page = page;
    }
}
