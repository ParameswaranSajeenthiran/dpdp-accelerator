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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.wso2.dpdp.accelerator.complaint.mgt.service.exception.ComplaintException;

import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.core.UriInfo;
import java.net.URI;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Covers the tenant-boundary check this filter enforces: the org id parsed off the request path
 * is client-supplied and must never be trusted directly - it must match the org id
 * {@link TokenIntrospectionClient} derives from the validated token itself.
 */
@ExtendWith(MockitoExtension.class)
class TokenIntrospectionFilterTest {

    @Mock
    private TokenIntrospectionClient introspectionClient;
    @Mock
    private ContainerRequestContext requestContext;
    @Mock
    private UriInfo uriInfo;

    private TokenIntrospectionFilter filter;

    @BeforeEach
    void setUp() {
        filter = new TokenIntrospectionFilter(introspectionClient);
        lenient().when(requestContext.getHeaderString("Authorization")).thenReturn("Bearer token123");
        lenient().when(requestContext.getUriInfo()).thenReturn(uriInfo);
    }

    private void requestPath(String path) {
        when(uriInfo.getRequestUri()).thenReturn(URI.create("https://host" + path));
    }

    @Test
    void rejectsWhenNoAuthorizationHeaderIsPresent() {
        when(requestContext.getHeaderString("Authorization")).thenReturn(null);

        ComplaintException ex = assertThrows(ComplaintException.class, () -> filter.filter(requestContext));

        assertEquals("CO-4010", ex.getCode());
    }

    @Test
    void rejectsWhenIntrospectionReturnsNoActivePrincipal() throws Exception {
        requestPath("/t/example.com/api/dpdp/complaints/v1/complaints");
        when(introspectionClient.introspect(anyString())).thenReturn(null);

        ComplaintException ex = assertThrows(ComplaintException.class, () -> filter.filter(requestContext));

        assertEquals("CO-4010", ex.getCode());
    }

    @Test
    void acceptsWhenTheTokensOwnTenantMatchesTheRequestedTenant() throws Exception {
        requestPath("/t/example.com/api/dpdp/complaints/v1/complaints");
        AuthenticatedPrincipal principal =
                new AuthenticatedPrincipal("alice", "alice@example.com", "example.com", Set.of());
        when(introspectionClient.introspect(anyString())).thenReturn(principal);

        assertDoesNotThrow(() -> filter.filter(requestContext));

        verify(requestContext).setProperty(TokenIntrospectionFilter.PRINCIPAL_PROPERTY, principal);
    }

    @Test
    void rejectsWithForbiddenWhenTheTokenBelongsToADifferentTenantThanTheUrlClaims() throws Exception {
        // A token that is perfectly valid for "example.com" must not grant access merely because
        // the caller changed the /t/{tenant-domain}/ segment of the URL to "other-tenant.com".
        requestPath("/t/other-tenant.com/api/dpdp/complaints/v1/complaints");
        AuthenticatedPrincipal principal =
                new AuthenticatedPrincipal("alice", "alice@example.com", "example.com", Set.of());
        when(introspectionClient.introspect(anyString())).thenReturn(principal);

        ComplaintException ex = assertThrows(ComplaintException.class, () -> filter.filter(requestContext));

        assertEquals("CO-4030", ex.getCode());
    }

    @Test
    void rejectsWithForbiddenWhenTheTokensTenantCannotBeDetermined() throws Exception {
        // introspect() returns a principal with a null orgId when the introspection response
        // carried no username claim at all - the tenant is unverifiable, so this must fail
        // closed rather than silently trusting the URL as before.
        requestPath("/t/example.com/api/dpdp/complaints/v1/complaints");
        AuthenticatedPrincipal principal = new AuthenticatedPrincipal("alice", null, null, Set.of());
        when(introspectionClient.introspect(anyString())).thenReturn(principal);

        ComplaintException ex = assertThrows(ComplaintException.class, () -> filter.filter(requestContext));

        assertEquals("CO-4030", ex.getCode());
    }

    @Test
    void acceptsASuperTenantTokenAgainstAnUnqualifiedRequestPath() throws Exception {
        requestPath("/api/dpdp/complaints/v1/complaints");
        AuthenticatedPrincipal principal = new AuthenticatedPrincipal("admin", "admin", "carbon.super", Set.of());
        when(introspectionClient.introspect(anyString())).thenReturn(principal);

        assertDoesNotThrow(() -> filter.filter(requestContext));
    }

    /**
     * [tenant_context.rewrite] custom_webapps makes Tomcat's TenantContextRewriteValve internally
     * forward /t/{tenant}/api/dpdp/complaints/... to this webapp's own context, stripping the
     * /t/{tenant}/ prefix before UriInfo ever sees it - so a request that reached this filter with
     * a bare (unqualified-looking) UriInfo path must still resolve the real tenant from the
     * standard servlet forward attribute, not silently fall back to the super tenant.
     */
    @Test
    void resolvesTheRequestedTenantFromTheForwardAttributeWhenTheValveStrippedThePathPrefix() throws Exception {
        HttpServletRequest httpServletRequest = org.mockito.Mockito.mock(HttpServletRequest.class);
        when(httpServletRequest.getAttribute("javax.servlet.forward.request_uri"))
                .thenReturn("/t/example.com/api/dpdp/complaints/v1/complaints");
        filter.setHttpServletRequest(httpServletRequest);
        AuthenticatedPrincipal principal =
                new AuthenticatedPrincipal("alice", "alice@example.com", "example.com", Set.of());
        when(introspectionClient.introspect(anyString())).thenReturn(principal);

        assertDoesNotThrow(() -> filter.filter(requestContext));

        verify(requestContext).setProperty(TokenIntrospectionFilter.PRINCIPAL_PROPERTY, principal);
    }

    @Test
    void rejectsWithForbiddenWhenTheForwardAttributeTenantDiffersFromTheTokensTenant() throws Exception {
        HttpServletRequest httpServletRequest = org.mockito.Mockito.mock(HttpServletRequest.class);
        when(httpServletRequest.getAttribute("javax.servlet.forward.request_uri"))
                .thenReturn("/t/other-tenant.com/api/dpdp/complaints/v1/complaints");
        filter.setHttpServletRequest(httpServletRequest);
        AuthenticatedPrincipal principal =
                new AuthenticatedPrincipal("alice", "alice@example.com", "example.com", Set.of());
        when(introspectionClient.introspect(anyString())).thenReturn(principal);

        ComplaintException ex = assertThrows(ComplaintException.class, () -> filter.filter(requestContext));

        assertEquals("CO-4030", ex.getCode());
    }
}
