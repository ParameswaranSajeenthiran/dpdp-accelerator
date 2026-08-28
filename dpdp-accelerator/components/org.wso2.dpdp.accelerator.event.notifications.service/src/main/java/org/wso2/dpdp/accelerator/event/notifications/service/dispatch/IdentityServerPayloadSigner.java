/**
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

package org.wso2.dpdp.accelerator.event.notifications.service.dispatch;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.security.Signature;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

/** Creates the compact RS256 JWS used for Identity Server event authentication. */
final class IdentityServerPayloadSigner implements EventPayloadSigner {

    private static final String SIGNATURE_ALGORITHM = "SHA256withRSA";
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private final TenantSigningKeyResolver signingKeyResolver;

    IdentityServerPayloadSigner() {
        this(new IdentityServerTenantSigningKeyResolver());
    }

    IdentityServerPayloadSigner(TenantSigningKeyResolver signingKeyResolver) {
        this.signingKeyResolver = signingKeyResolver;
    }

    @Override
    public String sign(EventPayloadSigningContext context) throws Exception {

        TenantSigningKey signingKey = signingKeyResolver.resolve(context.getTenantDomain());

        Map<String, Object> header = new LinkedHashMap<>();
        header.put("alg", "RS256");
        header.put("typ", "JWT");
        header.put("kid", signingKey.getKeyId());
        header.put("x5t#S256", signingKey.getCertificateThumbprint());

        Map<String, Object> claims = new LinkedHashMap<>();
        claims.put("iss", context.getIssuer());
        claims.put("sub", context.getSubject());
        claims.put("aud", context.getAudience());
        claims.put("iat", context.getIssuedAt());
        claims.put("jti", context.getDeliveryId());
        claims.put("txn", context.getEventId());
        claims.put("payloadHash", context.getPayloadHash());
        claims.put("payload", context.getPayload());

        String encodedHeader = encode(toJson(header).getBytes(StandardCharsets.UTF_8));
        String encodedClaims = encode(toJson(claims).getBytes(StandardCharsets.UTF_8));
        String signingInput = encodedHeader + "." + encodedClaims;

        Signature signature = Signature.getInstance(SIGNATURE_ALGORITHM);
        signature.initSign(signingKey.getPrivateKey());
        signature.update(signingInput.getBytes(StandardCharsets.US_ASCII));
        return signingInput + "." + encode(signature.sign());
    }

    private static String toJson(Map<String, Object> claims) throws JsonProcessingException {

        return MAPPER.writeValueAsString(claims);
    }

    private static String encode(byte[] value) {

        return Base64.getUrlEncoder().withoutPadding().encodeToString(value);
    }
}
