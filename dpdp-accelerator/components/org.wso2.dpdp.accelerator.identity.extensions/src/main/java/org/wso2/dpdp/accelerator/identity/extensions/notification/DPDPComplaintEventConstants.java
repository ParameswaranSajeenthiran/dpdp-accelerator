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

package org.wso2.dpdp.accelerator.identity.extensions.notification;

/**
 * Event name, handler name, and property keys for the complaint email notification flow -
 * mirrors the shape of an {@code IdentityEventConstants}-style constants holder used throughout
 * WSO2 IS's own event handlers (e.g. {@code CIBAWebLinkAuthenticatorConstants} in
 * financial-services-accelerator).
 */
public final class DPDPComplaintEventConstants {

    private DPDPComplaintEventConstants() {

    }

    /** Our own custom event - fired by {@code DPDPNotificationServlet}, consumed by {@code ComplaintNotificationHandler}. */
    public static final String COMPLAINT_NOTIFICATION_EVENT = "DPDP_COMPLAINT_NOTIFICATION_EVENT";
    public static final String NOTIFICATION_HANDLER_NAME = "dpdpComplaintNotificationHandler";

    // Notification types carried on the custom event - each maps 1:1 to a registered email
    // template type (see EmailTemplateProvisioningUtil).
    public static final String NOTIFICATION_TYPE_COMPLAINT_CREATED = "ComplaintCreated";
    public static final String NOTIFICATION_TYPE_COMMENT_ADDED = "ComplaintCommentAdded";

    // Property keys on our own custom event, set by DPDPNotificationServlet and read by
    // ComplaintNotificationHandler. Tenant domain and username reuse
    // IdentityEventConstants.EventProperty's own key constants directly instead of redefining them.
    public static final String PROP_NOTIFICATION_TYPE = "notification-type";
    public static final String PROP_COMPLAINT_ID = "complaint-id";
    public static final String PROP_REFERENCE_ID = "reference-id";
    public static final String PROP_CATEGORY = "category";
    public static final String PROP_PRIORITY = "priority";
    public static final String PROP_STATUS = "status";
    public static final String PROP_STATUTORY_DUE_TIME = "statutory-due-time";
    public static final String PROP_ACTOR_ROLE = "actor-role";
    public static final String PROP_ACTOR_USER_ID = "actor-user-id";
    public static final String PROP_ACTOR_USER_NAME = "actor-user-name";
    public static final String PROP_MESSAGE_EXCERPT = "message-excerpt";
    public static final String PROP_CREATOR_USER_ID = "creator-user-id";
    public static final String PROP_CREATOR_USER_NAME = "creator-user-name";

    // Placeholder keys the two email templates reference (see EmailTemplateProvisioningUtil).
    // Computed once in ComplaintNotificationHandler#buildTemplatePlaceholders from the raw
    // properties above, since only that class has RealmService access to resolve a display name
    // from a username, and only it knows which of the two notification directions is in play.
    public static final String PLACEHOLDER_DATA_PRINCIPAL_NAME = "data-principal-name";
    public static final String PLACEHOLDER_ACTOR_NAME = "actor-name";
    public static final String PLACEHOLDER_CATEGORY_LABEL = "category-label";
    public static final String PLACEHOLDER_PRIORITY_LABEL = "priority-label";
    public static final String PLACEHOLDER_STATUS_LABEL = "status-label";
    public static final String PLACEHOLDER_SLA_LABEL = "sla-label";
    public static final String PLACEHOLDER_ACTION_URL = "action-url";
    public static final String PLACEHOLDER_LOGO_URL = "logo-url";
    public static final String PLACEHOLDER_RECIPIENT_ROLE_LABEL = "recipient-role-label";
    public static final String PLACEHOLDER_HEADLINE_HTML = "headline-html";
    public static final String PLACEHOLDER_FOOTER_TEXT = "footer-text";

    /**
     * Property keys IS's own already-registered internal notification handler expects on a
     * standard {@code TRIGGER_NOTIFICATION} event. These are conventional string keys, not typed
     * API - their values mirror {@code org.wso2.carbon.identity.recovery.IdentityRecoveryConstants
     * .TEMPLATE_TYPE}/{@code .SEND_TO} exactly, hardcoded here rather than adding a dependency on
     * that artifact just for two string literals.
     */
    public static final String TRIGGER_PROP_TEMPLATE_TYPE = "TEMPLATE_TYPE";
    public static final String TRIGGER_PROP_SEND_TO = "send-to";
}
