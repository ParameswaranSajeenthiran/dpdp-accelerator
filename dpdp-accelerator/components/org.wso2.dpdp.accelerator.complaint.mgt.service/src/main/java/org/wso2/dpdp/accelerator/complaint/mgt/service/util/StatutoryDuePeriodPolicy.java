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

import org.wso2.dpdp.accelerator.complaint.mgt.service.internal.ComplaintServiceDataHolder;

/**
 * Statutory due period for grievance redressal under the DPDP Act. Configurable via
 * deployment.toml's [dpdp_accelerator.complaints] statutory_due_period_days, the same way
 * event notification settings are read - see the DPDPConfigurationService OSGi reference bound
 * into {@link ComplaintServiceDataHolder} by {@code ComplaintServiceComponent}, templated into
 * dpdp-accelerator.xml at server startup. Defaults to 90 days if unset.
 */
public class StatutoryDuePeriodPolicy {

    private StatutoryDuePeriodPolicy() {
    }

    public static long getDuePeriodMillis() {
        int days = ComplaintServiceDataHolder.getInstance().getConfigurationService()
                .getComplaintsStatutoryDuePeriodDays();
        return days * 24L * 60 * 60 * 1000;
    }
}
