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

package org.wso2.dpdp.accelerator.event.notifications.dao;

import java.util.Collections;
import java.util.List;

public class PaginatedDAOResult<T> {

    private final List<T> items;
    private final int total;

    public PaginatedDAOResult(List<T> items, int total) {
        this.items = items != null ? Collections.unmodifiableList(items) : Collections.<T>emptyList();
        this.total = total;
    }

    public List<T> getItems() {
        return items;
    }

    public int getTotal() {
        return total;
    }
}
