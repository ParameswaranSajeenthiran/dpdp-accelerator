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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.wso2.dpdp.common.config.ConfigProvider;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.Iterator;

/**
 * Decodes a bearer token's claims to resolve the caller's identity, org, and scopes - the sole
 * source of identity for every request this endpoint serves.
 **/

public class TokenIntrospectionClient {

    private static final String CONFIG_EXPECTED_CLIENT_ID = "dpdp_accelerator.consent_portal.client_id";

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final String expectedClientId;

    public TokenIntrospectionClient() {
        this.expectedClientId = ConfigProvider.getString(CONFIG_EXPECTED_CLIENT_ID,
                System.getProperty("CO_INTROSPECT_EXPECTED_CLIENT_ID", "DPDP_CONSENT_PORTAL"));
    }

    /**
     * Returns the resolved principal for a well-formed, unexpired, correctly-audienced JWT, or
     * {@code null} if the token is malformed/expired/mis-audienced. The principal's org id is
     * derived from the token's own {@code org_handle} claim - WSO2 IS's own tenant domain claim,
     * e.g. {@code "carbon.super"} - never from anything the caller supplies, so it is safe for
     * {@link TokenIntrospectionFilter} to check it against the client-supplied, unauthenticated org
     * id parsed off the request path. Unlike the old {@code username}-suffix parsing this replaced,
     * {@code org_handle} has no ambiguous "present but unqualified" case - it's either set (and
     * definitive) or absent, and an absent one means the tenant is unverifiable, which
     * {@link AuthenticatedPrincipal#getOrgId()} then reports as {@code null} rather than silently
     * defaulting to some tenant. There is deliberately no caller-supplied default for this reason.
     */
    public AuthenticatedPrincipal introspect(String token) {
        JsonNode claims = decodeClaims(token);
        if (claims == null) {
            return null;
        }

        long expEpochSeconds = claims.path("exp").asLong(-1);
        if (expEpochSeconds < 0 || Instant.now().isAfter(Instant.ofEpochSecond(expEpochSeconds))) {
            return null;
        }

        String sub = claims.path("sub").asText(null);
        if (sub == null || sub.trim().isEmpty()) {
            return null;
        }

        // Without this, a token minted for a completely different registered application - but
        // carrying the same complaints:* scope strings, which are org-agnostic by design -
        // would be accepted here too. Pinning to the one client this endpoint is meant to serve
        // closes that token-confusion gap. WSO2 IS JWT access tokens carry the client id as both a
        // "client_id" claim and (per the JWT "aud" convention) inside "aud" - prefer the explicit
        // claim, falling back to "aud" if a token ever omits it.
        String tokenClientId = claims.path("client_id").asText(null);
        if (tokenClientId == null) {
            tokenClientId = firstAudienceValue(claims.path("aud"));
        }
        if (!expectedClientId.equals(tokenClientId)) {
            return null;
        }

        // Present only because the DPDP Consent Portal application is configured with "username"
        // (local claim http://wso2.org/claims/username) as an Access Token Attribute - WSO2 IS
        // access tokens don't carry a human-readable username by default, only the opaque "sub".
        // A token minted before that attribute was added (or by some other, misconfigured client)
        // won't have it, so callers must not assume this is set.
        String resolvedUsername = claims.path("username").asText(null);

        String scopeStr = claims.path("scope").asText("");
        Set<String> scopes = scopeStr.trim().isEmpty()
                ? Collections.emptySet()
                : new HashSet<>(Arrays.asList(scopeStr.trim().split("\\s+")));

        // org_handle is WSO2 IS's own tenant domain claim (e.g. "carbon.super") - set by the
        // token issuer, never by the caller, so it's safe to check against the client-supplied,
        // unauthenticated org id parsed off the request path. A token that is ever issued without
        // it must fail closed (null - "unverifiable"), not be assumed to belong to some default
        // tenant - see this method's javadoc.
        String orgHandle = claims.path("org_handle").asText(null);
        String tokenOrgId = orgHandle != null && !orgHandle.trim().isEmpty() ? orgHandle.trim() : null;

        return new AuthenticatedPrincipal(sub.trim(), resolvedUsername, tokenOrgId, scopes);
    }

    /**
     * Base64url-decodes a compact JWT's middle (payload) segment and parses it as JSON - no
     * signature check, see this class's javadoc for why that's safe here. Returns {@code null} for
     * anything that isn't a well-formed three-segment JWT with a JSON object payload.
     */
    private JsonNode decodeClaims(String token) {
        if (token == null) {
            return null;
        }
        // limit=-1 so a trailing empty segment (an unsigned "alg":"none" token's empty signature)
        // isn't silently dropped by the default split() behaviour, which would make a genuinely
        // 3-segment token look like only 2.
        String[] segments = token.split("\\.", -1);
        if (segments.length != 3) {
            return null;
        }
        try {
            byte[] payload = Base64.getUrlDecoder().decode(segments[1]);
            return objectMapper.readTree(new String(payload, StandardCharsets.UTF_8));
        } catch (IllegalArgumentException | java.io.IOException e) {
            return null;
        }
    }

    private static String firstAudienceValue(JsonNode audNode) {
        if (audNode.isArray()) {
            Iterator<JsonNode> elements = audNode.elements();
            List<String> values = new ArrayList<>();
            elements.forEachRemaining(element -> values.add(element.asText(null)));
            return values.isEmpty() ? null : values.get(0);
        }
        return audNode.isMissingNode() ? null : audNode.asText(null);
    }
}
