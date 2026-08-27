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

package org.wso2.dpdp.accelerator.event.notifications.service.internal;

import org.wso2.dpdp.accelerator.common.config.DPDPConfigurationService;

/**
 * Holds external OSGi services shared by the Event Notification service bundle.
 */
public final class EventNotificationDataHolder {

    private static final EventNotificationDataHolder INSTANCE = new EventNotificationDataHolder();

    private volatile DPDPConfigurationService configurationService;

    private EventNotificationDataHolder() {
    }

    public static EventNotificationDataHolder getInstance() {
        return INSTANCE;
    }

    public DPDPConfigurationService getConfigurationService() {
        return configurationService;
    }

    public void setConfigurationService(DPDPConfigurationService configurationService) {
        this.configurationService = configurationService;
    }
}
