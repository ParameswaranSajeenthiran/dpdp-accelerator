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

package org.wso2.dpdp.common.util;

import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Resolves the acting organization/tenant id from a request path's WSO2 Carbon tenant-qualified
 * URL segment - {@code /t/{tenant-domain}/...} - shared across accelerator REST endpoints that
 * need the caller's org id but aren't dispatched through Carbon's own tenant-aware valve (e.g. a
 * webapp excluded from that valve, per its own [[resource.access_control]] entry in
 * deployment.toml).
 */
public final class TenantContextUtils {

    private static final Pattern TENANT_PATH_SEGMENT = Pattern.compile("/t/([^/]+)/");

    private TenantContextUtils() {
    }

    /**
     * Extracts the tenant domain from the first {@code /t/{tenant-domain}/} segment of
     * {@code requestPath}, if present. Returns {@code null} when the path carries no such
     * segment - e.g. a super-tenant request, or a caller that reached this endpoint without
     * going through a tenant-qualified URL at all. Callers must treat {@code null} as "the URL
     * made no tenant claim", not as "the caller claims the super tenant" - there is deliberately
     * no default here, the same fail-closed convention {@code TokenIntrospectionClient} uses for
     * the token's own org.
     *
     * <p><strong>This value is client-supplied and unauthenticated</strong> - it comes straight
     * off the URL, which the caller controls. It is only safe to use for routing/display; it must
     * never be trusted as the caller's actual org without cross-checking it against an
     * authenticated claim first.
     */
    public static String extractOrgId(String requestPath) {
        if (requestPath == null) {
            return null;
        }
        Matcher matcher = TENANT_PATH_SEGMENT.matcher(requestPath);
        if (!matcher.find()) {
            return null;
        }
        String tenantDomain = decode(matcher.group(1)).trim();
        return tenantDomain.isEmpty() ? null : tenantDomain;
    }

    private static String decode(String value) {
        try {
            return URLDecoder.decode(value, StandardCharsets.UTF_8.name());
        } catch (UnsupportedEncodingException e) {
            // UTF-8 is always supported - unreachable in practice.
            return value;
        }
    }
}
