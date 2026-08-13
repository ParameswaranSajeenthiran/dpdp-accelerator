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

package org.wso2.dpdp.accelerator.event.notifications.common.constants;

/**
 * Common constant definitions for WSO2 DPDP Event Notification Framework.
 */
public class EventNotificationCommonConstants {

    private EventNotificationCommonConstants() {
    }

    // Datasource Constants
    public static final String JDBC_EVENT_NOTIFICATION_DATASOURCE_NAME = "jdbc/EventNotificationDB";
    public static final String JDBC_EVENT_NOTIFICATION_JNDI_ENV_NAME = "java:comp/env/jdbc/EventNotificationDB";
    public static final String JDBC_SHARED_DATASOURCE_NAME = "jdbc/WSO2SHARED_DB";
    public static final String DEFAULT_H2_URL = "jdbc:h2:./repository/database/WSO2EVENT_NOTIFICATION_DB;DB_CLOSE_ON_EXIT=FALSE;LOCK_TIMEOUT=60000";
    public static final String DEFAULT_H2_USER = "wso2carbon";
    public static final String DEFAULT_H2_PASS = "wso2carbon";

    // Environment Overrides for DB
    public static final String ENV_JDBC_URL = "ENF_DB_URL";
    public static final String ENV_JDBC_USER = "ENF_DB_USER";
    public static final String ENV_JDBC_PASS = "ENF_DB_PASS";

    // Config Keys
    public static final String CONFIG_THREAD_POOL_SIZE = "event_notifications.thread_pool_size";
    public static final String CONFIG_BASE_BACKOFF_SECONDS = "event_notifications.base_backoff_seconds";
    public static final String CONFIG_MAX_RETRIES = "event_notifications.max_retries";
    public static final String CONFIG_ALLOW_HTTP_CALLBACK_URL = "event_notifications.allow_http_callback_url";
    public static final String CONFIG_DELIVERY_WORKER_BATCH_SIZE = "event_notifications.delivery_worker_batch_size";
    public static final String CONFIG_DELIVERY_WORKER_POLL_SECONDS = "event_notifications.delivery_worker_poll_seconds";
    public static final String CONFIG_STUCK_INFLIGHT_THRESHOLD_SECONDS =
            "event_notifications.stuck_inflight_threshold_seconds";
    public static final String CONFIG_MAX_VERIFICATION_RESPONSE_BODY_BYTES =
            "event_notifications.max_verification_response_body_bytes";
    public static final String CONFIG_PENDING_SUBSCRIPTION_RECOVERY_THRESHOLD_SECONDS =
            "event_notifications.pending_subscription_recovery_threshold_seconds";

    // Defaults
    public static final int DEFAULT_THREAD_POOL_SIZE = 4;
    public static final long DEFAULT_BASE_BACKOFF_SECONDS = 5L;
    public static final int DEFAULT_MAX_RETRIES = 5;
    public static final boolean DEFAULT_ALLOW_HTTP_CALLBACK_URL = true;
    public static final int DEFAULT_DELIVERY_WORKER_BATCH_SIZE = 50;
    public static final int DEFAULT_DELIVERY_WORKER_POLL_SECONDS = 5;
    public static final int DEFAULT_STUCK_INFLIGHT_THRESHOLD_SECONDS = 10;
    public static final int DEFAULT_MAX_VERIFICATION_RESPONSE_BODY_BYTES = 4096;
    public static final int DEFAULT_PENDING_SUBSCRIPTION_RECOVERY_THRESHOLD_SECONDS = 60;

    // Data Access Error Messages
    public static final String ERROR_ADDING_TOPIC = "Error adding topic [%s]";
    public static final String ERROR_GETTING_TOPIC_BY_ID = "Error getting topic by ID [%s]";
    public static final String ERROR_GETTING_TOPIC_BY_ORG_AND_NAME = "Error getting topic by org [%s] and name [%s]";
    public static final String ERROR_UPDATING_TOPIC_STATUS = "Error updating topic status for ID [%s]";
    public static final String ERROR_DEREGISTERING_TOPIC = "Error deregistering topic [%s]";
    public static final String ERROR_LISTING_TOPICS = "Error listing topics for org [%s]";

    public static final String ERROR_ADDING_SUBSCRIPTION = "Error adding subscription [%s]";
    public static final String ERROR_GETTING_SUBSCRIPTION_BY_ID = "Error getting subscription by ID [%s]";
    public static final String ERROR_UPDATING_SUBSCRIPTION_STATUS = "Error updating subscription status for ID [%s]";
    public static final String ERROR_DELETING_SUBSCRIPTION = "Error deleting subscription [%s]";
    public static final String ERROR_LISTING_SUBSCRIPTIONS = "Error listing subscriptions for org [%s]";
    public static final String ERROR_GETTING_SUBSCRIPTIONS_BY_ORG_AND_TOPIC = "Error getting subscriptions by org [%s] and topic [%s]";
    public static final String ERROR_GETTING_PURPOSES_BY_SUBSCRIPTION_ID = "Error getting purposes for subscription [%s]";
    public static final String ERROR_GETTING_PURPOSES_BY_BATCH_SUBSCRIPTION_IDS = "Error getting purposes for batch subscription IDs";
    public static final String ERROR_CHECKING_PENDING_DELIVERIES_FOR_SUBSCRIPTION = "Error checking pending deliveries for subscription [%s]";
    public static final String ERROR_GETTING_PENDING_SUBSCRIPTIONS_FOR_RECOVERY = "Error getting pending subscriptions for recovery";

    public static final String ERROR_ADDING_DELIVERY_ACK = "Error adding delivery ack [%s]";
    public static final String ERROR_GETTING_DELIVERY_ACK_BY_DELIVERY_ID = "Error getting delivery ack by delivery ID [%s]";

    public static final String ERROR_ADDING_WEBHOOK_DELIVERY = "Error adding webhook delivery [%s]";
    public static final String ERROR_GETTING_WEBHOOK_DELIVERY = "Error getting webhook delivery [%s]";
    public static final String ERROR_GETTING_PENDING_WEBHOOK_DELIVERIES = "Error getting pending webhook deliveries";
    public static final String ERROR_UPDATING_WEBHOOK_DELIVERY_STATUS = "Error updating webhook delivery status for [%s]";
    public static final String ERROR_ADDING_WEBHOOK_DELIVERY_AUDIT = "Error adding webhook delivery audit for delivery [%s]";
    public static final String ERROR_GETTING_WEBHOOK_DELIVERY_AUDITS = "Error getting webhook delivery audits for delivery [%s]";
    public static final String ERROR_ADDING_POLL_DELIVERY = "Error adding poll delivery [%s]";
    public static final String ERROR_GETTING_POLL_DELIVERY = "Error getting poll delivery [%s]";
    public static final String ERROR_GETTING_PENDING_POLL_DELIVERIES = "Error getting pending poll deliveries for group [%s]";
    public static final String ERROR_UPDATING_POLL_DELIVERY_STATUSES = "Error updating poll delivery statuses for group [%s]";
    public static final String ERROR_UPDATING_POLL_DELIVERY_STATUS = "Error updating poll delivery status for [%s]";
    public static final String ERROR_LISTING_DELIVERIES_FOR_SUBSCRIPTION = "Error listing deliveries for subscription [%s]";
    public static final String ERROR_GETTING_SUBSCRIPTION_DELIVERY = "Error getting subscription delivery [%s]";
    public static final String ERROR_LISTING_ORG_DELIVERIES = "Error listing org deliveries for org [%s]";
    public static final String ERROR_GETTING_ORG_DELIVERY = "Error getting org delivery [%s]";
    public static final String ERROR_ADDING_EVENT = "Error adding event [%s]";
    public static final String ERROR_GETTING_EVENT_BY_ID = "Error getting event by ID [%s]";
    public static final String ERROR_ADDING_EVENT_PURPOSES = "Error adding event purposes for event [%s]";
    public static final String ERROR_GETTING_EVENT_PURPOSES = "Error getting event purposes for event [%s]";
    public static final String ERROR_HAS_ACTIVE_EVENTS_FOR_TOPIC = "Error checking active events for topic [%s]";
    public static final String ERROR_GETTING_EVENT_PAYLOAD = "Error getting event payload [%s]";
    public static final String ERROR_LISTING_EVENTS = "Error listing events for org [%s]";
    public static final String ERROR_SUBSCRIPTION_OVERLAPPING_PURPOSES = "An active subscription with overlapping purposes already exists for this group/topic.";
    public static final String ERROR_DUPLICATE_SUBSCRIPTION = "A subscription with the same parameters already exists.";
    public static final String ERROR_DELIVERY_ACK_ALREADY_EXISTS = "Completion evidence already acknowledged for this delivery.";
    public static final String ERROR_TOPIC_HAS_ACTIVE_SUBSCRIPTIONS = "Topic has active subscriptions and cannot be deregistered.";
    public static final String ERROR_TOPIC_NOT_ACTIVE = "Topic is not active and cannot accept new subscriptions.";
    public static final String ERROR_TOPIC_NULL = "Topic must not be null.";
    public static final String ERROR_TOPIC_STATUS_NULL = "Topic status must not be null.";
    public static final String ERROR_TOPIC_ALREADY_EXISTS = "Topic already exists: %s";
    public static final String ERROR_UNKNOWN_ENUM_VALUE = "Unknown %s: %s";

    public static final int DEFAULT_LIMIT = 20;
    public static final int MAX_LIMIT = 100;
}
