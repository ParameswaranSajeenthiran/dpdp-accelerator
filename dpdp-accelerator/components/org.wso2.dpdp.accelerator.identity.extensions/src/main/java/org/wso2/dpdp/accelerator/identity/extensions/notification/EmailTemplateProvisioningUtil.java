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
import org.wso2.carbon.identity.governance.exceptions.notiification.NotificationTemplateManagerException;
import org.wso2.carbon.identity.governance.model.NotificationTemplate;
import org.wso2.carbon.identity.governance.service.notification.NotificationTemplateManager;
import org.wso2.dpdp.accelerator.identity.extensions.internal.DPDPIdentityExtensionDataHolder;

/**
 * Registers the two complaint notification email templates for a tenant, idempotently, so no
 * manual IS Console step is needed - mirrors the "only add what's missing" idiom already used for
 * role permissions in {@code DPDPConsentPortalRoleProvisioningUtil}. Called once per tenant
 * alongside role provisioning (see {@code DPDPIdentityExtensionTenantMgtListener}).
 */
public final class EmailTemplateProvisioningUtil {

    private static final Log LOG = LogFactory.getLog(EmailTemplateProvisioningUtil.class);
    private static final String EMAIL_CHANNEL = "EMAIL";
    private static final String DEFAULT_LOCALE = "en_US";
    private static final String CONTENT_TYPE = "text/html";

    private EmailTemplateProvisioningUtil() {

    }

    public static void provisionTemplates(String tenantDomain) {

        provisionTemplate(tenantDomain, DPDPComplaintEventConstants.NOTIFICATION_TYPE_COMPLAINT_CREATED,
                "New complaint filed: ${reference-id}", EMAIL_BODY);
        provisionTemplate(tenantDomain, DPDPComplaintEventConstants.NOTIFICATION_TYPE_COMMENT_ADDED,
                "New reply on complaint ${reference-id}", EMAIL_BODY);
    }

    /**
     * Shared HTML shell for both notification types - every visible piece of content is a
     * placeholder computed per-event by {@code ComplaintNotificationHandler#buildTemplatePlaceholders}
     * (headline wording, badges, the quoted message, the action link), so one body serves both
     * "a complaint was filed" and "a reply was posted" without templateType-specific branching
     * here. Table-based layout with inline styles throughout, no CSS classes/external assets/
     * gradients - the common denominator that survives Gmail's and Outlook's HTML sanitizers.
     */
    private static final String EMAIL_BODY =
            "<table role=\"presentation\" width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" "
                    + "style=\"background-color:#f3f4f6;padding:24px 0;\"><tr><td align=\"center\">"
                    + "<table role=\"presentation\" width=\"560\" cellpadding=\"0\" cellspacing=\"0\" "
                    + "style=\"background-color:#ffffff;border:1px solid #e5e7eb;border-radius:12px;"
                    + "font-family:-apple-system,Segoe UI,Roboto,Helvetica,Arial,sans-serif;\">"
                    // Header: brand + module eyebrow.
                    + "<tr><td style=\"padding:20px 28px;border-bottom:1px solid #e5e7eb;\">"
                    + "<table role=\"presentation\" width=\"100%\" cellpadding=\"0\" cellspacing=\"0\"><tr>"
                    + "<td style=\"font-size:16px;font-weight:700;color:#111827;\">"
                    + "<span style=\"display:inline-block;width:10px;height:10px;border-radius:50%;"
                    + "background-color:#f97316;margin-right:8px;\">&nbsp;</span>Consent Portal</td>"
                    + "<td align=\"right\" style=\"font-size:11px;font-weight:600;letter-spacing:0.06em;"
                    + "color:#9ca3af;text-transform:uppercase;\">Grievance Console</td>"
                    + "</tr></table></td></tr>"
                    // Body: badge, headline, detail card, quoted message, action button.
                    + "<tr><td style=\"padding:28px;\">"
                    + "<span style=\"display:inline-block;background-color:#fee2e2;color:#b91c1c;"
                    + "font-size:11px;font-weight:700;letter-spacing:0.04em;text-transform:uppercase;"
                    + "padding:4px 10px;border-radius:999px;\">Action Needed</span>"
                    + "<p style=\"margin:16px 0 24px 0;font-size:15px;line-height:1.5;color:#111827;\">"
                    + "${headline-html}</p>"
                    + "<table role=\"presentation\" width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" "
                    + "style=\"border:1px solid #e5e7eb;border-radius:8px;margin-bottom:20px;\">"
                    + detailCell("Reference ID", "${reference-id}", "Data Principal", "${data-principal-name}", true)
                    + detailCell("Subject", "${category-label}", "Priority", badge("${priority-label}"), false)
                    + detailCell("Status", badge("${status-label}"), "SLA",
                            "<span style=\"font-size:13px;font-weight:700;color:#ea580c;\">${sla-label}</span>",
                            false)
                    + "</table>"
                    + "<table role=\"presentation\" width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" "
                    + "style=\"background-color:#fff7ed;border-left:3px solid #f97316;border-radius:4px;"
                    + "margin-bottom:24px;\"><tr><td style=\"padding:14px 18px;\">"
                    + "<div style=\"font-size:13px;font-weight:700;color:#111827;margin-bottom:6px;\">"
                    + "${actor-name}</div>"
                    + "<div style=\"font-size:13px;line-height:1.5;color:#374151;\">&quot;${message-excerpt}"
                    + "&quot;</div></td></tr></table>"
                    + "<table role=\"presentation\" cellpadding=\"0\" cellspacing=\"0\" align=\"center\" "
                    + "style=\"margin:0 auto;\"><tr><td style=\"border-radius:24px;background-color:#f97316;\">"
                    + "<a href=\"${action-url}\" style=\"display:inline-block;padding:12px 32px;font-size:14px;"
                    + "font-weight:700;color:#ffffff;text-decoration:none;border-radius:24px;\">"
                    + "Review &amp; Reply</a></td></tr></table>"
                    + "</td></tr>"
                    // Footer.
                    + "<tr><td style=\"padding:20px 28px;border-top:1px solid #e5e7eb;font-size:11px;"
                    + "line-height:1.6;color:#9ca3af;text-align:center;\">"
                    + "${footer-text}<br/>"
                    + "<a href=\"${action-url}\" style=\"color:#9ca3af;text-decoration:underline;\">"
                    + "Open in Consent Portal</a>"
                    + "<p style=\"margin:12px 0 0 0;\">WSO2 LLC. All rights reserved.</p>"
                    + "</td></tr>"
                    + "</table></td></tr></table>";

    private static String badge(String textPlaceholder) {

        return "<span style=\"display:inline-block;background-color:#fee2e2;color:#b91c1c;font-size:11px;"
                + "font-weight:700;padding:3px 10px;border-radius:999px;\">" + textPlaceholder + "</span>";
    }

    /** One two-column row of the detail card - {@code topRow} adds no top border/padding (it's the card's first row). */
    private static String detailCell(String leftLabel, String leftValue, String rightLabel, String rightValue,
            boolean topRow) {

        String padding = topRow ? "padding:16px 20px;" : "padding:16px 20px 16px 20px;border-top:1px solid #e5e7eb;";
        return "<tr>"
                + "<td style=\"" + padding + "width:50%;border-right:1px solid #e5e7eb;\">"
                + "<div style=\"font-size:10px;font-weight:700;letter-spacing:0.05em;color:#9ca3af;"
                + "text-transform:uppercase;margin-bottom:4px;\">" + leftLabel + "</div>"
                + "<div style=\"font-size:14px;font-weight:600;color:#111827;\">" + leftValue + "</div></td>"
                + "<td style=\"" + padding + "width:50%;\">"
                + "<div style=\"font-size:10px;font-weight:700;letter-spacing:0.05em;color:#9ca3af;"
                + "text-transform:uppercase;margin-bottom:4px;\">" + rightLabel + "</div>"
                + "<div style=\"font-size:14px;font-weight:600;color:#111827;\">" + rightValue + "</div></td>"
                + "</tr>";
    }

    /**
     * Always (re)writes the template content - {@code addNotificationTemplate} overwrites an
     * existing type/locale/channel resource rather than failing on one, so this doubles as the
     * upgrade path when this class's own HTML/subject changes: every tenant picks up the new
     * content the next time it starts, with no separate migration step. Unlike role/permission
     * provisioning elsewhere in this bundle, these templates have no supported user-customization
     * workflow, so "add what's missing" idempotency isn't the right model here - "code always
     * wins" is. {@code addNotificationTemplateType} itself is NOT similarly upsert-safe - it
     * throws once the type is already registered for a tenant - so that failure is swallowed
     * separately and never blocks the content write below it.
     */
    private static void provisionTemplate(String tenantDomain, String templateType, String subject, String body) {

        NotificationTemplateManager templateManager = DPDPIdentityExtensionDataHolder.getInstance()
                .getNotificationTemplateManager();
        try {
            templateManager.addNotificationTemplateType(templateType, EMAIL_CHANNEL, tenantDomain);
        } catch (NotificationTemplateManagerException e) {
            LOG.debug("Notification template type '" + templateType + "' already registered for tenant '"
                    + tenantDomain + "'; continuing to (re)write its content.", e);
        }

        try {
            NotificationTemplate template = new NotificationTemplate();
            template.setType(templateType);
            template.setDisplayName(templateType);
            template.setLocale(DEFAULT_LOCALE);
            template.setNotificationChannel(EMAIL_CHANNEL);
            template.setContentType(CONTENT_TYPE);
            template.setSubject(subject);
            template.setBody(body);
            template.setFooter("");
            templateManager.addNotificationTemplate(template, tenantDomain);
            LOG.info("Provisioned email template '" + templateType + "' for tenant: " + tenantDomain);
        } catch (NotificationTemplateManagerException e) {
            LOG.error("Error provisioning email template '" + templateType + "' for tenant: " + tenantDomain, e);
        }
    }
}
