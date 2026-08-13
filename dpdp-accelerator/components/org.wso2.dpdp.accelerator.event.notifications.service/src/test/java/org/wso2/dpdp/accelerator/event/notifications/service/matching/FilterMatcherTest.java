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

import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;
import org.wso2.dpdp.accelerator.event.notifications.common.enums.PurposeFilterMode;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.testng.Assert.assertEquals;

/**
 * Unit tests for {@link FilterMatcher}.
 *
 * <p>Truth table covers all three modes plus the null/blank/unknown fallback
 * to {@code ALL} so fan-out never silently drops a subscription because of
 * a corrupt filter mode.</p>
 */
public class FilterMatcherTest {

    @DataProvider(name = "matchCases")
    public Object[][] matchCases() {
        return new Object[][] {
                // ALL — matches unconditionally.
                { PurposeFilterMode.ALL, Collections.singletonList("marketing"), Collections.singletonList("newsletter"), true },
                { PurposeFilterMode.ALL, Collections.singletonList("marketing"), Collections.emptyList(), true },
                { PurposeFilterMode.ALL, Collections.emptyList(), Collections.singletonList("newsletter"), true },

                // SPECIFIC — matches iff the event carries at least one allowed purpose.
                { PurposeFilterMode.SPECIFIC, Arrays.asList("marketing", "analytics"), Arrays.asList("marketing", "newsletter"), true },
                { PurposeFilterMode.SPECIFIC, Arrays.asList("marketing", "analytics"), Collections.singletonList("newsletter"), false },
                { PurposeFilterMode.SPECIFIC, Collections.singletonList("marketing"), Collections.emptyList(), false },

                // EXCEPT — matches iff the event carries something NOT in the deny-list.
                { PurposeFilterMode.EXCEPT, Collections.singletonList("marketing"), Arrays.asList("marketing", "newsletter"), true },
                { PurposeFilterMode.EXCEPT, Collections.singletonList("marketing"), Collections.singletonList("marketing"), false },
                { PurposeFilterMode.EXCEPT, Collections.singletonList("marketing"), Collections.emptyList(), false },

                // Normalisation: case and ordering must not affect the match.
                { PurposeFilterMode.SPECIFIC, Collections.singletonList("MARKETING"), Collections.singletonList("marketing"), true },
                { PurposeFilterMode.EXCEPT, Collections.singletonList("marketing"), Collections.singletonList("MARKETING"), false },
        };
    }

    @Test(dataProvider = "matchCases")
    public void matches_resolvesAllModes(PurposeFilterMode mode, List<String> subscriptionPurposes,
            List<String> eventPurposes, boolean expected) {
        assertEquals(FilterMatcher.matches(mode, subscriptionPurposes, eventPurposes), expected);
    }

    @Test
    public void matches_nullMode_defaultsToAll() {
        assertEquals(FilterMatcher.matches((PurposeFilterMode) null, Collections.singletonList("marketing"),
                Collections.singletonList("newsletter")), true);
    }

    @Test
    public void matches_stringMode_unknownDefaultsToAll() {
        // Deliberately invalid mode string — should fail open, not throw.
        assertEquals(FilterMatcher.matches("not-a-real-mode", Collections.singletonList("marketing"),
                Collections.singletonList("newsletter")), true);
    }

    @Test
    public void matches_stringMode_blankDefaultsToAll() {
        assertEquals(FilterMatcher.matches("", Collections.singletonList("marketing"),
                Collections.singletonList("newsletter")), true);
    }
}
