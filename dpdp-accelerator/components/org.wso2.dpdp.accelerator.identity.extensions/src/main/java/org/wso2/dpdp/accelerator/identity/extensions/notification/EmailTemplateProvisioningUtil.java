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
                "New complaint filed: ${reference-id}",
                "<p>A new complaint has been filed and needs your attention.</p>"
                        + "<p>Reference: ${reference-id}<br/>Category: ${category}</p>");
        provisionTemplate(tenantDomain, DPDPComplaintEventConstants.NOTIFICATION_TYPE_COMMENT_ADDED,
                "New comment on complaint ${reference-id}",
                "<p>A new comment has been added to complaint <strong>${reference-id}</strong>:</p>"
                        + "<p>${message-excerpt}</p>");
    }

    private static void provisionTemplate(String tenantDomain, String templateType, String subject, String body) {

        NotificationTemplateManager templateManager = DPDPIdentityExtensionDataHolder.getInstance()
                .getNotificationTemplateManager();
        try {
            NotificationTemplate existing = templateManager.getNotificationTemplate(EMAIL_CHANNEL, templateType,
                    DEFAULT_LOCALE, tenantDomain);
            if (existing != null) {
                LOG.debug("Email template '" + templateType + "' already exists for tenant: " + tenantDomain);
                return;
            }
        } catch (NotificationTemplateManagerException e) {
            LOG.debug("Email template '" + templateType + "' not resolvable for tenant '" + tenantDomain
                    + "'; provisioning it.", e);
        }

        try {
            templateManager.addNotificationTemplateType(templateType, EMAIL_CHANNEL, tenantDomain);

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
