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

import org.wso2.dpdp.common.config.ConfigProvider;

/**
 * Statutory due period for grievance redressal under the DPDP Act. Configurable via
 * deployment.toml's [complaint_mgt] statutory_due_period_days, with the
 * CO_STATUTORY_DUE_PERIOD_DAYS system property as a fallback beneath that (see ConfigProvider),
 * defaulting to 90 days if neither is set.
 */
public class StatutoryDuePeriodPolicy {

    private static final String CONFIG_DUE_PERIOD_DAYS = "complaint_mgt.statutory_due_period_days";
    private static final int DEFAULT_DUE_PERIOD_DAYS = 90;

    private StatutoryDuePeriodPolicy() {
    }

    public static long getDuePeriodMillis() {
        String configured = ConfigProvider.getString(CONFIG_DUE_PERIOD_DAYS,
                System.getProperty("CO_STATUTORY_DUE_PERIOD_DAYS", String.valueOf(DEFAULT_DUE_PERIOD_DAYS)));
        int days;
        try {
            days = Integer.parseInt(configured.trim());
        } catch (NumberFormatException e) {
            days = DEFAULT_DUE_PERIOD_DAYS;
        }
        return days * 24L * 60 * 60 * 1000;
    }
}
