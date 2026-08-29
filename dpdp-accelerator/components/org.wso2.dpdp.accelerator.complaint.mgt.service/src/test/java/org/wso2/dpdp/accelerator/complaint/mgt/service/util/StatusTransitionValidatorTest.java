/*
 * Copyright (c) 2026, WSO2 LLC. (https://www.wso2.com).
 *
 * WSO2 LLC. licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.wso2.dpdp.accelerator.complaint.mgt.service.util;

import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

class StatusTransitionValidatorTest {

    @DataProvider(name = "validTransitions")
    Object[][] validTransitions() {
        return new Object[][] {
                { "OPEN", "IN_PROGRESS" },
                { "OPEN", "WAITING_ON_CLIENT" },
                { "IN_PROGRESS", "WAITING_ON_CLIENT" },
                { "IN_PROGRESS", "RESOLVED" },
                { "WAITING_ON_CLIENT", "AWAITING_INTERNAL_REVIEW" },
                { "AWAITING_INTERNAL_REVIEW", "IN_PROGRESS" },
                { "AWAITING_INTERNAL_REVIEW", "WAITING_ON_CLIENT" },
                { "AWAITING_INTERNAL_REVIEW", "RESOLVED" },
                { "RESOLVED", "AWAITING_INTERNAL_REVIEW" }
        };
    }

    @Test(dataProvider = "validTransitions")
    void allowsDocumentedValidTransitions(String from, String to) {
        assertTrue(StatusTransitionValidator.isValidTransition(from, to));
    }

    @DataProvider(name = "invalidTransitions")
    Object[][] invalidTransitions() {
        return new Object[][] {
                { "OPEN", "RESOLVED" },
                { "OPEN", "AWAITING_INTERNAL_REVIEW" },
                { "OPEN", "OPEN" },
                { "RESOLVED", "OPEN" },
                { "RESOLVED", "IN_PROGRESS" },
                { "RESOLVED", "WAITING_ON_CLIENT" },
                { "RESOLVED", "RESOLVED" },
                { "IN_PROGRESS", "AWAITING_INTERNAL_REVIEW" },
                { "WAITING_ON_CLIENT", "IN_PROGRESS" },
                { "WAITING_ON_CLIENT", "RESOLVED" }
        };
    }

    @Test(dataProvider = "invalidTransitions")
    void rejectsInvalidTransitions(String from, String to) {
        assertFalse(StatusTransitionValidator.isValidTransition(from, to));
    }

    @DataProvider(name = "unknownStatusTransitions")
    Object[][] unknownStatusTransitions() {
        return new Object[][] {
                { "GARBAGE", "OPEN" },
                { "OPEN", "GARBAGE" }
        };
    }

    @Test(dataProvider = "unknownStatusTransitions")
    void rejectsTransitionsInvolvingUnknownStatuses(String from, String to) {
        assertFalse(StatusTransitionValidator.isValidTransition(from, to));
    }

    @Test
    void rejectsNullFromOrToStatus() {
        assertFalse(StatusTransitionValidator.isValidTransition(null, "OPEN"));
        assertFalse(StatusTransitionValidator.isValidTransition("OPEN", null));
    }
}
