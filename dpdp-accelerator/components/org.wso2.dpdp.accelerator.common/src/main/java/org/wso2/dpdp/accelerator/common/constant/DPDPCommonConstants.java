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

package org.wso2.dpdp.accelerator.common.constant;

/**
 * Config file structure and key constants for {@code dpdp-accelerator.xml}.
 */
public final class DPDPCommonConstants {

    public static final String CONFIG_FILE_NAME = "dpdp-accelerator.xml";
    public static final String JDBC_DPDP_DATASOURCE_NAME = "jdbc/WSO2DPDP_DB";
    public static final String JDBC_DPDP_JNDI_ENV_NAME = "java:comp/env/jdbc/WSO2DPDP_DB";

    public static final String CONSENT_PORTAL_AUTO_PROVISIONING_ENABLED = "ConsentPortal.AutoProvisioningEnabled";
    public static final String CONSENT_PORTAL_CLIENT_ID = "ConsentPortal.ClientId";

    public static final String EVENT_NOTIFICATIONS_THREAD_POOL_SIZE = "EventNotifications.ThreadPoolSize";
    public static final String EVENT_NOTIFICATIONS_BASE_BACKOFF_SECONDS = "EventNotifications.BaseBackoffSeconds";
    public static final String EVENT_NOTIFICATIONS_MAX_RETRIES = "EventNotifications.MaxRetries";
    public static final String EVENT_NOTIFICATIONS_ALLOW_HTTP_CALLBACK_URL =
            "EventNotifications.AllowHttpCallbackUrl";
    public static final String EVENT_NOTIFICATIONS_DELIVERY_WORKER_BATCH_SIZE =
            "EventNotifications.DeliveryWorkerBatchSize";
    public static final String EVENT_NOTIFICATIONS_DELIVERY_WORKER_POLL_SECONDS =
            "EventNotifications.DeliveryWorkerPollSeconds";
    public static final String EVENT_NOTIFICATIONS_STUCK_INFLIGHT_THRESHOLD_SECONDS =
            "EventNotifications.StuckInFlightThresholdSeconds";
    public static final String EVENT_NOTIFICATIONS_MAX_VERIFICATION_RESPONSE_BODY_BYTES =
            "EventNotifications.MaxVerificationResponseBodyBytes";
    public static final String EVENT_NOTIFICATIONS_PENDING_SUBSCRIPTION_RECOVERY_THRESHOLD_SECONDS =
            "EventNotifications.PendingSubscriptionRecoveryThresholdSeconds";
    public static final String EVENT_NOTIFICATIONS_BACKGROUND_WORKER_INITIAL_DELAY_SECONDS =
            "EventNotifications.BackgroundWorkerInitialDelaySeconds";
    public static final String EVENT_NOTIFICATIONS_PENDING_SUBSCRIPTION_RECOVERY_INTERVAL_SECONDS =
            "EventNotifications.PendingSubscriptionRecoveryIntervalSeconds";
    public static final String EVENT_NOTIFICATIONS_PENDING_SUBSCRIPTION_RECOVERY_BATCH_SIZE =
            "EventNotifications.PendingSubscriptionRecoveryBatchSize";
    public static final String EVENT_NOTIFICATIONS_WORKER_SHUTDOWN_TIMEOUT_SECONDS =
            "EventNotifications.WorkerShutdownTimeoutSeconds";

    public static final String CONSENT_HISTORY_ENABLED = "ConsentHistory.Enabled";
    public static final String CONSENT_HISTORY_SNAPSHOT_ENABLED = "ConsentHistory.SnapshotEnabled";
    public static final String CONSENT_HISTORY_DATA_SOURCE_NAME = "ConsentHistory.DataSourceName";
    public static final String DEFAULT_CONSENT_HISTORY_DATA_SOURCE_NAME = "jdbc/WSO2DPDP_DB";

    public static final String CONSENT_EXPIRY_ENABLED = "ConsentExpiry.Enabled";
    public static final String CONSENT_EXPIRY_CRON_VALUE = "ConsentExpiry.CronValue";
    public static final String CONSENT_EXPIRY_BATCH_SIZE = "ConsentExpiry.BatchSize";
    public static final String DEFAULT_CONSENT_EXPIRY_CRON_VALUE = "0 0 0 * * ?";
    public static final int DEFAULT_CONSENT_EXPIRY_BATCH_SIZE = 100;

    private DPDPCommonConstants() {

    }
}
