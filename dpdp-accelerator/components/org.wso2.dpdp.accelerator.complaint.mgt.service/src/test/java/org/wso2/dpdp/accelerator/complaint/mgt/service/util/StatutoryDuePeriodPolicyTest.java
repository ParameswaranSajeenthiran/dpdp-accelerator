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

import org.testng.annotations.Test;

import static org.testng.Assert.assertEquals;

/**
 * Outside a real Carbon environment (no dpdp-accelerator.xml on disk), DPDPConfigurationService
 * always falls back to its own default - see DPDPConfigurationServiceImplTest for coverage of the
 * configured-value/validation path itself, which lives entirely in that class now.
 */
class StatutoryDuePeriodPolicyTest {

    @Test
    void defaultsToNinetyDaysWhenNoDpdpAcceleratorXmlIsAvailable() {
        assertEquals(90L * 24 * 60 * 60 * 1000, StatutoryDuePeriodPolicy.getDuePeriodMillis());
    }
}
