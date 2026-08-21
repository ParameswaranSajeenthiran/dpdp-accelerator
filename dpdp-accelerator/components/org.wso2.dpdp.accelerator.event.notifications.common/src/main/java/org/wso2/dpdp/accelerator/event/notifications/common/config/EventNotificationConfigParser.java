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

package org.wso2.dpdp.accelerator.event.notifications.common.config;

import org.wso2.dpdp.accelerator.common.config.DPDPConfigParser;
import org.wso2.dpdp.accelerator.common.constant.DPDPCommonConstants;
import org.wso2.dpdp.accelerator.event.notifications.common.constants.EventNotificationCommonConstants;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * Configuration parser for event notification settings.
 * <p>
 * Configuration Precedence Order (highest to lowest):
 * 1. Carbon / TOML deployment configuration map (set via setConfiguration)
 * 2. JVM System Properties (e.g. -Devent_notifications.thread_pool_size=8)
 * 3. Environment Variables (e.g. EVENT_NOTIFICATIONS_THREAD_POOL_SIZE=8)
 * 4. Default Fallback Constants
 */
public class EventNotificationConfigParser {

    private static final Log LOG = LogFactory.getLog(EventNotificationConfigParser.class);

    private static volatile EventNotificationConfigParser instance;
    private final Map<String, Object> configurationMap = new ConcurrentHashMap<>();

    private EventNotificationConfigParser() {
        loadConfigurations();
    }

    public static EventNotificationConfigParser getInstance() {
        if (instance == null) {
            synchronized (EventNotificationConfigParser.class) {
                if (instance == null) {
                    instance = new EventNotificationConfigParser();
                }
            }
        }
        return instance;
    }

    private void loadConfigurations() {
        configurationMap.put(DPDPCommonConstants.EVENT_NOTIFICATIONS_THREAD_POOL_SIZE,
                resolveIntConfig("EVENT_NOTIFICATIONS_THREAD_POOL_SIZE",
                        "event_notifications.thread_pool_size",
                        EventNotificationCommonConstants.DEFAULT_THREAD_POOL_SIZE));

        configurationMap.put(DPDPCommonConstants.EVENT_NOTIFICATIONS_BASE_BACKOFF_SECONDS,
                resolveLongConfig("EVENT_NOTIFICATIONS_BASE_BACKOFF_SECONDS",
                        "event_notifications.base_backoff_seconds",
                        EventNotificationCommonConstants.DEFAULT_BASE_BACKOFF_SECONDS));

        configurationMap.put(DPDPCommonConstants.EVENT_NOTIFICATIONS_MAX_RETRIES,
                resolveIntConfig("EVENT_NOTIFICATIONS_MAX_RETRIES",
                        "event_notifications.max_retries",
                        EventNotificationCommonConstants.DEFAULT_MAX_RETRIES));

        configurationMap.put(DPDPCommonConstants.EVENT_NOTIFICATIONS_ALLOW_HTTP_CALLBACK_URL,
                resolveBooleanConfig("EVENT_NOTIFICATIONS_ALLOW_HTTP_CALLBACK_URL",
                        "event_notifications.allow_http_callback_url",
                        EventNotificationCommonConstants.DEFAULT_ALLOW_HTTP_CALLBACK_URL));

        configurationMap.put(DPDPCommonConstants.EVENT_NOTIFICATIONS_DELIVERY_WORKER_BATCH_SIZE,
                resolveIntConfig("EVENT_NOTIFICATIONS_DELIVERY_WORKER_BATCH_SIZE",
                        "event_notifications.delivery_worker_batch_size",
                        EventNotificationCommonConstants.DEFAULT_DELIVERY_WORKER_BATCH_SIZE));

        configurationMap.put(DPDPCommonConstants.EVENT_NOTIFICATIONS_DELIVERY_WORKER_POLL_SECONDS,
                resolveIntConfig("EVENT_NOTIFICATIONS_DELIVERY_WORKER_POLL_SECONDS",
                        "event_notifications.delivery_worker_poll_seconds",
                        EventNotificationCommonConstants.DEFAULT_DELIVERY_WORKER_POLL_SECONDS));

        configurationMap.put(DPDPCommonConstants.EVENT_NOTIFICATIONS_STUCK_INFLIGHT_THRESHOLD_SECONDS,
                resolveIntConfig("EVENT_NOTIFICATIONS_STUCK_INFLIGHT_THRESHOLD_SECONDS",
                        "event_notifications.stuck_inflight_threshold_seconds",
                        EventNotificationCommonConstants.DEFAULT_STUCK_INFLIGHT_THRESHOLD_SECONDS));

        configurationMap.put(DPDPCommonConstants.EVENT_NOTIFICATIONS_MAX_VERIFICATION_RESPONSE_BODY_BYTES,
                resolveIntConfig("EVENT_NOTIFICATIONS_MAX_VERIFICATION_RESPONSE_BODY_BYTES",
                        "event_notifications.max_verification_response_body_bytes",
                        EventNotificationCommonConstants.DEFAULT_MAX_VERIFICATION_RESPONSE_BODY_BYTES));

        configurationMap.put(DPDPCommonConstants.EVENT_NOTIFICATIONS_PENDING_SUBSCRIPTION_RECOVERY_THRESHOLD_SECONDS,
                resolveIntConfig("EVENT_NOTIFICATIONS_PENDING_SUBSCRIPTION_RECOVERY_THRESHOLD_SECONDS",
                        "event_notifications.pending_subscription_recovery_threshold_seconds",
                        EventNotificationCommonConstants.DEFAULT_PENDING_SUBSCRIPTION_RECOVERY_THRESHOLD_SECONDS));

        loadCarbonConfiguration();
    }

    private void loadCarbonConfiguration() {
        try {
            Map<String, Object> carbonConfiguration = DPDPConfigParser.getInstance().getConfiguration();
            carbonConfiguration.forEach((key, value) -> {
                if (key.startsWith("EventNotifications.")) {
                    configurationMap.put(key, value);
                }
            });
        } catch (RuntimeException | LinkageError e) {
            LOG.warn("Unable to load dpdp-accelerator.xml for Event Notification configuration; "
                    + "using system, environment, or default values.", e);
        }
    }

    private int resolveIntConfig(String envKey, String sysKey, int defaultValue) {
        String sysVal = System.getProperty(sysKey);
        if (sysVal != null && !sysVal.trim().isEmpty()) {
            try {
                return Integer.parseInt(sysVal.trim());
            } catch (NumberFormatException e) {
                LOG.warn("Invalid integer for System property " + sysKey + ": '" + sysVal + "', falling back to next tier.");
            }
        }
        String envVal = System.getenv(envKey);
        if (envVal != null && !envVal.trim().isEmpty()) {
            try {
                return Integer.parseInt(envVal.trim());
            } catch (NumberFormatException e) {
                LOG.warn("Invalid integer for Environment variable " + envKey + ": '" + envVal + "', falling back to default.");
            }
        }
        return defaultValue;
    }

    private long resolveLongConfig(String envKey, String sysKey, long defaultValue) {
        String sysVal = System.getProperty(sysKey);
        if (sysVal != null && !sysVal.trim().isEmpty()) {
            try {
                return Long.parseLong(sysVal.trim());
            } catch (NumberFormatException e) {
                LOG.warn("Invalid long for System property " + sysKey + ": '" + sysVal + "', falling back to next tier.");
            }
        }
        String envVal = System.getenv(envKey);
        if (envVal != null && !envVal.trim().isEmpty()) {
            try {
                return Long.parseLong(envVal.trim());
            } catch (NumberFormatException e) {
                LOG.warn("Invalid long for Environment variable " + envKey + ": '" + envVal + "', falling back to default.");
            }
        }
        return defaultValue;
    }

    private boolean resolveBooleanConfig(String envKey, String sysKey, boolean defaultValue) {
        String sysVal = System.getProperty(sysKey);
        if (sysVal != null && !sysVal.trim().isEmpty()) {
            return Boolean.parseBoolean(sysVal.trim());
        }
        String envVal = System.getenv(envKey);
        if (envVal != null && !envVal.trim().isEmpty()) {
            return Boolean.parseBoolean(envVal.trim());
        }
        return defaultValue;
    }

    public void setConfiguration(Map<String, Object> configs) {
        if (configs != null) {
            this.configurationMap.putAll(configs);
        }
    }

    public int getThreadPoolSize() {
        Object val = configurationMap.get(DPDPCommonConstants.EVENT_NOTIFICATIONS_THREAD_POOL_SIZE);
        if (val instanceof Integer) {
            return (Integer) val;
        } else if (val instanceof String) {
            try {
                return Integer.parseInt(((String) val).trim());
            } catch (NumberFormatException e) {
                LOG.warn("Invalid thread pool size in config map: '" + val + "'");
            }
        }
        return EventNotificationCommonConstants.DEFAULT_THREAD_POOL_SIZE;
    }

    public long getBaseBackoffSeconds() {
        Object val = configurationMap.get(DPDPCommonConstants.EVENT_NOTIFICATIONS_BASE_BACKOFF_SECONDS);
        if (val instanceof Long) {
            return (Long) val;
        } else if (val instanceof Integer) {
            return ((Integer) val).longValue();
        } else if (val instanceof String) {
            try {
                return Long.parseLong(((String) val).trim());
            } catch (NumberFormatException e) {
                LOG.warn("Invalid base backoff seconds in config map: '" + val + "'");
            }
        }
        return EventNotificationCommonConstants.DEFAULT_BASE_BACKOFF_SECONDS;
    }

    public int getMaxRetries() {
        Object val = configurationMap.get(DPDPCommonConstants.EVENT_NOTIFICATIONS_MAX_RETRIES);
        if (val instanceof Integer) {
            return (Integer) val;
        } else if (val instanceof String) {
            try {
                return Integer.parseInt(((String) val).trim());
            } catch (NumberFormatException e) {
                LOG.warn("Invalid max retries in config map: '" + val + "'");
            }
        }
        return EventNotificationCommonConstants.DEFAULT_MAX_RETRIES;
    }

    public int getMaxAttempts() {
        return getMaxRetries();
    }

    public boolean isAllowHttpCallbackUrl() {
        Object val = configurationMap.get(DPDPCommonConstants.EVENT_NOTIFICATIONS_ALLOW_HTTP_CALLBACK_URL);
        if (val instanceof Boolean) {
            return (Boolean) val;
        } else if (val instanceof String) {
            return Boolean.parseBoolean(((String) val).trim());
        }
        return EventNotificationCommonConstants.DEFAULT_ALLOW_HTTP_CALLBACK_URL;
    }

    public void setAllowHttpCallbackUrl(boolean allowHttp) {
        configurationMap.put(DPDPCommonConstants.EVENT_NOTIFICATIONS_ALLOW_HTTP_CALLBACK_URL, allowHttp);
    }

    public int getDeliveryWorkerBatchSize() {
        Object val = configurationMap.get(DPDPCommonConstants.EVENT_NOTIFICATIONS_DELIVERY_WORKER_BATCH_SIZE);
        if (val instanceof Integer) {
            return (Integer) val;
        } else if (val instanceof String) {
            try {
                return Integer.parseInt(((String) val).trim());
            } catch (NumberFormatException e) {
                LOG.warn("Invalid delivery worker batch size in config map: '" + val + "'");
            }
        }
        return EventNotificationCommonConstants.DEFAULT_DELIVERY_WORKER_BATCH_SIZE;
    }

    public int getDeliveryWorkerPollSeconds() {
        Object val = configurationMap.get(DPDPCommonConstants.EVENT_NOTIFICATIONS_DELIVERY_WORKER_POLL_SECONDS);
        if (val instanceof Integer) {
            return (Integer) val;
        } else if (val instanceof String) {
            try {
                return Integer.parseInt(((String) val).trim());
            } catch (NumberFormatException e) {
                LOG.warn("Invalid delivery worker poll seconds in config map: '" + val + "'");
            }
        }
        return EventNotificationCommonConstants.DEFAULT_DELIVERY_WORKER_POLL_SECONDS;
    }

    public int getStuckInFlightThresholdSeconds() {
        Object val = configurationMap.get(DPDPCommonConstants.EVENT_NOTIFICATIONS_STUCK_INFLIGHT_THRESHOLD_SECONDS);
        if (val instanceof Integer) {
            return (Integer) val;
        } else if (val instanceof String) {
            try {
                return Integer.parseInt(((String) val).trim());
            } catch (NumberFormatException e) {
                LOG.warn("Invalid stuck in-flight threshold in config map: '" + val + "'");
            }
        }
        return EventNotificationCommonConstants.DEFAULT_STUCK_INFLIGHT_THRESHOLD_SECONDS;
    }

    public int getMaxVerificationResponseBodyBytes() {
        Object val = configurationMap.get(
                DPDPCommonConstants.EVENT_NOTIFICATIONS_MAX_VERIFICATION_RESPONSE_BODY_BYTES);
        if (val instanceof Integer) {
            return (Integer) val;
        } else if (val instanceof String) {
            try {
                return Integer.parseInt(((String) val).trim());
            } catch (NumberFormatException e) {
                LOG.warn("Invalid max verification response body bytes in config map: '" + val + "'");
            }
        }
        return EventNotificationCommonConstants.DEFAULT_MAX_VERIFICATION_RESPONSE_BODY_BYTES;
    }

    public int getPendingSubscriptionRecoveryThresholdSeconds() {
        Object val = configurationMap.get(
                DPDPCommonConstants.EVENT_NOTIFICATIONS_PENDING_SUBSCRIPTION_RECOVERY_THRESHOLD_SECONDS);
        if (val instanceof Integer) {
            return (Integer) val;
        } else if (val instanceof String) {
            try {
                return Integer.parseInt(((String) val).trim());
            } catch (NumberFormatException e) {
                LOG.warn("Invalid pending subscription recovery threshold in config map: '" + val + "'");
            }
        }
        return EventNotificationCommonConstants.DEFAULT_PENDING_SUBSCRIPTION_RECOVERY_THRESHOLD_SECONDS;
    }
}
