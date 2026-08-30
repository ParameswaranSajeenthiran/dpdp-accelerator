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
import org.wso2.dpdp.accelerator.common.util.LogSanitizer;
import org.wso2.dpdp.accelerator.identity.extensions.internal.DPDPIdentityExtensionDataHolder;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * Registers the three complaint notification email templates for a tenant, idempotently, so no
 * manual IS Console step is needed - mirrors the "only add what's missing" idiom already used for
 * role permissions in {@code DPDPConsentPortalRoleProvisioningUtil}. Called once per tenant
 * alongside role provisioning (see {@code DPDPIdentityExtensionTenantMgtListener}).
 */
public final class EmailTemplateProvisioningUtil {

    private static final Log LOG = LogFactory.getLog(EmailTemplateProvisioningUtil.class);
    private static final String EMAIL_CHANNEL = "EMAIL";
    private static final String DEFAULT_LOCALE = "en_US";
    private static final String CONTENT_TYPE = "text/html";

    // Template types - each mirrors the TEMPLATE_TYPE value the notification's own trigger sets
    // (see complaint.mgt.service's EmailNotificationClient, which fires TRIGGER_NOTIFICATION
    // directly and duplicates these same three literals rather than depending on this bundle).
    private static final String TEMPLATE_TYPE_COMPLAINT_CREATED = "ComplaintCreated";
    private static final String TEMPLATE_TYPE_COMMENT_ADDED = "ComplaintCommentAdded";
    private static final String TEMPLATE_TYPE_COMPLAINT_ACKNOWLEDGED = "ComplaintAcknowledged";

    private EmailTemplateProvisioningUtil() {

    }

    public static void provisionTemplates(String tenantDomain) {

        provisionTemplate(tenantDomain, TEMPLATE_TYPE_COMPLAINT_CREATED,
                "New complaint filed: {{reference-id}}", EMAIL_BODY);
        provisionTemplate(tenantDomain, TEMPLATE_TYPE_COMMENT_ADDED,
                "New reply on complaint {{reference-id}}", EMAIL_BODY);
        provisionTemplate(tenantDomain, TEMPLATE_TYPE_COMPLAINT_ACKNOWLEDGED,
                "We've received your complaint: {{reference-id}}", EMAIL_BODY);
    }

    // Shared HTML shell for all three notification types, bundled as an OSGi resource rather than
    // an inline Java string - see that file's own header comment for what it contains and why.
    private static final String EMAIL_BODY_RESOURCE = "/notification/complaint-email-body.html";
    private static final String EMAIL_BODY = loadResource(EMAIL_BODY_RESOURCE);

    private static String loadResource(String path) {

        try (InputStream in = EmailTemplateProvisioningUtil.class.getResourceAsStream(path)) {
            if (in == null) {
                throw new IllegalStateException("Missing bundled resource: " + path);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("Error loading bundled resource: " + path, e);
        }
    }

    /**
     * Always (re)writes the template content - {@code addNotificationTemplate} upserts, so this
     * also doubles as the upgrade path when the HTML/subject here changes.
     * {@code addNotificationTemplateType} is not upsert-safe (throws if already registered), so
     * that failure is swallowed separately and never blocks the content write below it.
     */
    private static void provisionTemplate(String tenantDomain, String templateType, String subject, String body) {

        NotificationTemplateManager templateManager = DPDPIdentityExtensionDataHolder.getInstance()
                .getNotificationTemplateManager();
        try {
            templateManager.addNotificationTemplateType(templateType, EMAIL_CHANNEL, tenantDomain);
        } catch (NotificationTemplateManagerException e) {
            LOG.debug("Notification template type '" + templateType + "' already registered for tenant '"
                    + LogSanitizer.sanitize(tenantDomain) + "'; continuing to (re)write its content.", e);
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
            LOG.info("Provisioned email template '" + templateType + "' for tenant: "
                    + LogSanitizer.sanitize(tenantDomain));
        } catch (NotificationTemplateManagerException e) {
            LOG.error("Error provisioning email template '" + templateType + "' for tenant: "
                    + LogSanitizer.sanitize(tenantDomain), e);
        }
    }
}
