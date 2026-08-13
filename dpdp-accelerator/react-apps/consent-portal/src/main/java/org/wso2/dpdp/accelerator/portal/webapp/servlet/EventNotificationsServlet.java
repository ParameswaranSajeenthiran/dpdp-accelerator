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

package org.wso2.dpdp.accelerator.portal.webapp.servlet;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.wso2.dpdp.accelerator.portal.webapp.client.IdentityServerClient;
import org.wso2.dpdp.accelerator.portal.webapp.util.AuthUtil;
import org.wso2.dpdp.accelerator.portal.webapp.util.HttpUtil;

import java.io.IOException;

import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * Proxy servlet for DPDP Event Notification REST API endpoints
 * (/api/dpdp/event-notifications/*).
 * Resolves split-token cookies / bearer headers and proxies calls upstream on
 * behalf of signed-in users.
 */
@WebServlet(urlPatterns = "/api/event-notifications/*")
public class EventNotificationsServlet extends AbstractProxyServlet {

    private static final long serialVersionUID = 1L;
    private static final Log LOG = LogFactory.getLog(EventNotificationsServlet.class);

    @Override
    protected void service(HttpServletRequest request, HttpServletResponse response) throws IOException {

        String method = request.getMethod();
        if (!"GET".equals(method) && !"POST".equals(method) && !"DELETE".equals(method)) {
            HttpUtil.sendError(response, HttpServletResponse.SC_METHOD_NOT_ALLOWED, "METHOD_NOT_ALLOWED",
                    method + " is not supported by this endpoint.");
            return;
        }
        dispatch(request, response, method);
    }

    private void dispatch(HttpServletRequest request, HttpServletResponse response, String method)
            throws IOException {

        String path = request.getPathInfo() == null ? "" : request.getPathInfo();
        IdentityServerClient client = resolveClient(request, response);
        if (client == null) {
            return;
        }

        String orgId = resolveOrgId(request);
        String groupId = AuthUtil.resolveGroupId(request);

        String query = request.getQueryString();
        String target = IdentityServerClient.EVENT_NOTIFICATION_API + path
                + (query == null || query.trim().isEmpty() ? "" : "?" + query);

        try {
            String body = "POST".equals(method) ? readBody(request) : null;
            IdentityServerClient.Result result = client.forwardEventNotificationRequest(method, target, body, orgId,
                    groupId);
            relay(result, response);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            LOG.error("Upstream request to Event Notification API was interrupted.", e);
            sendUpstreamFailure(response);
        } catch (Exception e) {
            LOG.error("Failed to proxy Event Notification request upstream.", e);
            sendUpstreamFailure(response);
        }
    }
}
