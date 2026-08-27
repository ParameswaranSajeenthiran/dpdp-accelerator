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

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * The scope each {@link RequireScope}-annotated resource method requires, keyed by
 * "{@code <HTTP method> <path template>}" (the class's {@code @Path} joined with the method's
 * {@code @Path}, e.g. {@code "POST /complaints/{complaintId}/status"}). Built-in defaults below
 * match complaint-server-API.yaml; {@code configure()} lets deployment.toml's
 * {@code [complaintScopes]} table override individual entries without a rebuild - see
 * AppBootstrap#loadDeploymentConfig in this module, invoked once at servlet context startup.
 *
 * <p>Unlike {@code PriorityMapper}'s wholesale-replace, overrides here are merged key-by-key on
 * top of the defaults: a partially-specified or malformed {@code [complaintScopes]} table
 * must not silently leave an *unmentioned* operation with no scope requirement at all - getting a
 * complaint's priority wrong is a minor annoyance, leaving an endpoint unguarded is not.
 */
public final class ComplaintScopeRegistry {

    public static final String READ_SELF = "complaints:read:self";
    public static final String WRITE_SELF = "complaints:write:self";
    public static final String READ_ANY = "complaints:read:any";
    public static final String WRITE_ANY = "complaints:write:any";

    private static final Set<String> KNOWN_SCOPES = new HashSet<>(
            Arrays.asList(READ_SELF, WRITE_SELF, READ_ANY, WRITE_ANY));

    private static volatile Map<String, String> scopeByOperation = buildDefaultMapping();

    private ComplaintScopeRegistry() {
    }

    private static Map<String, String> buildDefaultMapping() {
        Map<String, String> defaults = new HashMap<>();

        defaults.put("POST /complaints", WRITE_ANY);
        defaults.put("GET /complaints", READ_ANY);
        defaults.put("GET /complaints/stats", READ_ANY);
        defaults.put("GET /complaints/categories", READ_ANY);
        defaults.put("GET /complaints/{complaintId}", READ_ANY);
        defaults.put("POST /complaints/{complaintId}/status", WRITE_ANY);
        defaults.put("GET /complaints/{complaintId}/timeline", READ_ANY);
        defaults.put("POST /complaints/{complaintId}/comments", WRITE_ANY);
        defaults.put("POST /complaints/{complaintId}/attachments", WRITE_ANY);
        defaults.put("GET /complaints/{complaintId}/attachments/{attachmentId}", READ_ANY);

        defaults.put("POST /me/complaints", WRITE_SELF);
        defaults.put("GET /me/complaints", READ_SELF);
        defaults.put("GET /me/complaints/categories", READ_SELF);
        defaults.put("GET /me/complaints/{complaintId}", READ_SELF);
        defaults.put("POST /me/complaints/{complaintId}/status", WRITE_SELF);
        defaults.put("GET /me/complaints/{complaintId}/timeline", READ_SELF);
        defaults.put("POST /me/complaints/{complaintId}/comments", WRITE_SELF);
        defaults.put("POST /me/complaints/{complaintId}/attachments", WRITE_SELF);
        defaults.put("GET /me/complaints/{complaintId}/attachments/{attachmentId}", READ_SELF);

        return defaults;
    }

    /**
     * Merges {@code overrides} on top of the current mapping, one key at a time. An entry whose
     * value isn't one of the four known {@code complaints:*} scopes is dropped rather than
     * applied; an entry whose key doesn't match any real operation is harmless (never looked up)
     * and is kept as-is rather than rejected, so a deployment.toml written against a newer version
     * of this module still loads cleanly on an older one.
     */
    public static void configure(Map<String, String> overrides) {
        if (overrides == null || overrides.isEmpty()) {
            return;
        }
        Map<String, String> merged = new HashMap<>(scopeByOperation);
        for (Map.Entry<String, String> entry : overrides.entrySet()) {
            String operationKey = entry.getKey();
            String scope = entry.getValue() == null ? null : entry.getValue().trim();
            if (operationKey != null && KNOWN_SCOPES.contains(scope)) {
                merged.put(operationKey.trim(), scope);
            }
        }
        scopeByOperation = merged;
    }

    /** The scope required for {@code operationKey}, or {@code null} if it isn't a known operation. */
    public static String requiredScopeFor(String operationKey) {
        return scopeByOperation.get(operationKey);
    }

    /** The current mapping, defaults with any deployment.toml overrides merged in. */
    public static Map<String, String> getScopeByOperation() {
        return Collections.unmodifiableMap(scopeByOperation);
    }

    /** Test seam - configure() only ever merges, so tests that override a key must undo that via this. */
    static void resetToDefaultsForTesting() {
        scopeByOperation = buildDefaultMapping();
    }
}
