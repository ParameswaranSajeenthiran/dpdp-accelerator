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

package org.wso2.dpdp.accelerator.identity.extensions.user;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.wso2.carbon.identity.event.IdentityEventConstants;
import org.wso2.carbon.identity.event.event.Event;
import org.wso2.carbon.identity.event.handler.AbstractEventHandler;
import org.wso2.dpdp.accelerator.common.util.LogSanitizer;
import org.wso2.dpdp.accelerator.identity.extensions.util.DPDPLifecycleEventUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Captures the 2 user-scoped DPDP lifecycle events via IS's Identity Event Framework - the same
 * mechanism as IS's own {@code ConsentDeletionUserEventHandler}. Hooking here, rather than any
 * one REST controller, catches the mutation regardless of which door it came through - SCIM2,
 * Console, or SOAP.
 *
 * <p>Registered as an OSGi {@code EventHandler} in {@code DPDPIdentityExtensionServiceComponent}
 * and subscribed via {@code deployment.toml}'s {@code [[event_handler]]} - OSGi registration
 * alone does not make IS invoke it.
 */
public class DPDPUserLifecycleEventHandler extends AbstractEventHandler {

    private static final Log LOG = LogFactory.getLog(DPDPUserLifecycleEventHandler.class);

    @Override
    public String getName() {

        return "dpdpUserLifecycleEventHandler";
    }

    @Override
    public void handleEvent(Event event) {

        String eventName = event.getEventName();
        boolean isAccountDeleted = IdentityEventConstants.Event.POST_DELETE_USER.equals(eventName);
        boolean isDataChanged = IdentityEventConstants.Event.POST_SET_USER_CLAIMS.equals(eventName);
        if (!isAccountDeleted && !isDataChanged) {
            return;
        }

        DPDPLifecycleEventUtil.notify(listener -> {
            Map<String, Object> properties = event.getEventProperties();
            String tenantDomain = asString(properties.get(IdentityEventConstants.EventProperty.TENANT_DOMAIN));
            String userId = resolveUserId(properties);
            if (tenantDomain == null || userId == null) {
                LOG.debug("Missing tenant domain or user identifier on a '" + LogSanitizer.sanitize(eventName)
                        + "' event; skipping the DPDP lifecycle notification.");
                return;
            }

            if (isAccountDeleted) {
                listener.onUserAccountDeleted(tenantDomain, userId);
            } else {
                listener.onUserDataChanged(tenantDomain, userId, resolveChangedClaimUris(properties));
            }
        });
    }

    /** Prefers the opaque {@code USER_ID} property, falling back to the plain username. */
    private static String resolveUserId(Map<String, Object> properties) {

        String userId = asString(properties.get(IdentityEventConstants.EventProperty.USER_ID));
        if (userId != null) {
            return userId;
        }
        return asString(properties.get(IdentityEventConstants.EventProperty.USER_NAME));
    }

    /** Only claim URIs are surfaced, never values - claim values are frequently PII. */
    @SuppressWarnings("unchecked")
    private static List<String> resolveChangedClaimUris(Map<String, Object> properties) {

        Object rawClaims = properties.get(IdentityEventConstants.EventProperty.USER_CLAIMS);
        if (!(rawClaims instanceof Map)) {
            return null;
        }
        return new ArrayList<>(((Map<String, ?>) rawClaims).keySet());
    }

    private static String asString(Object value) {

        return value instanceof String && !((String) value).trim().isEmpty() ? (String) value : null;
    }
}
