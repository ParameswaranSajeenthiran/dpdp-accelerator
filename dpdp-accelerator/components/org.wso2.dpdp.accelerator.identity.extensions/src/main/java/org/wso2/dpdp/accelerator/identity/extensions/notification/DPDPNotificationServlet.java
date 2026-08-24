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
import org.wso2.carbon.identity.event.IdentityEventConstants;
import org.wso2.carbon.identity.event.IdentityEventException;
import org.wso2.carbon.identity.event.event.Event;
import org.wso2.dpdp.accelerator.identity.extensions.internal.DPDPIdentityExtensionDataHolder;

import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.HashMap;
import java.util.Map;

import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * The bridge between the complaint feature's plain (non-OSGi) WAR and this bundle's
 * {@link org.wso2.carbon.identity.event.services.IdentityEventService}: registered via OSGi
 * {@code HttpService} (see {@code DPDPIdentityExtensionServiceComponent}), since a plain WAR
 * deployed into Carbon's Tomcat has no other way to reach an OSGi service.
 *
 * <p>Accepts only loopback requests - both callers run inside the same JVM/Tomcat instance in
 * this deployment model, so a shared-secret header is unnecessary; anything not from
 * {@code 127.0.0.1}/{@code ::1} is rejected outright. The body is a plain
 * {@code application/x-www-form-urlencoded} form (not JSON) precisely so this bundle needs no new
 * JSON-parsing dependency for what both callers already treat as a small, fixed-shape payload.
 */
public class DPDPNotificationServlet extends HttpServlet {

    private static final long serialVersionUID = 1L;
    private static final Log LOG = LogFactory.getLog(DPDPNotificationServlet.class);

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {

        if (!isLoopback(request.getRemoteAddr())) {
            LOG.warn("Rejected a complaint notification request from non-loopback address: "
                    + request.getRemoteAddr());
            response.sendError(HttpServletResponse.SC_FORBIDDEN);
            return;
        }

        String notificationType = request.getParameter(DPDPComplaintEventConstants.PROP_NOTIFICATION_TYPE);
        String tenantDomain = request.getParameter(IdentityEventConstants.EventProperty.TENANT_DOMAIN);
        if (notificationType == null || notificationType.trim().isEmpty()
                || tenantDomain == null || tenantDomain.trim().isEmpty()) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST, "notification-type and tenant-domain are "
                    + "required.");
            return;
        }

        Map<String, Object> properties = new HashMap<>();
        properties.put(DPDPComplaintEventConstants.PROP_NOTIFICATION_TYPE, notificationType);
        properties.put(IdentityEventConstants.EventProperty.TENANT_DOMAIN, tenantDomain);
        copyParameter(request, properties, DPDPComplaintEventConstants.PROP_COMPLAINT_ID);
        copyParameter(request, properties, DPDPComplaintEventConstants.PROP_REFERENCE_ID);
        copyParameter(request, properties, DPDPComplaintEventConstants.PROP_CATEGORY);
        copyParameter(request, properties, DPDPComplaintEventConstants.PROP_ACTOR_ROLE);
        copyParameter(request, properties, DPDPComplaintEventConstants.PROP_MESSAGE_EXCERPT);
        copyParameter(request, properties, DPDPComplaintEventConstants.PROP_CREATOR_USER_ID);
        copyParameter(request, properties, DPDPComplaintEventConstants.PROP_CREATOR_USER_NAME);

        Event event = new Event(DPDPComplaintEventConstants.COMPLAINT_NOTIFICATION_EVENT, properties);
        try {
            DPDPIdentityExtensionDataHolder.getInstance().getIdentityEventService().handleEvent(event);
            response.setStatus(HttpServletResponse.SC_ACCEPTED);
        } catch (IdentityEventException e) {
            LOG.error("Error handling complaint notification event for tenant: " + tenantDomain, e);
            response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
        }
    }

    private static void copyParameter(HttpServletRequest request, Map<String, Object> properties, String name) {

        String value = request.getParameter(name);
        if (value != null && !value.trim().isEmpty()) {
            properties.put(name, value.trim());
        }
    }

    private static boolean isLoopback(String remoteAddr) {

        if (remoteAddr == null || remoteAddr.trim().isEmpty()) {
            return false;
        }
        try {
            return InetAddress.getByName(remoteAddr).isLoopbackAddress();
        } catch (UnknownHostException e) {
            return false;
        }
    }
}
