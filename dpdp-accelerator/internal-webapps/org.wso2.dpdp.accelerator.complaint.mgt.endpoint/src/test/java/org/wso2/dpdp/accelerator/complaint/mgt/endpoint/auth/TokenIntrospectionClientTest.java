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

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * The org id on the returned {@link AuthenticatedPrincipal} must always come from the decoded
 * token's own {@code org_handle} claim, never be assumed from anything the caller of
 * {@link TokenIntrospectionClient#introspect} passes in - {@link TokenIntrospectionFilter} relies
 * on this to verify a request's client-supplied tenant against the token's real one.
 *
 * <p>Tokens here are unsigned (no real Identity Server keypair in a unit test) - that's fine,
 * since {@link TokenIntrospectionClient} deliberately never checks the signature; Carbon's own
 * valve is what verifies it before a real request ever reaches this code (see that class's
 * javadoc).
 */
class TokenIntrospectionClientTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final long FUTURE_EXP = Instant.now().getEpochSecond() + 3600;
    private static final long PAST_EXP = Instant.now().getEpochSecond() - 3600;

    private final TokenIntrospectionClient client = new TokenIntrospectionClient();

    private static String token(Map<String, Object> claims) {
        try {
            String header = encode(OBJECT_MAPPER.writeValueAsBytes(Map.of("alg", "none", "typ", "JWT")));
            String payload = encode(OBJECT_MAPPER.writeValueAsBytes(claims));
            return header + "." + payload + ".";
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static String encode(byte[] bytes) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static Map<String, Object> baseClaims() {
        Map<String, Object> claims = new LinkedHashMap<>();
        claims.put("client_id", "DPDP_CONSENT_PORTAL");
        claims.put("exp", FUTURE_EXP);
        return claims;
    }

    @Test
    void derivesTheOrgIdFromTheOrgHandleClaim() {
        Map<String, Object> claims = baseClaims();
        claims.put("sub", "user-123");
        claims.put("org_handle", "example.com");
        claims.put("scope", "portal:complaints:read:self");

        AuthenticatedPrincipal principal = client.introspect(token(claims));

        assertEquals("example.com", principal.getOrgId());
        assertEquals("user-123", principal.getUserId());
    }

    @Test
    void readsTheUsernameClaimWhenTheTokenCarriesOne() {
        // Only present because the app has "username" (http://wso2.org/claims/username)
        // configured as an Access Token Attribute - see this class's javadoc.
        Map<String, Object> claims = baseClaims();
        claims.put("sub", "user-123");
        claims.put("org_handle", "carbon.super");
        claims.put("username", "ctizen1");
        claims.put("scope", "");

        AuthenticatedPrincipal principal = client.introspect(token(claims));

        assertEquals("ctizen1", principal.getUserName());
    }

    @Test
    void userNameIsNullWhenATokenCarriesNoUsernameClaim() {
        // e.g. a token minted before the app's Access Token Attributes was configured to include
        // it - callers must not assume this is always set.
        Map<String, Object> claims = baseClaims();
        claims.put("sub", "user-123");
        claims.put("org_handle", "carbon.super");
        claims.put("scope", "");

        AuthenticatedPrincipal principal = client.introspect(token(claims));

        assertNull(principal.getUserName());
    }

    @Test
    void resolvesANullOrgIdWhenATokenHasNoOrgHandleClaimAtAll() {
        // Unlike the old username-suffix parsing, org_handle has no ambiguous "present but
        // unqualified" case - it's either set (definitive) or absent, and absent means the
        // tenant is unverifiable. That must fail closed (null), never silently default to some
        // tenant - see introspect's javadoc.
        Map<String, Object> claims = baseClaims();
        claims.put("sub", "user-123");
        claims.put("scope", "");

        AuthenticatedPrincipal principal = client.introspect(token(claims));

        assertNull(principal.getOrgId());
    }

    @Test
    void returnsNullWhenTheTokenIsExpired() {
        Map<String, Object> claims = baseClaims();
        claims.put("exp", PAST_EXP);
        claims.put("sub", "user-123");
        claims.put("org_handle", "example.com");
        claims.put("scope", "");

        assertNull(client.introspect(token(claims)));
    }

    @Test
    void returnsNullWhenTheTokenIsNotAWellFormedJwt() {
        assertNull(client.introspect("not-a-jwt"));
    }

    @Test
    void returnsNullWhenTheTokenWasMintedForADifferentClientApplication() {
        // Otherwise a token that is unexpired and carries the right scope strings - but was issued
        // to a completely different registered application - would be trusted here, since the
        // scope strings this endpoint checks are org-agnostic by design (token confusion).
        Map<String, Object> claims = baseClaims();
        claims.put("client_id", "SOME_OTHER_APP");
        claims.put("sub", "user-123");
        claims.put("org_handle", "example.com");
        claims.put("scope", "");

        assertNull(client.introspect(token(claims)));
    }

    @Test
    void returnsNullWhenTheTokenHasNoClientIdOrAudienceAtAll() {
        Map<String, Object> claims = new LinkedHashMap<>();
        claims.put("exp", FUTURE_EXP);
        claims.put("sub", "user-123");
        claims.put("org_handle", "example.com");
        claims.put("scope", "");

        assertNull(client.introspect(token(claims)));
    }

    @Test
    void fallsBackToTheAudienceClaimWhenClientIdIsMissing() {
        Map<String, Object> claims = new LinkedHashMap<>();
        claims.put("aud", List.of("DPDP_CONSENT_PORTAL"));
        claims.put("exp", FUTURE_EXP);
        claims.put("sub", "user-123");
        claims.put("org_handle", "example.com");
        claims.put("scope", "");

        AuthenticatedPrincipal principal = client.introspect(token(claims));

        assertEquals("user-123", principal.getUserId());
    }
}
