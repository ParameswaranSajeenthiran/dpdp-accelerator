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

package org.wso2.dpdp.accelerator.event.notifications.service.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Enumeration representing the delivery mode for an event subscription.
 */
public enum DeliveryMode {
    WEBHOOK("webhook"),
    POLL("poll");

    private final String value;

    DeliveryMode(String value) {
        this.value = value;
    }

    @JsonValue
    public String getValue() {
        return value;
    }

    @JsonCreator
    public static DeliveryMode fromValue(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        for (DeliveryMode mode : DeliveryMode.values()) {
            if (mode.value.equalsIgnoreCase(value.trim()) || mode.name().equalsIgnoreCase(value.trim())) {
                return mode;
            }
        }
        throw new IllegalArgumentException("Unknown DeliveryMode: " + value);
    }

    public static DeliveryMode fromValueOrDefault(String value, DeliveryMode defaultValue) {
        try {
            DeliveryMode m = fromValue(value);
            return m != null ? m : defaultValue;
        } catch (IllegalArgumentException e) {
            return defaultValue;
        }
    }
}
