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

import org.wso2.dpdp.accelerator.complaint.mgt.service.exception.ComplaintErrorCode;
import org.wso2.dpdp.accelerator.complaint.mgt.service.exception.ComplaintException;

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
 * Runs after {@link TokenIntrospectionFilter} on every request. Reads the {@link RequireScope}
 * annotation off the matched resource method, looks up the scope it requires for this specific
 * operation in {@link ComplaintScopeRegistry}, and enforces it against the scopes
 * {@link TokenIntrospectionFilter} resolved for the caller's token - a plain global filter reading
 * per-method metadata via {@code ResourceInfo}, deliberately not JAX-RS name-binding, to keep a
 * single filter class instead of one filter per scope value.
 *
 * <p>A resource method with no {@link RequireScope} annotation is not gated by this filter at
 * all - every method on every endpoint in this module carries one, so in practice nothing here is
 * ever left open by omission. A method that does carry the annotation but whose operation key has
 * no entry in {@link ComplaintScopeRegistry} (a deployment.toml override typo'd the key, say) fails
 * closed with an internal error rather than silently letting the request through.
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
        Method resourceMethod = resourceInfo.getResourceMethod();
        RequireScope requireScope = resourceMethod != null
                ? resourceMethod.getAnnotation(RequireScope.class)
                : null;
        if (requireScope == null) {
            return;
        }

        String operationKey = operationKey(requestContext.getMethod(), resourceInfo.getResourceClass(),
                resourceMethod);
        String requiredScope = ComplaintScopeRegistry.requiredScopeFor(operationKey);
        if (requiredScope == null) {
            throw new ComplaintException(ComplaintErrorCode.INTERNAL_ERROR,
                    "No scope configured for operation '" + operationKey + "'.");
        }

        Object principalObj = requestContext.getProperty(TokenIntrospectionFilter.PRINCIPAL_PROPERTY);
        if (!(principalObj instanceof AuthenticatedPrincipal)) {
            // TokenIntrospectionFilter runs first and always either sets this or throws - reaching
            // here without it means the two filters are misregistered/out of priority order.
            throw new ComplaintException(ComplaintErrorCode.UNAUTHENTICATED, "No authenticated principal resolved.");
        }

        AuthenticatedPrincipal principal = (AuthenticatedPrincipal) principalObj;
        if (!principal.hasScope(requiredScope)) {
            throw new ComplaintException(ComplaintErrorCode.FORBIDDEN,
                    "Bearer token is missing required scope '" + requiredScope + "'.");
        }
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
