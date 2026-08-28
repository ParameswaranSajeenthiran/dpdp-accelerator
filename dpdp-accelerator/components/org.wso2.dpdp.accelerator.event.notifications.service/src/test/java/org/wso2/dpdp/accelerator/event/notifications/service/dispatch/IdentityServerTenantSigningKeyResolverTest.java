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

import org.testng.annotations.Test;
import org.wso2.carbon.identity.core.IdentityKeyStoreResolver;
import org.wso2.carbon.identity.core.util.IdentityKeyStoreResolverConstants.InboundProtocol;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.security.cert.Certificate;
import java.util.Base64;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertSame;
import static org.testng.Assert.expectThrows;

/** Tests Identity Server tenant key selection without loading a Carbon runtime. */
public class IdentityServerTenantSigningKeyResolverTest {

    @Test
    public void testResolvesOAuthTenantKeyAndCertificateThumbprint() throws Exception {
        IdentityKeyStoreResolver identityResolver = mock(IdentityKeyStoreResolver.class);
        Certificate certificate = mock(Certificate.class);
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        KeyPair keyPair = generator.generateKeyPair();
        byte[] encodedCertificate = "tenant-certificate".getBytes(java.nio.charset.StandardCharsets.UTF_8);

        when(identityResolver.getPrivateKey("tenant.example", InboundProtocol.OAUTH))
                .thenReturn(keyPair.getPrivate());
        when(identityResolver.getCertificate("tenant.example", InboundProtocol.OAUTH))
                .thenReturn(certificate);
        when(certificate.getEncoded()).thenReturn(encodedCertificate);

        TenantSigningKey signingKey =
                new IdentityServerTenantSigningKeyResolver(identityResolver).resolve(" tenant.example ");

        assertSame(signingKey.getPrivateKey(), keyPair.getPrivate());
        byte[] certificateDigest = MessageDigest.getInstance("SHA-256").digest(encodedCertificate);
        StringBuilder hex = new StringBuilder(certificateDigest.length * 2);
        for (byte current : certificateDigest) {
            hex.append(String.format("%02x", current));
        }
        assertEquals(signingKey.getKeyId(), Base64.getUrlEncoder().withoutPadding()
                .encodeToString(hex.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8)) + "_RS256");
        assertEquals(signingKey.getCertificateThumbprint(),
                Base64.getUrlEncoder().withoutPadding().encodeToString(certificateDigest));
        verify(identityResolver).getPrivateKey("tenant.example", InboundProtocol.OAUTH);
        verify(identityResolver).getCertificate("tenant.example", InboundProtocol.OAUTH);
    }

    @Test
    public void testRejectsMissingTenantDomain() {
        IdentityKeyStoreResolver identityResolver = mock(IdentityKeyStoreResolver.class);
        IdentityServerTenantSigningKeyResolver resolver =
                new IdentityServerTenantSigningKeyResolver(identityResolver);

        expectThrows(IllegalArgumentException.class, () -> resolver.resolve(" "));
    }
}
