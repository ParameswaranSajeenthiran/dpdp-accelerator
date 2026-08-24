/**
 * Copyright (c) 2026, WSO2 LLC. (https://www.wso2.com).
 * <p>
 * WSO2 LLC. licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 *     http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.wso2.dpdp.accelerator.event.notifications.endpoint.util;

import org.wso2.carbon.context.PrivilegedCarbonContext;
import org.wso2.dpdp.accelerator.event.notifications.service.constants.EventNotificationServiceConstants;
import org.wso2.dpdp.accelerator.event.notifications.service.exception.EventNotificationException;

/**
 * Utility for resolving and validating tenant domain and organization ID
 * across event notification REST endpoints.
 */
public class EndpointTenantUtils {

    private EndpointTenantUtils() {
    }

    /**
     * Resolves the organization ID from the Carbon/URL context and cross-verifies
     * it against the authenticated token tenant context.
     *
     * @param headerOrgId Optional org-id passed in HTTP request header.
     * @return Validated non-empty organization ID / tenant domain.
     * @throws EventNotificationException If token tenant mismatches URL context or orgId cannot be resolved.
     */
    public static String resolveAndValidateOrgId(String headerOrgId) {
        String urlTenant = null;
        String tokenTenant = null;

        try {
            urlTenant = PrivilegedCarbonContext.getThreadLocalCarbonContext().getTenantDomain();
            String username = PrivilegedCarbonContext.getThreadLocalCarbonContext().getUsername();
            if (username != null && username.contains("@")) {
                tokenTenant = username.substring(username.lastIndexOf("@") + 1).trim();
            }
        } catch (Throwable ignored) {
            // Standalone test environment where Carbon runtime context is uninitialized
        }

        // 1. Cross-check: If token tenant is present, ensure it matches URL tenant context
        if (urlTenant != null && !urlTenant.isBlank() && !"carbon.super".equalsIgnoreCase(urlTenant)
                && tokenTenant != null && !tokenTenant.isBlank() && !"carbon.super".equalsIgnoreCase(tokenTenant)) {
            if (!urlTenant.equalsIgnoreCase(tokenTenant)) {
                throw new EventNotificationException(
                        EventNotificationServiceConstants.ERROR_CODE_INVALID_REQUEST,
                        EventNotificationServiceConstants.ERROR_TITLE_OPERATION_FORBIDDEN,
                        "Token tenant domain [" + tokenTenant + "] does not match URL tenant context [" + urlTenant + "].",
                        403);
            }
        }

        // 2. If URL tenant context is non-super, use it
        if (urlTenant != null && !urlTenant.isBlank() && !"carbon.super".equalsIgnoreCase(urlTenant)) {
            return urlTenant.trim();
        }

        // 3. If header orgId is supplied, cross-check against token tenant if present
        if (headerOrgId != null && !headerOrgId.isBlank()) {
            String trimmedHeader = headerOrgId.trim();
            if (tokenTenant != null && !tokenTenant.isBlank() && !"carbon.super".equalsIgnoreCase(tokenTenant)) {
                if (!trimmedHeader.equalsIgnoreCase(tokenTenant)) {
                    throw new EventNotificationException(
                            EventNotificationServiceConstants.ERROR_CODE_INVALID_REQUEST,
                            EventNotificationServiceConstants.ERROR_TITLE_OPERATION_FORBIDDEN,
                            "Token tenant domain [" + tokenTenant + "] does not match header org-id [" + trimmedHeader + "].",
                            403);
                }
            }
            return trimmedHeader;
        }

        // 4. If URL context is carbon.super, fallback to carbon.super
        if (urlTenant != null && !urlTenant.isBlank()) {
            return urlTenant.trim();
        }

        throw new EventNotificationException(
                EventNotificationServiceConstants.ERROR_CODE_INVALID_REQUEST,
                EventNotificationServiceConstants.ERROR_TITLE_MALFORMED_REQUEST,
                EventNotificationServiceConstants.ORG_ID_MISSING_ERROR_MSG,
                400);
    }
}
