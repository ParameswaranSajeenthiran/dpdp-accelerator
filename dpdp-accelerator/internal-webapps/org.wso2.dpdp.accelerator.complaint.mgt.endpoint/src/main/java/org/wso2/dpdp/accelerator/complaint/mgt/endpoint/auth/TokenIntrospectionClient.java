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

package org.wso2.dpdp.accelerator.complaint.mgt.endpoint.auth;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.wso2.carbon.context.PrivilegedCarbonContext;

import java.util.Collections;

/**
 * Resolves a caller's identity from Carbon's own request-scoped {@link PrivilegedCarbonContext} -
 * the sole source of identity for every request this endpoint serves.
 *
 * <p>The access token is opaque (see {@code DPDPConsentPortalAppProvisioningUtil}'s
 * {@code tokenType = "Default"}), matching IS My Account/Console. Carbon's own valve pipeline
 * (the {@code [[resource.access_control]]} entries for {@code /api/dpdp/complaints} in
 * {@code wso2is-7.3.0-deployment.toml}, {@code allowed_auth_handlers = ["OAuthAuthentication"]})
 * already introspects the token before this webapp ever sees the request, and populates
 * {@link PrivilegedCarbonContext} with the resolved caller - confirmed empirically to work for
 * this plain-Tomcat-WAR webapp exactly the same way it does for an OSGi-registered Carbon service
 * like {@code ConsentHistorySelfApi.requireCallerOwnsConsent()}. There is deliberately no second,
 * in-process introspection call (no credentials to manage, nothing to call out to) - re-verifying
 * what Carbon's valve already verified would be redundant, not additional safety.
 *
 * <p>This class does not resolve OAuth scopes - {@link PrivilegedCarbonContext} carries identity
 * and tenant only. Scope enforcement for each operation is already done, per route, by the same
 * {@code [[resource.access_control]]} entries (each carries its own {@code scopes = [...]}), so
 * there is nothing left for this webapp to re-check once a request has reached it - see {@link
 * ScopeAuthorizationFilter}.
 */
public class TokenIntrospectionClient {

    private static final Log LOG = LogFactory.getLog(TokenIntrospectionClient.class);

    /**
     * Returns the resolved principal for the current request, or {@code null} if
     * {@link PrivilegedCarbonContext} carries no resolvable username - Carbon's valve is expected
     * to have already rejected any request that got this far without one, so reaching this is
     * effectively "unverifiable", not a normal case.
     */
    public AuthenticatedPrincipal introspect(String token) {
        if (token == null || token.trim().isEmpty()) {
            return null;
        }
        try {
            PrivilegedCarbonContext context = PrivilegedCarbonContext.getThreadLocalCarbonContext();
            String username = context.getUsername();
            String tenantDomain = context.getTenantDomain();
            if (username == null || username.trim().isEmpty()) {
                return null;
            }
            String userId = username.trim();
            return new AuthenticatedPrincipal(userId, userId, tenantDomain, Collections.emptySet());
        } catch (Exception e) {
            LOG.warn("Could not resolve the caller's identity from PrivilegedCarbonContext.", e);
            return null;
        }
    }
}
