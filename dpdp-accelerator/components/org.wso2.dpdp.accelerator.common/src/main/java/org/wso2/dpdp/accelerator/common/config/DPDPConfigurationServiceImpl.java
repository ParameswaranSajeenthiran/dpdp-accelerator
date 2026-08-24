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
    private static final int DEFAULT_THREAD_POOL_SIZE = 4;
    private static final long DEFAULT_BASE_BACKOFF_SECONDS = 5L;
    private static final int DEFAULT_MAX_RETRIES = 5;
    private static final boolean DEFAULT_ALLOW_HTTP_CALLBACK_URL = true;
    private static final int DEFAULT_DELIVERY_WORKER_BATCH_SIZE = 50;
    private static final int DEFAULT_DELIVERY_WORKER_POLL_SECONDS = 5;
    private static final int DEFAULT_STUCK_INFLIGHT_THRESHOLD_SECONDS = 10;
    private static final int DEFAULT_MAX_VERIFICATION_RESPONSE_BODY_BYTES = 4096;
    private static final int DEFAULT_PENDING_SUBSCRIPTION_RECOVERY_THRESHOLD_SECONDS = 60;
    private static final int DEFAULT_BACKGROUND_WORKER_INITIAL_DELAY_SECONDS = 10;
    private static final int DEFAULT_PENDING_SUBSCRIPTION_RECOVERY_INTERVAL_SECONDS = 30;
    private static final int DEFAULT_PENDING_SUBSCRIPTION_RECOVERY_BATCH_SIZE = 20;
    private static final int DEFAULT_WORKER_SHUTDOWN_TIMEOUT_SECONDS = 5;
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
        return getPositiveInt("EventNotifications.ThreadPoolSize", DEFAULT_THREAD_POOL_SIZE);
    }

    @Override
    public long getEventNotificationBaseBackoffSeconds() {
        return getNonNegativeLong("EventNotifications.BaseBackoffSeconds", DEFAULT_BASE_BACKOFF_SECONDS);
    }

    @Override
    public int getEventNotificationMaxRetries() {
        return getNonNegativeInt("EventNotifications.MaxRetries", DEFAULT_MAX_RETRIES);
    }

    @Override
    public boolean isEventNotificationHttpCallbackUrlAllowed() {
        return getBoolean("EventNotifications.AllowHttpCallbackUrl", DEFAULT_ALLOW_HTTP_CALLBACK_URL);
    }

    @Override
    public int getEventNotificationDeliveryWorkerBatchSize() {
        return getPositiveInt("EventNotifications.DeliveryWorkerBatchSize", DEFAULT_DELIVERY_WORKER_BATCH_SIZE);
    }

    @Override
    public int getEventNotificationDeliveryWorkerPollSeconds() {
        return getPositiveInt("EventNotifications.DeliveryWorkerPollSeconds", DEFAULT_DELIVERY_WORKER_POLL_SECONDS);
    }

    @Override
    public int getEventNotificationStuckInFlightThresholdSeconds() {
        return getNonNegativeInt("EventNotifications.StuckInFlightThresholdSeconds",
                DEFAULT_STUCK_INFLIGHT_THRESHOLD_SECONDS);
    }

    @Override
    public int getEventNotificationMaxVerificationResponseBodyBytes() {
        return getPositiveInt("EventNotifications.MaxVerificationResponseBodyBytes",
                DEFAULT_MAX_VERIFICATION_RESPONSE_BODY_BYTES);
    }

    @Override
    public int getEventNotificationPendingSubscriptionRecoveryThresholdSeconds() {
        return getNonNegativeInt("EventNotifications.PendingSubscriptionRecoveryThresholdSeconds",
                DEFAULT_PENDING_SUBSCRIPTION_RECOVERY_THRESHOLD_SECONDS);
    }

    @Override
    public int getEventNotificationBackgroundWorkerInitialDelaySeconds() {
        return getNonNegativeInt("EventNotifications.BackgroundWorkerInitialDelaySeconds",
                DEFAULT_BACKGROUND_WORKER_INITIAL_DELAY_SECONDS);
    }

    @Override
    public int getEventNotificationPendingSubscriptionRecoveryIntervalSeconds() {
        return getPositiveInt("EventNotifications.PendingSubscriptionRecoveryIntervalSeconds",
                DEFAULT_PENDING_SUBSCRIPTION_RECOVERY_INTERVAL_SECONDS);
    }

    @Override
    public int getEventNotificationPendingSubscriptionRecoveryBatchSize() {
        return getPositiveInt("EventNotifications.PendingSubscriptionRecoveryBatchSize",
                DEFAULT_PENDING_SUBSCRIPTION_RECOVERY_BATCH_SIZE);
    }

    @Override
    public int getEventNotificationWorkerShutdownTimeoutSeconds() {
        return getPositiveInt("EventNotifications.WorkerShutdownTimeoutSeconds",
                DEFAULT_WORKER_SHUTDOWN_TIMEOUT_SECONDS);
    }

    private int getPositiveInt(String configKey, int defaultValue) {
        int value = getInt(configKey, defaultValue);
        if (value <= 0) {
            throw new IllegalStateException("DPDP configuration must be positive: " + configKey);
        }
        return value;
    }

    private int getNonNegativeInt(String configKey, int defaultValue) {
        int value = getInt(configKey, defaultValue);
        if (value < 0) {
            throw new IllegalStateException("DPDP configuration cannot be negative: " + configKey);
        }
        return value;
    }

    private long getNonNegativeLong(String configKey, long defaultValue) {
        String value = getValue(configKey, String.valueOf(defaultValue));
        try {
            long parsed = Long.parseLong(value);
            if (parsed < 0) {
                throw new IllegalStateException("DPDP configuration cannot be negative: " + configKey);
            }
            return parsed;
        } catch (NumberFormatException e) {
            throw new IllegalStateException("Invalid numeric DPDP configuration: " + configKey, e);
        }
    }

    private int getInt(String configKey, int defaultValue) {
        String value = getValue(configKey, String.valueOf(defaultValue));
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            throw new IllegalStateException("Invalid numeric DPDP configuration: " + configKey, e);
        }
    }

    private boolean getBoolean(String configKey, boolean defaultValue) {
        String value = getValue(configKey, String.valueOf(defaultValue));
        if (!"true".equalsIgnoreCase(value) && !"false".equalsIgnoreCase(value)) {
            throw new IllegalStateException("Invalid boolean DPDP configuration: " + configKey);
        }
        return Boolean.parseBoolean(value);
    }

    private String getValue(String configKey, String defaultValue) {
        Object configuredValue = getConfigurations().get(configKey);
        if (configuredValue != null && !configuredValue.toString().trim().isEmpty()) {
            return configuredValue.toString().trim();
        }
        return defaultValue;
    }
}
