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

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.util.Map;
import java.util.Collections;

public class DPDPConfigurationServiceImpl implements DPDPConfigurationService {

    private static final Log LOG = LogFactory.getLog(DPDPConfigurationServiceImpl.class);
    private final DPDPConfigParser configParser;

    public DPDPConfigurationServiceImpl() {
        this(true);
    }

    public DPDPConfigurationServiceImpl(boolean loadConfiguration) {
        DPDPConfigParser parser;
        if (!loadConfiguration) {
            parser = null;
        } else {
            try {
                parser = DPDPConfigParser.getInstance();
            } catch (RuntimeException e) {
            LOG.debug("DPDP accelerator configuration is unavailable.", e);
                parser = null;
            }
        }
        this.configParser = parser;
    }

    @Override
    public Map<String, Object> getConfigurations() {

        return configParser == null ? Collections.emptyMap() : configParser.getConfiguration();
    }

    @Override
    public boolean isConsentPortalProvisioningEnabled() {

        return configParser == null || configParser.isConsentPortalProvisioningEnabled();
    }

    @Override
    public String getConsentPortalClientId() {

        return configParser == null ? "DPDP_CONSENT_PORTAL" : configParser.getConsentPortalClientId();
    }

    @Override
    public int getEventNotificationThreadPoolSize() {
        return getInt("EventNotifications.ThreadPoolSize");
    }

    @Override
    public long getEventNotificationBaseBackoffSeconds() {
        return getLong("EventNotifications.BaseBackoffSeconds");
    }

    @Override
    public int getEventNotificationMaxRetries() {
        return getInt("EventNotifications.MaxRetries");
    }

    @Override
    public boolean isEventNotificationHttpCallbackUrlAllowed() {
        return getBoolean("EventNotifications.AllowHttpCallbackUrl");
    }

    @Override
    public int getEventNotificationDeliveryWorkerBatchSize() {
        return getInt("EventNotifications.DeliveryWorkerBatchSize");
    }

    @Override
    public int getEventNotificationDeliveryWorkerPollSeconds() {
        return getInt("EventNotifications.DeliveryWorkerPollSeconds");
    }

    @Override
    public int getEventNotificationStuckInFlightThresholdSeconds() {
        return getInt("EventNotifications.StuckInFlightThresholdSeconds");
    }

    @Override
    public int getEventNotificationMaxVerificationResponseBodyBytes() {
        return getInt("EventNotifications.MaxVerificationResponseBodyBytes");
    }

    @Override
    public int getEventNotificationPendingSubscriptionRecoveryThresholdSeconds() {
        return getInt("EventNotifications.PendingSubscriptionRecoveryThresholdSeconds");
    }

    private int getInt(String configKey) {
        return Integer.parseInt(getRequiredValue(configKey));
    }

    private long getLong(String configKey) {
        return Long.parseLong(getRequiredValue(configKey));
    }

    private boolean getBoolean(String configKey) {
        return Boolean.parseBoolean(getRequiredValue(configKey));
    }

    private String getRequiredValue(String configKey) {
        Object configuredValue = getConfigurations().get(configKey);
        if (configuredValue != null && !configuredValue.toString().trim().isEmpty()) {
            return configuredValue.toString().trim();
        }
        throw new IllegalStateException("Required DPDP configuration is missing: " + configKey);
    }
}
