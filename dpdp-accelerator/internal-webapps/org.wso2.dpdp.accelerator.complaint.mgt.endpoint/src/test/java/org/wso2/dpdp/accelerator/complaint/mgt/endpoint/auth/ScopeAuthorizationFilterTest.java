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

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.container.ResourceInfo;
import java.lang.reflect.Method;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.lenient;

@ExtendWith(MockitoExtension.class)
class ScopeAuthorizationFilterTest {

    /** A tiny fixture resource class - only its @Path/@RequireScope shape matters, it's never routed. */
    @Path("/complaints/{complaintId}")
    private static class FixtureResource {

        @GET
        @RequireScope
        void gated() {
        }

        @POST
        @Path("/status")
        @RequireScope
        void gatedWithSubPath() {
        }

        void ungated() {
        }
    }

    @Mock
    private ResourceInfo resourceInfo;
    @Mock
    private ContainerRequestContext requestContext;

    private ScopeAuthorizationFilter filter;

    @BeforeEach
    void setUp() {
        filter = new ScopeAuthorizationFilter();
        filter.setResourceInfo(resourceInfo);
        lenient().when(resourceInfo.getResourceClass()).thenAnswer(invocation -> FixtureResource.class);
    }

    @AfterEach
    void restoreDefaults() {
        ComplaintScopeRegistry.resetToDefaultsForTesting();
    }

    private Method gatedMethod() throws NoSuchMethodException {
        return FixtureResource.class.getDeclaredMethod("gated");
    }

    private Method ungatedMethod() throws NoSuchMethodException {
        return FixtureResource.class.getDeclaredMethod("ungated");
    }

    private AuthenticatedPrincipal principalWithScopes(String... scopes) {
        return new AuthenticatedPrincipal("user1", "User One", "carbon.super",
                Set.of(scopes));
    }

    // ---- operationKey ----

    @Test
    void operationKeyJoinsClassAndMethodPathsWithTheHttpMethod() throws NoSuchMethodException {
        assertEquals("GET /complaints/{complaintId}",
                ScopeAuthorizationFilter.operationKey("GET", FixtureResource.class, gatedMethod()));
    }

    @Test
    void operationKeyAppendsAMethodLevelPathToTheClassPath() throws NoSuchMethodException {
        Method gatedWithSubPath = FixtureResource.class.getDeclaredMethod("gatedWithSubPath");

        assertEquals("POST /complaints/{complaintId}/status",
                ScopeAuthorizationFilter.operationKey("POST", FixtureResource.class, gatedWithSubPath));
    }

    // ---- filter ----
    //
    // No-op since the switch to opaque access tokens - Carbon's own valve pipeline already
    // enforces each operation's required scope, per route, before this webapp ever sees the
    // request (see the class javadoc). filter() never throws regardless of resource method,
    // principal, or scope state.

    @Test
    void filterDoesNothingWhenTheResourceMethodHasNoRequireScopeAnnotation() throws NoSuchMethodException {
        lenient().when(resourceInfo.getResourceMethod()).thenReturn(ungatedMethod());

        assertDoesNotThrow(() -> filter.filter(requestContext));
    }

    @Test
    void filterDoesNothingEvenWhenNoPrincipalWasResolved() throws NoSuchMethodException {
        lenient().when(resourceInfo.getResourceMethod()).thenReturn(gatedMethod());
        lenient().when(requestContext.getProperty(TokenIntrospectionFilter.PRINCIPAL_PROPERTY)).thenReturn(null);

        assertDoesNotThrow(() -> filter.filter(requestContext));
    }

    @Test
    void filterDoesNothingEvenWhenThePrincipalHasNoScopes() throws NoSuchMethodException {
        lenient().when(resourceInfo.getResourceMethod()).thenReturn(gatedMethod());
        lenient().when(requestContext.getProperty(TokenIntrospectionFilter.PRINCIPAL_PROPERTY))
                .thenReturn(principalWithScopes());

        assertDoesNotThrow(() -> filter.filter(requestContext));
    }
}
