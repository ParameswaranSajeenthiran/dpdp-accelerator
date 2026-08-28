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

package org.wso2.dpdp.accelerator.complaint.mgt.service.internal;

import org.wso2.dpdp.accelerator.common.config.DPDPConfigurationService;
import org.wso2.dpdp.accelerator.complaint.mgt.dao.ComplaintDAOProvider;

/**
 * Holds the external OSGi services this bundle depends on, mirroring
 * {@code EventNotificationDataHolder} in the event-notifications module.
 */
public final class ComplaintServiceDataHolder {

    private static final ComplaintServiceDataHolder INSTANCE = new ComplaintServiceDataHolder();

    private volatile ComplaintDAOProvider daoProvider;
    private volatile DPDPConfigurationService configurationService;

    private ComplaintServiceDataHolder() {
    }

    public static ComplaintServiceDataHolder getInstance() {
        return INSTANCE;
    }

    public ComplaintDAOProvider getDaoProvider() {
        return daoProvider;
    }

    public void setDaoProvider(ComplaintDAOProvider daoProvider) {
        this.daoProvider = daoProvider;
    }

    public DPDPConfigurationService getConfigurationService() {
        return configurationService;
    }

    public void setConfigurationService(DPDPConfigurationService configurationService) {
        this.configurationService = configurationService;
    }
}
