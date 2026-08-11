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

package org.wso2.dpdp.accelerator.event.notifications.service.constants;

/**
 * Event Notification Service Constants matching WSO2 Accelerator standards.
 */
public class EventNotificationServiceConstants {

    private EventNotificationServiceConstants() {
    }

    // Domain & State Constants
    public static final String WEBHOOK_DELIVERY_MODE = "webhook";
    public static final String POLL_DELIVERY_MODE = "poll";
    public static final String STATUS_ACTIVE = "active";
    public static final String STATUS_STALE = "stale";
    public static final String STATUS_PENDING = "pending";
    public static final String STATUS_DELETED = "deleted";

    // Error Codes
    public static final String ERROR_CODE_INVALID_REQUEST = "CS-4001";
    public static final String ERROR_CODE_MISSING_REQUIRED_PARAM = "CS-4002";
    public static final String ERROR_CODE_INVALID_STATE = "CS-4003";
    public static final String ERROR_CODE_RESOURCE_NOT_FOUND = "CS-4040";
    public static final String ERROR_CODE_TOPIC_NOT_FOUND = "CS-4041";
    public static final String ERROR_CODE_DELIVERY_NOT_FOUND = "CS-4042";
    public static final String ERROR_CODE_RESOURCE_EXISTS = "CS-4090";
    public static final String ERROR_CODE_WEBHOOK_VERIFICATION_FAILED = "CS-4220";
    public static final String ERROR_CODE_INTERNAL_ERROR = "CS-5000";

    // Error Titles
    public static final String ERROR_TITLE_MALFORMED_REQUEST = "Malformed request";
    public static final String ERROR_TITLE_VALIDATION_FAILED = "Validation failed";
    public static final String ERROR_TITLE_INVALID_STATE = "Invalid state";
    public static final String ERROR_TITLE_RESOURCE_NOT_FOUND = "Resource not found";
    public static final String ERROR_TITLE_TOPIC_NOT_FOUND = "Topic not found";
    public static final String ERROR_TITLE_DELIVERY_NOT_FOUND = "Delivery not found";
    public static final String ERROR_TITLE_RESOURCE_EXISTS = "Resource already exists";
    public static final String ERROR_TITLE_DUPLICATE_SUBSCRIPTION = "Duplicate subscription";
    public static final String ERROR_TITLE_TOPIC_ALREADY_EXISTS = "Topic already exists";
    public static final String ERROR_TITLE_WEBHOOK_VERIFICATION_FAILED = "Webhook verification failed";
    public static final String ERROR_TITLE_INTERNAL_ERROR = "Internal error";
    public static final String ERROR_TITLE_CONCURRENT_MUTATION = "Concurrent state mutation";
    public static final String ERROR_TITLE_IN_FLIGHT_DELIVERIES = "In-flight deliveries exist";
    public static final String ERROR_TITLE_OPERATION_FORBIDDEN = "Operation forbidden";

    // Error Messages & Descriptions
    public static final String ORG_ID_OR_TOPIC_NAME_MISSING_ERROR_MSG = "Org ID and topic name are required.";
    public static final String TOPIC_ID_MISSING_ERROR_MSG = "Topic ID is required.";
    public static final String TOPIC_ALREADY_EXISTS_ERROR_MSG = "A topic with the specified name already exists for this organization.";
    public static final String FAILED_TO_REACTIVATE_TOPIC_ERROR_MSG = "Failed to reactivate existing topic.";
    public static final String FAILED_TO_CREATE_TOPIC_ERROR_MSG = "Failed to create topic in database.";
    public static final String FAILED_TO_DEREGISTER_TOPIC_ERROR_MSG = "Failed to deregister topic.";
    public static final String ORG_ID_MISSING_ERROR_MSG = "Organization ID is required.";
    public static final String SUBSCRIPTION_ID_MISSING_ERROR_MSG = "Subscription ID is required.";
    public static final String SUBSCRIPTION_NOT_FOUND_ERROR_MSG = "No subscription exists with the specified ID for this organization.";
    public static final String DELIVERY_NOT_FOUND_ERROR_MSG = "No delivery exists with the specified ID for this subscription.";
    public static final String ONLY_STALE_SUBSCRIPTIONS_VERIFIABLE_ERROR_MSG = "Only subscriptions in 'stale' state can be re-verified.";
    public static final String NO_CALLBACK_URL_ERROR_MSG = "Subscription does not have a callback URL — re-verification is only applicable to webhook subscriptions.";
    public static final String WEBHOOK_VERIFICATION_FAILED_ERROR_MSG = "Webhook intent verification failed for callback URL.";
    public static final String CALLBACK_URL_REQUIRED_ERROR_MSG = "callbackUrl is required when delivery mode is WEBHOOK.";
    public static final String CALLBACK_URL_HTTP_SCHEME_ERROR_MSG = "callbackUrl must use HTTP or HTTPS scheme.";
    public static final String CALLBACK_URL_HOST_INVALID_ERROR_MSG = "callbackUrl hostname is invalid.";
    public static final String CALLBACK_URL_NOT_PERMITTED_ERROR_MSG = "Callback URL destination is not permitted.";
    public static final String DUPLICATE_SUBSCRIPTION_ERROR_MSG = "A subscription with the same parameters already exists.";
    public static final String FAILED_TO_DELETE_SUBSCRIPTION_ERROR_MSG = "Failed to delete subscription.";
    public static final String CALLBACK_URL_HTTPS_REQUIRED_ERROR_MSG = "callbackUrl must use HTTPS scheme in production environment.";
    public static final String WEBHOOK_CHALLENGE_MISMATCH_ERROR_MSG = "Callback URL did not echo back challenge string.";
    public static final String TOPIC_NOT_FOUND_ERROR_MSG = "No topic exists with ID '%s' for this org.";
    public static final String SYSTEM_TOPIC_DELETE_FORBIDDEN_ERROR_MSG = "System topic '%s' is system-defined and cannot be deleted or deactivated.";
    public static final String TOPIC_ALREADY_DEREGISTERED_ERROR_MSG = "Topic '%s' is already deregistered.";
    public static final String SUBSCRIPTION_IN_FLIGHT_DELIVERIES_ERROR_MSG = "Subscription has pending or in-flight deliveries and cannot be deleted until they complete.";
    public static final String SUBSCRIPTION_CONCURRENT_MODIFICATION_ERROR_MSG = "Subscription status was modified concurrently by another operation.";
    public static final String TOPIC_HAS_ACTIVE_SUBSCRIPTIONS_ERROR_MSG = "Topic '%s' has active subscriptions and cannot be deregistered. Delete or complete all subscriptions for this topic first.";
    public static final String TOPIC_NOT_ACTIVE_ERROR_MSG = "Topic '%s' is not active and cannot accept new subscriptions.";
    public static final String FILTER_PURPOSES_REQUIRED_FOR_SPECIFIC_ERROR_MSG = "filter.purposes must contain at least one entry when filter.type is SPECIFIC.";
    public static final String FILTER_PURPOSES_REQUIRED_FOR_EXCEPT_ERROR_MSG = "filter.purposes must contain at least one entry when filter.type is EXCEPT.";
}
