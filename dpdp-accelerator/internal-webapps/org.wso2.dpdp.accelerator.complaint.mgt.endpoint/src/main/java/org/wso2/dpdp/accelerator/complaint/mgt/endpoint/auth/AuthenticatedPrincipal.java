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

import java.util.Collections;
import java.util.Set;

/**
 * The caller identity/authorization resolved by {@link TokenIntrospectionFilter} from a validated
 * bearer token's {@code sub}, {@code username}, and {@code scope} claims - including
 * {@link #getOrgId()}, which is derived from the token's own {@code username} claim (see
 * {@link TokenIntrospectionClient} and org.wso2.dpdp.common.util.TenantContextUtils), never from
 * anything client-supplied. Stashed on the request so resource methods and
 * {@link ScopeAuthorizationFilter} never need to re-introspect or trust anything client-supplied.
 */
public class AuthenticatedPrincipal {

    private final String userId;
    private final String userName;
    private final String orgId;
    private final Set<String> scopes;

    public AuthenticatedPrincipal(String userId, String userName, String orgId, Set<String> scopes) {
        this.userId = userId;
        this.userName = userName;
        this.orgId = orgId;
        this.scopes = scopes != null ? scopes : Collections.emptySet();
    }

    public String getUserId() {
        return userId;
    }

    /**
     * The introspection response's {@code username} field - a human-readable identity (e.g.
     * "admin@carbon.super"), as opposed to {@link #getUserId()}'s opaque {@code sub}. May be
     * {@code null} if the introspection endpoint didn't return one.
     */
    public String getUserName() {
        return userName;
    }

    public String getOrgId() {
        return orgId;
    }

    public boolean hasScope(String scope) {
        return scopes.contains(scope);
    }
}
