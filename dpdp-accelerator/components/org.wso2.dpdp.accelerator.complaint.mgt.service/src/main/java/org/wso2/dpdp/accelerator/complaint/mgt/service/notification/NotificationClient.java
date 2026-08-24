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

package org.wso2.dpdp.accelerator.complaint.mgt.service.notification;

import org.wso2.carbon.context.PrivilegedCarbonContext;
import org.wso2.carbon.identity.event.IdentityEventConstants;
import org.wso2.carbon.identity.event.event.Event;
import org.wso2.carbon.identity.event.services.IdentityEventService;
import org.wso2.dpdp.accelerator.complaint.mgt.dao.model.Complaint;
import org.wso2.dpdp.accelerator.complaint.mgt.dao.model.ComplaintEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Notifies the {@code org.wso2.dpdp.accelerator.identity.extensions} OSGi bundle of complaint
 * events, so its {@code ComplaintNotificationHandler} can resolve recipients and trigger IS's
 * native email notification mechanism (see that bundle's {@code notification} package).
 *
 * <p>This plain, non-OSGi module has no {@code BundleContext} of its own, but
 * {@link PrivilegedCarbonContext#getOSGiService(Class, java.util.Hashtable)} resolves OSGi
 * services via a static holder ({@code org.wso2.carbon.context.internal.OSGiDataHolder}) that is
 * populated wherever the {@code org.wso2.carbon.context} classes are loaded from Carbon's shared
 * classloader rather than bundled per-webapp - which is exactly why
 * {@code org.wso2.carbon.utils}/{@code org.wso2.carbon.identity.event} are declared {@code
 * provided} in this module's pom rather than bundled into the WAR. This is the same lookup
 * mechanism used throughout Carbon-hosted custom webapps to reach an OSGi service without being
 * an OSGi bundle themselves.
 *
 * <p>Never lets a notification failure propagate to the caller - every public method here is
 * fire-and-forget by design, since a complaint or comment write must succeed independently of
 * whether anyone could be notified about it.
 */
public class NotificationClient {

    private static final Logger LOGGER = Logger.getLogger(NotificationClient.class.getName());

    /** Mirrors identity.extensions' DPDPComplaintEventConstants - the source of truth for this event contract. */
    private static final String COMPLAINT_NOTIFICATION_EVENT = "DPDP_COMPLAINT_NOTIFICATION_EVENT";
    private static final String PROP_NOTIFICATION_TYPE = "notification-type";
    private static final String PROP_COMPLAINT_ID = "complaint-id";
    private static final String PROP_REFERENCE_ID = "reference-id";
    private static final String PROP_CATEGORY = "category";
    private static final String PROP_ACTOR_ROLE = "actor-role";
    private static final String PROP_MESSAGE_EXCERPT = "message-excerpt";
    private static final String PROP_CREATOR_USER_ID = "creator-user-id";
    private static final String PROP_CREATOR_USER_NAME = "creator-user-name";

    private static final String NOTIFICATION_TYPE_COMPLAINT_CREATED = "ComplaintCreated";
    private static final String NOTIFICATION_TYPE_COMMENT_ADDED = "ComplaintCommentAdded";

    private static final int MAX_EXCERPT_LENGTH = 300;

    private final Supplier<IdentityEventService> eventServiceSupplier;

    public NotificationClient() {
        this(() -> (IdentityEventService) PrivilegedCarbonContext.getThreadLocalCarbonContext()
                .getOSGiService(IdentityEventService.class, null));
    }

    /** Test seam - lets a test inject a mock supplier instead of a real OSGi lookup. */
    NotificationClient(Supplier<IdentityEventService> eventServiceSupplier) {
        this.eventServiceSupplier = eventServiceSupplier;
    }

    /** Notifies the complaint officers (dpdp-consent-admin role members) that a complaint was filed. */
    public void notifyComplaintCreated(Complaint complaint) {
        Map<String, Object> properties = new HashMap<>();
        properties.put(PROP_NOTIFICATION_TYPE, NOTIFICATION_TYPE_COMPLAINT_CREATED);
        properties.put(IdentityEventConstants.EventProperty.TENANT_DOMAIN, complaint.getOrgId());
        putIfPresent(properties, PROP_COMPLAINT_ID, complaint.getComplaintId());
        putIfPresent(properties, PROP_REFERENCE_ID, complaint.getReferenceId());
        putIfPresent(properties, PROP_CATEGORY, complaint.getCategory());
        fire(properties);
    }

    /**
     * Notifies the other party of a new comment: complaint officers when a citizen comments, or
     * the complaint's original creator when an officer comments (see
     * {@code ComplaintNotificationHandler} for how the actor role decides this).
     */
    public void notifyCommentAdded(Complaint complaint, ComplaintEvent event) {
        Map<String, Object> properties = new HashMap<>();
        properties.put(PROP_NOTIFICATION_TYPE, NOTIFICATION_TYPE_COMMENT_ADDED);
        properties.put(IdentityEventConstants.EventProperty.TENANT_DOMAIN, complaint.getOrgId());
        putIfPresent(properties, PROP_COMPLAINT_ID, complaint.getComplaintId());
        putIfPresent(properties, PROP_REFERENCE_ID, complaint.getReferenceId());
        putIfPresent(properties, PROP_CATEGORY, complaint.getCategory());
        putIfPresent(properties, PROP_ACTOR_ROLE, event.getActorRole());
        putIfPresent(properties, PROP_MESSAGE_EXCERPT, excerpt(event.getComment()));
        putIfPresent(properties, PROP_CREATOR_USER_ID, complaint.getUserId());
        putIfPresent(properties, PROP_CREATOR_USER_NAME, complaint.getUserName());
        fire(properties);
    }

    private static void putIfPresent(Map<String, Object> properties, String key, String value) {
        if (value != null && !value.trim().isEmpty()) {
            properties.put(key, value);
        }
    }

    private static String excerpt(String message) {
        if (message == null) {
            return "";
        }
        return message.length() <= MAX_EXCERPT_LENGTH ? message : message.substring(0, MAX_EXCERPT_LENGTH) + "...";
    }

    private void fire(Map<String, Object> properties) {
        try {
            IdentityEventService eventService = eventServiceSupplier.get();
            if (eventService == null) {
                LOGGER.warning("IdentityEventService is not resolvable via PrivilegedCarbonContext; "
                        + "complaint notification not sent.");
                return;
            }
            eventService.handleEvent(new Event(COMPLAINT_NOTIFICATION_EVENT, properties));
        } catch (Throwable t) {
            // Deliberately never rethrown - see class javadoc. The complaint/comment write this
            // is called after has already committed; a notification failure must not surface as
            // one. Catches Throwable, not just Exception, because a misconfigured deployment
            // (org.wso2.carbon.context classes not actually on Carbon's shared classloader) would
            // surface as a LinkageError/NoClassDefFoundError, not a checked exception.
            LOGGER.log(Level.WARNING, "Error sending complaint notification", t);
        }
    }
}
