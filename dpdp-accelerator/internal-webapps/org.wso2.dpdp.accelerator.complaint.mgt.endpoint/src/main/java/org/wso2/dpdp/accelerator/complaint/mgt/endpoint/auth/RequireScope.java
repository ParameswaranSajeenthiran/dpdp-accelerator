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

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a resource method as requiring an OAuth2 scope check. Carries no value itself - the
 * required scope (one of the four {@code portal:complaints:*} scopes in complaint-server-API.yaml)
 * is looked up by {@link ScopeAuthorizationFilter} from {@link ComplaintScopeRegistry}, keyed by
 * "{@code <HTTP method> <path template>}" (e.g. {@code "GET /complaints/{complaintId}"}), so the
 * actual scope-per-operation mapping lives in deployment.toml's {@code [complaintScopes]} table
 * (or the registry's built-in defaults) rather than as a literal here - see
 * {@link ComplaintScopeRegistry} for the full mapping and override format.
 *
 * <p>Every resource method on every endpoint class in this module must carry this annotation,
 * matching that operation's documented {@code security:} block.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface RequireScope {
}
