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

package org.wso2.dpdp.accelerator.event.notifications.service.matching;

import org.wso2.dpdp.accelerator.event.notifications.common.enums.PurposeFilterMode;
import org.wso2.dpdp.accelerator.event.notifications.common.util.PurposeOverlapUtils;

import java.util.List;
import java.util.Set;

/**
 * Decides whether a published event matches a subscription's purpose filter.
 *
 * <p>Semantics:</p>
 * <ul>
 *     <li>{@code ALL} — match unconditionally. Used when the subscription
 *         has no specific purpose filter.</li>
 *     <li>{@code SPECIFIC} — match when the event carries at least one
 *         purpose that is in the subscription's allow-list.</li>
 *     <li>{@code EXCEPT} — match when the event carries at least one
 *         purpose that is NOT in the subscription's deny-list.</li>
 * </ul>
 *
 * <p>Purpose strings are normalised via
 * {@link PurposeOverlapUtils#canonicalize(List)} so casing and ordering
 * differences do not affect the match.</p>
 */
public final class FilterMatcher {

    private FilterMatcher() {
    }

    /**
     * Convenient overload that resolves the string mode via {@link PurposeFilterMode}.
     * A null/blank mode defaults to {@code ALL}. Unknown modes also default to {@code ALL}
     * so a corrupt column never silently rejects every event.
     */
    public static boolean matches(String purposeFilterMode, List<String> subscriptionPurposes,
            List<String> eventPurposes) {
        PurposeFilterMode mode = PurposeFilterMode.fromValueOrDefault(purposeFilterMode, PurposeFilterMode.ALL);
        return matches(mode, subscriptionPurposes, eventPurposes);
    }

    public static boolean matches(PurposeFilterMode mode, List<String> subscriptionPurposes,
            List<String> eventPurposes) {
        if (mode == null || mode == PurposeFilterMode.ALL) {
            return true;
        }

        Set<String> subscriptionSet = PurposeOverlapUtils.canonicalize(subscriptionPurposes);
        Set<String> eventSet = PurposeOverlapUtils.canonicalize(eventPurposes);

        if (mode == PurposeFilterMode.SPECIFIC) {
            // Match iff the event carries at least one purpose from the subscription's allow-list.
            for (String purpose : eventSet) {
                if (subscriptionSet.contains(purpose)) {
                    return true;
                }
            }
            return false;
        }

        if (mode == PurposeFilterMode.EXCEPT) {
            // Match iff the event carries at least one purpose NOT in the subscription's deny-list.
            for (String purpose : eventSet) {
                if (!subscriptionSet.contains(purpose)) {
                    return true;
                }
            }
            return false;
        }

        // Unknown mode — fail open (default to ALL).
        return true;
    }
}
