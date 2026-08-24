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

import org.wso2.dpdp.accelerator.complaint.mgt.dao.constants.DAOConstants;
import org.wso2.dpdp.accelerator.complaint.mgt.service.exception.ComplaintErrorCode;
import org.wso2.dpdp.accelerator.complaint.mgt.service.exception.ComplaintException;
import org.wso2.dpdp.common.util.TenantContextUtils;

import javax.annotation.Priority;
import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.Priorities;
import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.container.ContainerRequestFilter;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.ext.Provider;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Runs on every request to this endpoint. Carbon's own session-based valve does not gate this
 * webapp by default (see the [[resource.access_control]] entries for /api/dpdp/complaints in
 * wso2is-7.3.0-deployment.toml) - per-route entries there have Carbon's OAuthAuthentication
 * handler independently validate the bearer token's signature/expiry/revocation status and the
 * required portal:complaints:* scope before this webapp ever sees the request. This filter is the
 * authoritative, in-process gate on top of that: it extracts the Authorization: Bearer token,
 * decodes its claims via {@link TokenIntrospectionClient} (no signature re-check - see that
 * class's javadoc for why relying on Carbon's valve for that is safe), and stashes the resolved
 * {@link AuthenticatedPrincipal} as a request property for {@link ScopeAuthorizationFilter} and
 * resource methods to read.
 */
@Provider
@Priority(Priorities.AUTHENTICATION)
public class TokenIntrospectionFilter implements ContainerRequestFilter {

    public static final String PRINCIPAL_PROPERTY = "complaint.authenticatedPrincipal";

    private static final Logger LOGGER = Logger.getLogger(TokenIntrospectionFilter.class.getName());
    private static final String BEARER_PREFIX = "Bearer ";

    private static final String FORWARD_REQUEST_URI_ATTRIBUTE = "javax.servlet.forward.request_uri";

    private final TokenIntrospectionClient introspectionClient;

    @Context
    private HttpServletRequest httpServletRequest;

    public TokenIntrospectionFilter() {
        this(new TokenIntrospectionClient());
    }

    public TokenIntrospectionFilter(TokenIntrospectionClient introspectionClient) {
        this.introspectionClient = introspectionClient;
    }

    /** Test seam - Jersey normally field-injects this via @Context. */
    void setHttpServletRequest(HttpServletRequest httpServletRequest) {
        this.httpServletRequest = httpServletRequest;
    }

    @Override
    public void filter(ContainerRequestContext requestContext) {
        String authHeader = requestContext.getHeaderString(HttpHeaders.AUTHORIZATION);
        if (authHeader == null || !authHeader.regionMatches(true, 0, BEARER_PREFIX, 0, BEARER_PREFIX.length())) {
            throw new ComplaintException(ComplaintErrorCode.UNAUTHENTICATED,
                    "Missing or malformed Authorization header; expected 'Bearer <token>'.");
        }
        String token = authHeader.substring(BEARER_PREFIX.length()).trim();
        if (token.isEmpty()) {
            throw new ComplaintException(ComplaintErrorCode.UNAUTHENTICATED, "Missing bearer token.");
        }

        // /t/{tenant-domain}/ is a WSO2 Carbon tenant-qualified URL segment. This webapp is
        // registered under [tenant_context.rewrite] custom_webapps (see wso2is-7.3.0-deployment.toml)
        // so Tomcat's TenantContextRewriteValve can dispatch /t/{tenant}/api/dpdp/complaints/...
        // to it at all - but that valve does an internal forward that strips the /t/{tenant}/
        // prefix before this servlet ever sees the request, so UriInfo's path never carries it.
        // The servlet spec guarantees the pre-forward URI survives in this standard request
        // attribute, so read from there first and only fall back to UriInfo's path for a request
        // that reached this webapp some other way (e.g. a direct, unqualified super-tenant call).
        // Falls back further to DAOConstants.DEFAULT_ORG_ID when neither carries a /t/.../ segment.
        //
        // IMPORTANT: this value is client-supplied and UNAUTHENTICATED - the caller can put
        // anything here. It must never be assigned to the principal directly; it only tells us
        // which tenant the caller is claiming to act as, which we then verify below against the
        // tenant the validated token actually belongs to.
        String forwardedRequestUri = httpServletRequest == null ? null
                : (String) httpServletRequest.getAttribute(FORWARD_REQUEST_URI_ATTRIBUTE);
        String pathToInspect = forwardedRequestUri != null ? forwardedRequestUri
                : requestContext.getUriInfo().getRequestUri().getPath();
        String requestedOrgId = TenantContextUtils.extractOrgId(pathToInspect, DAOConstants.DEFAULT_ORG_ID);

        AuthenticatedPrincipal principal;
        try {
            principal = introspectionClient.introspect(token);
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Failed to decode bearer token claims", e);
            throw new ComplaintException(ComplaintErrorCode.UNAUTHENTICATED, "Could not decode the bearer token.");
        }

        if (principal == null) {
            throw new ComplaintException(ComplaintErrorCode.UNAUTHENTICATED, "Bearer token is not active.");
        }

        // The token's own org (derived server-side from the introspection response) must match
        // the org the caller's URL claims to be acting against. A token that is perfectly valid
        // for its own tenant must not be usable to read or write another tenant's complaints
        // merely by changing the /t/{tenant-domain}/ segment of the URL. A null org here means
        // the token's tenant could not be determined at all (e.g. introspection didn't return a
        // username claim) - that is treated as unverifiable, not as a pass.
        if (principal.getOrgId() == null || !principal.getOrgId().equals(requestedOrgId)) {
            throw new ComplaintException(ComplaintErrorCode.FORBIDDEN,
                    "Bearer token does not belong to the requested tenant.");
        }

        requestContext.setProperty(PRINCIPAL_PROPERTY, principal);
    }

    /** Retrieves the principal this filter resolved for the current request. Never null once past this filter. */
    public static AuthenticatedPrincipal currentPrincipal(ContainerRequestContext requestContext) {
        Object principal = requestContext.getProperty(PRINCIPAL_PROPERTY);
        if (!(principal instanceof AuthenticatedPrincipal)) {
            throw new ComplaintException(ComplaintErrorCode.UNAUTHENTICATED, "No authenticated principal resolved.");
        }
        return (AuthenticatedPrincipal) principal;
    }
}
