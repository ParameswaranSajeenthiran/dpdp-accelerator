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

import org.wso2.carbon.identity.core.IdentityKeyStoreResolver;
import org.wso2.carbon.identity.core.util.IdentityKeyStoreResolverConstants.InboundProtocol;

import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.security.MessageDigest;
import java.security.PrivateKey;
import java.security.cert.Certificate;
import java.util.Base64;

/** Uses Identity Server's tenant-aware OAuth keystore resolution and certificate selection. */
final class IdentityServerTenantSigningKeyResolver implements TenantSigningKeyResolver {

    private final IdentityKeyStoreResolver keyStoreResolver;

    IdentityServerTenantSigningKeyResolver() {
        this(null);
    }

    IdentityServerTenantSigningKeyResolver(IdentityKeyStoreResolver keyStoreResolver) {
        this.keyStoreResolver = keyStoreResolver;
    }

    @Override
    public TenantSigningKey resolve(String tenantDomain) throws Exception {
        if (tenantDomain == null || tenantDomain.trim().isEmpty()) {
            throw new IllegalArgumentException("Tenant domain is required for event payload signing.");
        }

        String normalizedTenantDomain = tenantDomain.trim();
        IdentityKeyStoreResolver resolver = keyStoreResolver != null
                ? keyStoreResolver : IdentityKeyStoreResolver.getInstance();
        Key key = resolver.getPrivateKey(normalizedTenantDomain, InboundProtocol.OAUTH);
        if (!(key instanceof PrivateKey)) {
            throw new IllegalStateException("Identity Server tenant signing key is not a private key.");
        }

        Certificate certificate = resolver.getCertificate(normalizedTenantDomain, InboundProtocol.OAUTH);
        if (certificate == null) {
            throw new IllegalStateException("Identity Server tenant signing certificate is unavailable.");
        }

        byte[] thumbprint = MessageDigest.getInstance("SHA-256").digest(certificate.getEncoded());
        String certificateThumbprint = Base64.getUrlEncoder().withoutPadding().encodeToString(thumbprint);
        // Identity Server's default OAuth KeyIDProvider uses the URL-safe Base64 encoding
        // of the lowercase SHA-256 certificate digest followed by the JWS algorithm.
        String keyId = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(toLowerHex(thumbprint).getBytes(StandardCharsets.UTF_8)) + "_RS256";
        return new TenantSigningKey((PrivateKey) key, keyId, certificateThumbprint);
    }

    private static String toLowerHex(byte[] value) {
        StringBuilder hex = new StringBuilder(value.length * 2);
        for (byte current : value) {
            hex.append(Character.forDigit((current >>> 4) & 0x0F, 16));
            hex.append(Character.forDigit(current & 0x0F, 16));
        }
        return hex.toString();
    }
}
