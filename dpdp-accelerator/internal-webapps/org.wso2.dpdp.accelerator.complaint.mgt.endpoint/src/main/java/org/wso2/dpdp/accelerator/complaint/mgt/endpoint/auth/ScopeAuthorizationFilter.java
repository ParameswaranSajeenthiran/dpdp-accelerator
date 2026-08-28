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

import javax.annotation.Priority;
import javax.ws.rs.Path;
import javax.ws.rs.Priorities;
import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.container.ContainerRequestFilter;
import javax.ws.rs.container.ResourceInfo;
import javax.ws.rs.core.Context;
import javax.ws.rs.ext.Provider;
import java.lang.reflect.Method;

/**
 * No-op since the switch to opaque access tokens: scope enforcement for every operation in this
 * module is already done, per route, by Carbon's own valve pipeline (the
 * {@code [[resource.access_control]]} entries for {@code /api/dpdp/complaints} in
 * {@code wso2is-7.3.0-deployment.toml}, each carrying its own {@code scopes = [...]}) before this
 * webapp ever sees the request. {@link TokenIntrospectionClient} resolves identity from Carbon's
 * {@code PrivilegedCarbonContext} rather than introspecting the token itself, so there is no
 * scope list left on {@link AuthenticatedPrincipal} to re-check here - re-verifying what Carbon's
 * valve already verified would be redundant, not additional safety. Kept registered (rather than
 * removed outright) so {@link RequireScope}/{@link ComplaintScopeRegistry} - which still document
 * each operation's required scope for readers of the code and for the deployment.toml-driven
 * override table - don't need to be ripped out alongside this.
 */
@Provider
@Priority(Priorities.AUTHORIZATION)
public class ScopeAuthorizationFilter implements ContainerRequestFilter {

    @Context
    private ResourceInfo resourceInfo;

    /** Test seam - Jersey normally field-injects this via @Context. */
    void setResourceInfo(ResourceInfo resourceInfo) {
        this.resourceInfo = resourceInfo;
    }

    @Override
    public void filter(ContainerRequestContext requestContext) {
        // Intentionally a no-op - see class javadoc.
    }

    /**
     * Builds the "{@code <HTTP method> <path template>}" key {@link ComplaintScopeRegistry} is
     * keyed by, from the resource class's {@code @Path} joined with the matched method's own
     * {@code @Path} (methods with no method-level {@code @Path} contribute nothing beyond the
     * class path). Every endpoint in this module is a flat root-resource class with plain
     * {@code @GET}/{@code @POST} methods - no sub-resource locators, no regex path segments - so
     * this simple concatenation exactly reproduces each method's routed path.
     */
    static String operationKey(String httpMethod, Class<?> resourceClass, Method resourceMethod) {
        String classPath = resourceClass.isAnnotationPresent(Path.class)
                ? resourceClass.getAnnotation(Path.class).value() : "";
        String methodPath = resourceMethod.isAnnotationPresent(Path.class)
                ? resourceMethod.getAnnotation(Path.class).value() : "";
        String fullPath = (classPath + "/" + methodPath).replaceAll("/+", "/");
        if (fullPath.length() > 1 && fullPath.endsWith("/")) {
            fullPath = fullPath.substring(0, fullPath.length() - 1);
        }
        return httpMethod + " " + fullPath;
    }
}
