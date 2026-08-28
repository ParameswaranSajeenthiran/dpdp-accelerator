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

package org.wso2.dpdp.accelerator.event.notifications.common.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import org.wso2.dpdp.accelerator.event.notifications.common.constants.EventNotificationCommonConstants;

/**
 * Enumeration representing the filtering mode applied to event purposes within a subscription.
 */
public enum PurposeFilterMode {
    ALL("all"),
    SPECIFIC("specific"),
    EXCEPT("all_except");

    private final String value;

    PurposeFilterMode(String value) {
        this.value = value;
    }

    @JsonValue
    public String getValue() {
        return value;
    }

    @JsonCreator
    public static PurposeFilterMode fromValue(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        for (PurposeFilterMode mode : PurposeFilterMode.values()) {
            if (mode.value.equalsIgnoreCase(value.trim()) || mode.name().equalsIgnoreCase(value.trim())) {
                return mode;
            }
        }
        throw new IllegalArgumentException(
                String.format(EventNotificationCommonConstants.ERROR_UNKNOWN_ENUM_VALUE, "PurposeFilterMode", value));
    }

    public static PurposeFilterMode fromValueOrDefault(String value, PurposeFilterMode defaultValue) {
        try {
            PurposeFilterMode m = fromValue(value);
            return m != null ? m : defaultValue;
        } catch (IllegalArgumentException e) {
            return defaultValue;
        }
    }
}
