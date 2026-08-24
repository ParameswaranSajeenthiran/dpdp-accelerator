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

package org.wso2.dpdp.accelerator.common.config;

import java.util.Map;

public class DPDPConfigurationServiceImpl implements DPDPConfigurationService {

    private static final DPDPConfigParser CONFIG_PARSER = DPDPConfigParser.getInstance();

    @Override
    public Map<String, Object> getConfigurations() {

        return CONFIG_PARSER.getConfiguration();
    }

    @Override
    public boolean isConsentPortalProvisioningEnabled() {

        return CONFIG_PARSER.isConsentPortalProvisioningEnabled();
    }

    @Override
    public String getConsentPortalClientId() {

        return CONFIG_PARSER.getConsentPortalClientId();
    }

    @Override
    public boolean isConsentHistoryEnabled() {

        return CONFIG_PARSER.isConsentHistoryEnabled();
    }

    @Override
    public boolean isConsentHistorySnapshotEnabled() {

        return CONFIG_PARSER.isConsentHistorySnapshotEnabled();
    }

    @Override
    public String getConsentHistoryDataSourceName() {

        return CONFIG_PARSER.getConsentHistoryDataSourceName();
    }

    @Override
    public boolean isConsentExpiryEnabled() {

        return CONFIG_PARSER.isConsentExpiryEnabled();
    }

    @Override
    public String getConsentExpiryCronValue() {

        return CONFIG_PARSER.getConsentExpiryCronValue();
    }

    @Override
    public int getConsentExpiryBatchSize() {

        return CONFIG_PARSER.getConsentExpiryBatchSize();
    }
}
