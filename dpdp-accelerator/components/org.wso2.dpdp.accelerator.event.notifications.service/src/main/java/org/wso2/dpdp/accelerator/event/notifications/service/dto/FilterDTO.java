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

package org.wso2.dpdp.accelerator.event.notifications.service.dto;

import org.wso2.dpdp.accelerator.event.notifications.service.enums.PurposeFilterMode;

import java.util.List;

/**
 * Data Transfer Object representing purpose filtering criteria for a
 * subscription.
 */
public class FilterDTO {

    private PurposeFilterMode type;
    private List<String> purposes;

    public FilterDTO() {
    }

    public FilterDTO(PurposeFilterMode type, List<String> purposes) {
        this.type = type;
        this.purposes = purposes;
    }

    public PurposeFilterMode getType() {
        return type;
    }

    public void setType(PurposeFilterMode type) {
        this.type = type;
    }

    public List<String> getPurposes() {
        return purposes;
    }

    public void setPurposes(List<String> purposes) {
        this.purposes = purposes;
    }
}