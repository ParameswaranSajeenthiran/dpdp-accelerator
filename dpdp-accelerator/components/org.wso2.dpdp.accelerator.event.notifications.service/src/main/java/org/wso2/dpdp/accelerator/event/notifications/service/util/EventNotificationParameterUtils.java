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
package org.wso2.dpdp.accelerator.event.notifications.service.util;

import org.wso2.dpdp.accelerator.event.notifications.common.constants.EventNotificationCommonConstants;

import java.util.Locale;

/** Shared normalization for Event Notification service query parameters. */
public final class EventNotificationParameterUtils {

    private EventNotificationParameterUtils() {
    }

    public static int normalizeLimit(int limit) {
        return limit <= 0 ? EventNotificationCommonConstants.DEFAULT_LIMIT
                : Math.min(limit, EventNotificationCommonConstants.MAX_LIMIT);
    }

    public static int normalizeOffset(int offset) {
        return Math.max(offset, 0);
    }

    public static String normalizeStatusFilter(String value) {
        if (value == null || value.trim().isEmpty() || "all".equalsIgnoreCase(value.trim())) {
            return null;
        }
        return value.trim().toLowerCase(Locale.ROOT);
    }
}
