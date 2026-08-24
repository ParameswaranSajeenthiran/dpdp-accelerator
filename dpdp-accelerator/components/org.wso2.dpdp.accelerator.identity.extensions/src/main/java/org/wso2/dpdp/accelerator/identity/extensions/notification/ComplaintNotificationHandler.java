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
import org.wso2.carbon.identity.event.IdentityEventConstants;
import org.wso2.carbon.identity.event.IdentityEventException;
import org.wso2.carbon.identity.event.bean.IdentityEventMessageContext;
import org.wso2.carbon.identity.event.event.Event;
import org.wso2.carbon.identity.event.handler.AbstractEventHandler;
import org.wso2.dpdp.accelerator.identity.extensions.internal.DPDPIdentityExtensionDataHolder;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

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

        List<ComplaintNotificationRecipientResolver.Recipient> recipients = resolveRecipients(properties,
                tenantDomain, notificationType);
        if (recipients.isEmpty()) {
            LOG.warn("No recipients resolved for a '" + notificationType + "' complaint notification in tenant '"
                    + tenantDomain + "'; nothing to send.");
            return;
        }

        for (ComplaintNotificationRecipientResolver.Recipient recipient : recipients) {
            triggerNotification(recipient, tenantDomain, notificationType, properties);
        }
    }

    private List<ComplaintNotificationRecipientResolver.Recipient> resolveRecipients(Map<String, Object> properties,
            String tenantDomain, String notificationType) {

        String actorRole = (String) properties.get(DPDPComplaintEventConstants.PROP_ACTOR_ROLE);
        // "COMPLAINT_OFFICER" mirrors ComplaintActorRole.COMPLAINT_OFFICER.name() from the
        // complaint.mgt.dao module - not depended on directly here, since the actor role already
        // crosses the process boundary as a plain string (see DPDPNotificationServlet's payload).
        boolean notifyCreator = DPDPComplaintEventConstants.NOTIFICATION_TYPE_COMMENT_ADDED.equals(notificationType)
                && "COMPLAINT_OFFICER".equals(actorRole);

        if (!notifyCreator) {
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
     * Fires a standard {@code TRIGGER_NOTIFICATION} event for one recipient - the compulsory
     * attributes IS's own notification handler expects are {@code send-to}, {@code user-name} and
     * a template type (see {@link DPDPComplaintEventConstants}).
     */
    private void triggerNotification(ComplaintNotificationRecipientResolver.Recipient recipient,
            String tenantDomain, String notificationType, Map<String, Object> complaintProperties) {

        Map<String, Object> triggerProperties = new HashMap<>();
        triggerProperties.put(DPDPComplaintEventConstants.TRIGGER_PROP_SEND_TO, recipient.getEmail());
        triggerProperties.put(IdentityEventConstants.EventProperty.USER_NAME, recipient.getUsername());
        triggerProperties.put(IdentityEventConstants.EventProperty.TENANT_DOMAIN, tenantDomain);
        triggerProperties.put(DPDPComplaintEventConstants.TRIGGER_PROP_TEMPLATE_TYPE, notificationType);
        // Placeholders the registered email template body/subject reference.
        triggerProperties.put(DPDPComplaintEventConstants.PROP_REFERENCE_ID,
                complaintProperties.get(DPDPComplaintEventConstants.PROP_REFERENCE_ID));
        triggerProperties.put(DPDPComplaintEventConstants.PROP_CATEGORY,
                complaintProperties.get(DPDPComplaintEventConstants.PROP_CATEGORY));
        triggerProperties.put(DPDPComplaintEventConstants.PROP_MESSAGE_EXCERPT,
                complaintProperties.get(DPDPComplaintEventConstants.PROP_MESSAGE_EXCERPT));

        Event triggerEvent = new Event(IdentityEventConstants.Event.TRIGGER_NOTIFICATION, triggerProperties);
        try {
            DPDPIdentityExtensionDataHolder.getInstance().getIdentityEventService().handleEvent(triggerEvent);
        } catch (IdentityEventException e) {
            LOG.error("Error triggering '" + notificationType + "' notification email to '" + recipient.getEmail()
                    + "' in tenant: " + tenantDomain, e);
        }
    }
}
