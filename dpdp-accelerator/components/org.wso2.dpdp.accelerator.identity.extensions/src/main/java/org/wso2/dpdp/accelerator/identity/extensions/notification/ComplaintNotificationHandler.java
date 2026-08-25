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

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.wso2.carbon.identity.base.IdentityRuntimeException;
import org.wso2.carbon.identity.core.bean.context.MessageContext;
import org.wso2.carbon.identity.core.util.IdentityUtil;
import org.wso2.carbon.identity.event.IdentityEventConstants;
import org.wso2.carbon.identity.event.IdentityEventException;
import org.wso2.carbon.identity.event.bean.IdentityEventMessageContext;
import org.wso2.carbon.identity.event.event.Event;
import org.wso2.carbon.identity.event.handler.AbstractEventHandler;
import org.wso2.dpdp.accelerator.identity.extensions.internal.DPDPIdentityExtensionDataHolder;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

/**
 * Handles the DPDP complaint notification event: resolves who to notify (complaint officers, or
 * the complaint's original creator - see {@link ComplaintNotificationRecipientResolver}), then
 * fires a standard {@code TRIGGER_NOTIFICATION} event per recipient so IS's own already-registered
 * internal notification handler does the real templated-email + SMTP dispatch. Mirrors
 * financial-services-accelerator's {@code CIBAWebLinkNotificationHandler} shape (an
 * {@link AbstractEventHandler} that filters on a custom event name in {@link #canHandle}), but -
 * unlike that class, which fully owns delivery via a custom {@code NotificationProvider} - this
 * handler deliberately re-enters {@link org.wso2.carbon.identity.event.services.IdentityEventService}
 * with a standard event so IS's real template/SMTP mechanism runs, rather than reimplementing mail
 * delivery here.
 */
public class ComplaintNotificationHandler extends AbstractEventHandler {

    private static final Log LOG = LogFactory.getLog(ComplaintNotificationHandler.class);

    // Mirrors ComplaintActorRole.COMPLAINT_OFFICER.name() from the complaint.mgt.dao module - not
    // depended on directly here, since the actor role already crosses the process boundary as a
    // plain string (see NotificationClient's event properties).
    private static final String ACTOR_ROLE_COMPLAINT_OFFICER = "COMPLAINT_OFFICER";
    // Mirrors the portal frontend's own status labels exactly (complaintDisplay.ts's
    // STATUS_LABEL_KEYS + public/i18n/en/common.json's complaints.status.* strings) rather than a
    // narrative computed here, so the same complaint shows the same status word in both places.
    private static final Map<String, String> STATUS_LABELS = buildStatusLabels();
    private static final String ROLE_LABEL_GRIEVANCE_OFFICER = "Grievance Officer";
    private static final String ROLE_LABEL_DATA_PRINCIPAL = "Data Principal";
    // The portal has two separate routes for the same complaint (App.tsx): "/complaints/:id" is
    // the citizen's own self-service view (COMPLAINTS_READ_SELF scope, backed by the /me/
    // complaints/{id} API), "/complaint-management/:id" is the officer's view (COMPLAINTS_READ_ANY
    // scope, backed by /complaints/{id}). Linking an officer to the citizen route 404s (the /me/
    // API only ever resolves the caller's own complaints) - so which path to use depends on who
    // this email is actually going to, not just the complaint id.
    private static final String CREATOR_DEEP_LINK_PATH = "/consent-portal/complaints/";
    private static final String OFFICER_DEEP_LINK_PATH = "/consent-portal/complaint-management/";
    // Same PNG the portal itself serves next to its "Consent Portal" brand title (see
    // MainLayout.tsx's Header.BrandLogo) - referenced by a hosted URL rather than embedded as a
    // data: URI, since several mail clients strip inline base64 images.
    private static final String LOGO_PATH = "/consent-portal/wso2-logo.png";

    @Override
    public boolean canHandle(MessageContext messageContext) throws IdentityRuntimeException {

        Event event = ((IdentityEventMessageContext) messageContext).getEvent();
        return event.getEventName().equals(DPDPComplaintEventConstants.COMPLAINT_NOTIFICATION_EVENT);
    }

    @Override
    public String getName() {

        return DPDPComplaintEventConstants.NOTIFICATION_HANDLER_NAME;
    }

    @Override
    public void handleEvent(Event event) throws IdentityEventException {

        Map<String, Object> properties = event.getEventProperties();
        String tenantDomain = (String) properties.get(IdentityEventConstants.EventProperty.TENANT_DOMAIN);
        String notificationType = (String) properties.get(DPDPComplaintEventConstants.PROP_NOTIFICATION_TYPE);
        if (tenantDomain == null || notificationType == null) {
            LOG.warn("Complaint notification event is missing tenant domain or notification type; ignoring.");
            return;
        }

        String actorRole = (String) properties.get(DPDPComplaintEventConstants.PROP_ACTOR_ROLE);
        boolean notifyingCreator = DPDPComplaintEventConstants.NOTIFICATION_TYPE_COMMENT_ADDED.equals(notificationType)
                && ACTOR_ROLE_COMPLAINT_OFFICER.equals(actorRole);

        List<ComplaintNotificationRecipientResolver.Recipient> recipients = resolveRecipients(properties,
                tenantDomain, notifyingCreator);
        if (recipients.isEmpty()) {
            LOG.warn("No recipients resolved for a '" + notificationType + "' complaint notification in tenant '"
                    + tenantDomain + "'; nothing to send.");
            return;
        }

        Map<String, Object> placeholders = buildTemplatePlaceholders(properties, tenantDomain, notificationType,
                notifyingCreator);
        for (ComplaintNotificationRecipientResolver.Recipient recipient : recipients) {
            triggerNotification(recipient, tenantDomain, notificationType, placeholders);
        }
    }

    private List<ComplaintNotificationRecipientResolver.Recipient> resolveRecipients(Map<String, Object> properties,
            String tenantDomain, boolean notifyingCreator) {

        if (!notifyingCreator) {
            // Either the complaint was just created (always notify officers), or a citizen
            // commented (notify officers) - both resolve to the same officer-role lookup.
            return ComplaintNotificationRecipientResolver.resolveOfficers(tenantDomain);
        }

        String creatorUserId = (String) properties.get(DPDPComplaintEventConstants.PROP_CREATOR_USER_ID);
        String creatorUserName = (String) properties.get(DPDPComplaintEventConstants.PROP_CREATOR_USER_NAME);
        Optional<ComplaintNotificationRecipientResolver.Recipient> creator = ComplaintNotificationRecipientResolver
                .resolveCreator(creatorUserId, creatorUserName, tenantDomain);
        return creator.map(Collections::singletonList).orElseGet(Collections::emptyList);
    }

    /**
     * Computes every display-ready placeholder the two HTML templates reference (see
     * {@link EmailTemplateProvisioningUtil}) - the same values for every recipient of a given
     * event, since they describe the complaint/comment itself rather than who's reading it.
     */
    private Map<String, Object> buildTemplatePlaceholders(Map<String, Object> properties, String tenantDomain,
            String notificationType, boolean notifyingCreator) {

        String creatorUserId = (String) properties.get(DPDPComplaintEventConstants.PROP_CREATOR_USER_ID);
        String creatorUserName = (String) properties.get(DPDPComplaintEventConstants.PROP_CREATOR_USER_NAME);
        String dataPrincipalName = ComplaintNotificationRecipientResolver
                .resolveUsername(creatorUserId, creatorUserName, tenantDomain)
                .map(username -> ComplaintNotificationRecipientResolver.resolveDisplayName(username, tenantDomain))
                .orElse(ROLE_LABEL_DATA_PRINCIPAL);

        String actorName;
        if (!DPDPComplaintEventConstants.NOTIFICATION_TYPE_COMMENT_ADDED.equals(notificationType)) {
            // A new complaint was filed - the citizen who filed it is both the actor and the
            // data principal.
            actorName = dataPrincipalName;
        } else if (notifyingCreator) {
            String actorUserId = (String) properties.get(DPDPComplaintEventConstants.PROP_ACTOR_USER_ID);
            String actorUserName = (String) properties.get(DPDPComplaintEventConstants.PROP_ACTOR_USER_NAME);
            actorName = ComplaintNotificationRecipientResolver.resolveUsername(actorUserId, actorUserName,
                    tenantDomain)
                    .map(username -> ComplaintNotificationRecipientResolver.resolveDisplayName(username,
                            tenantDomain))
                    .orElse(ROLE_LABEL_GRIEVANCE_OFFICER);
        } else {
            actorName = dataPrincipalName;
        }

        String status = (String) properties.get(DPDPComplaintEventConstants.PROP_STATUS);
        String statusLabel = STATUS_LABELS.getOrDefault(status, humanize(status));

        String complaintId = (String) properties.get(DPDPComplaintEventConstants.PROP_COMPLAINT_ID);
        String deepLinkPath = notifyingCreator ? CREATOR_DEEP_LINK_PATH : OFFICER_DEEP_LINK_PATH;
        String actionUrl = complaintId == null ? IdentityUtil.getServerURL("/consent-portal/", true, false)
                : IdentityUtil.getServerURL(deepLinkPath + complaintId, true, false);

        String referenceId = (String) properties.get(DPDPComplaintEventConstants.PROP_REFERENCE_ID);
        boolean isCommentAdded = DPDPComplaintEventConstants.NOTIFICATION_TYPE_COMMENT_ADDED.equals(notificationType);
        String verb = isCommentAdded ? "replied to complaint" : "filed a new complaint";
        String headlineHtml = "<strong>" + htmlEscape(actorName) + "</strong> " + verb + " <strong>"
                + htmlEscape(referenceId) + "</strong>.";
        String footerText = notifyingCreator
                ? "You're receiving this because you filed this complaint."
                : "You're receiving this because you're the assigned Grievance Officer - this is an "
                        + "automated notification, please don't reply directly to it.";

        Map<String, Object> placeholders = new HashMap<>();
        placeholders.put(DPDPComplaintEventConstants.PROP_REFERENCE_ID, referenceId);
        placeholders.put(DPDPComplaintEventConstants.PROP_MESSAGE_EXCERPT,
                properties.get(DPDPComplaintEventConstants.PROP_MESSAGE_EXCERPT));
        placeholders.put(DPDPComplaintEventConstants.PLACEHOLDER_DATA_PRINCIPAL_NAME, dataPrincipalName);
        placeholders.put(DPDPComplaintEventConstants.PLACEHOLDER_ACTOR_NAME, actorName);
        placeholders.put(DPDPComplaintEventConstants.PLACEHOLDER_CATEGORY_LABEL,
                humanize((String) properties.get(DPDPComplaintEventConstants.PROP_CATEGORY)));
        placeholders.put(DPDPComplaintEventConstants.PLACEHOLDER_PRIORITY_LABEL,
                humanize((String) properties.get(DPDPComplaintEventConstants.PROP_PRIORITY)));
        placeholders.put(DPDPComplaintEventConstants.PLACEHOLDER_STATUS_LABEL, statusLabel);
        placeholders.put(DPDPComplaintEventConstants.PLACEHOLDER_SLA_LABEL,
                slaLabel((String) properties.get(DPDPComplaintEventConstants.PROP_STATUTORY_DUE_TIME)));
        placeholders.put(DPDPComplaintEventConstants.PLACEHOLDER_ACTION_URL, actionUrl);
        placeholders.put(DPDPComplaintEventConstants.PLACEHOLDER_LOGO_URL,
                IdentityUtil.getServerURL(LOGO_PATH, true, false));
        placeholders.put(DPDPComplaintEventConstants.PLACEHOLDER_RECIPIENT_ROLE_LABEL,
                notifyingCreator ? ROLE_LABEL_DATA_PRINCIPAL : ROLE_LABEL_GRIEVANCE_OFFICER);
        placeholders.put(DPDPComplaintEventConstants.PLACEHOLDER_HEADLINE_HTML, headlineHtml);
        placeholders.put(DPDPComplaintEventConstants.PLACEHOLDER_FOOTER_TEXT, footerText);
        return placeholders;
    }

    private static String htmlEscape(String value) {

        if (value == null) {
            return "";
        }
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }

    private static Map<String, String> buildStatusLabels() {

        Map<String, String> labels = new HashMap<>();
        labels.put("OPEN", "Open");
        labels.put("IN_PROGRESS", "In Progress");
        labels.put("WAITING_ON_CLIENT", "Waiting on Client");
        labels.put("AWAITING_INTERNAL_REVIEW", "Waiting on Internal Review");
        labels.put("RESOLVED", "Resolved");
        return labels;
    }

    /** {@code "UNAUTHORIZED_DATA_SHARING"} / {@code "CRITICAL"} -> {@code "Unauthorized Data Sharing"} / {@code "Critical"}. */
    private static String humanize(String value) {

        if (value == null || value.trim().isEmpty()) {
            return "";
        }
        StringBuilder result = new StringBuilder();
        for (String word : value.trim().split("_")) {
            if (word.isEmpty()) {
                continue;
            }
            if (result.length() > 0) {
                result.append(' ');
            }
            result.append(Character.toUpperCase(word.charAt(0)))
                    .append(word.substring(1).toLowerCase(Locale.ENGLISH));
        }
        return result.toString();
    }

    private static String slaLabel(String statutoryDueTimeMillis) {

        if (statutoryDueTimeMillis == null) {
            return "";
        }
        try {
            long dueTime = Long.parseLong(statutoryDueTimeMillis);
            long remainingMillis = dueTime - System.currentTimeMillis();
            long remainingDays = (long) Math.ceil(remainingMillis / (double) TimeUnit.DAYS.toMillis(1));
            if (remainingDays > 1) {
                return remainingDays + " days left";
            } else if (remainingDays == 1) {
                return "1 day left";
            } else if (remainingDays == 0) {
                return "Due today";
            }
            long overdueDays = Math.abs(remainingDays);
            return "Overdue by " + overdueDays + (overdueDays == 1 ? " day" : " days");
        } catch (NumberFormatException e) {
            return "";
        }
    }

    /**
     * Fires a standard {@code TRIGGER_NOTIFICATION} event for one recipient - the compulsory
     * attributes IS's own notification handler expects are {@code send-to}, {@code user-name} and
     * a template type (see {@link DPDPComplaintEventConstants}).
     */
    private void triggerNotification(ComplaintNotificationRecipientResolver.Recipient recipient,
            String tenantDomain, String notificationType, Map<String, Object> placeholders) {

        Map<String, Object> triggerProperties = new HashMap<>(placeholders);
        triggerProperties.put(DPDPComplaintEventConstants.TRIGGER_PROP_SEND_TO, recipient.getEmail());
        triggerProperties.put(IdentityEventConstants.EventProperty.USER_NAME, recipient.getUsername());
        triggerProperties.put(IdentityEventConstants.EventProperty.TENANT_DOMAIN, tenantDomain);
        triggerProperties.put(DPDPComplaintEventConstants.TRIGGER_PROP_TEMPLATE_TYPE, notificationType);

        Event triggerEvent = new Event(IdentityEventConstants.Event.TRIGGER_NOTIFICATION, triggerProperties);
        try {
            DPDPIdentityExtensionDataHolder.getInstance().getIdentityEventService().handleEvent(triggerEvent);
        } catch (IdentityEventException e) {
            LOG.error("Error triggering '" + notificationType + "' notification email to '" + recipient.getEmail()
                    + "' in tenant: " + tenantDomain, e);
        }
    }
}
