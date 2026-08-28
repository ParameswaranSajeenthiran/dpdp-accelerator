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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Signature;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

/** Tests the tenant boundary and compact RS256 contract of the event payload signer. */
public class IdentityServerPayloadSignerTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String TENANT_A = "tenant-a.example";
    private static final String TENANT_B = "tenant-b.example";

    private KeyPair tenantAKeyPair;
    private KeyPair tenantBKeyPair;
    private IdentityServerPayloadSigner signer;

    @BeforeClass
    public void setUp() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        tenantAKeyPair = generator.generateKeyPair();
        tenantBKeyPair = generator.generateKeyPair();

        Map<String, TenantSigningKey> keys = new HashMap<>();
        keys.put(TENANT_A, new TenantSigningKey(
                tenantAKeyPair.getPrivate(), "tenant-a-kid", "tenant-a-thumbprint"));
        keys.put(TENANT_B, new TenantSigningKey(
                tenantBKeyPair.getPrivate(), "tenant-b-kid", "tenant-b-thumbprint"));
        signer = new IdentityServerPayloadSigner(keys::get);
    }

    @Test
    public void testSignsWithTenantSpecificKeyAndMetadata() throws Exception {
        JsonNode eventPayload = MAPPER.readTree("{\"deliveryId\":\"delivery-1\",\"topic\":\"consent.revoke\","
                + "\"payload\":{\"status\":\"REVOKED\"}}");
        String jws = signer.sign(context(TENANT_A, "group-1", "delivery-1", "event-1",
                "payload-hash", eventPayload));

        String[] parts = jws.split("\\.");
        assertEquals(parts.length, 3);

        JsonNode header = decodeJson(parts[0]);
        assertEquals(header.get("alg").asText(), "RS256");
        assertEquals(header.get("typ").asText(), "JWT");
        assertEquals(header.get("kid").asText(), "tenant-a-kid");
        assertEquals(header.get("x5t#S256").asText(), "tenant-a-thumbprint");

        JsonNode claims = decodeJson(parts[1]);
        assertEquals(claims.get("iss").asText(), TENANT_A);
        assertEquals(claims.get("sub").asText(), "group-1");
        assertEquals(claims.get("aud").asText(), "dpdp-event-notifications");
        assertEquals(claims.get("jti").asText(), "delivery-1");
        assertEquals(claims.get("txn").asText(), "event-1");
        assertEquals(claims.get("payloadHash").asText(), "payload-hash");
        assertEquals(claims.get("payload"), eventPayload);

        assertTrue(verify(jws, tenantAKeyPair));
        assertFalse(verify(jws, tenantBKeyPair),
                "A tenant signature must not verify with another tenant's public key.");
    }

    @Test
    public void testDifferentTenantsProduceDifferentKeyIdentifiers() throws Exception {
        JsonNode payload = MAPPER.readTree("{\"payload\":{\"key\":\"value\"}}");
        String tenantAJws = signer.sign(context(TENANT_A, "group-a", "delivery-a", "event-a",
                "hash-a", payload));
        String tenantBJws = signer.sign(context(TENANT_B, "group-b", "delivery-b", "event-b",
                "hash-b", payload));

        assertEquals(decodeJson(tenantAJws.split("\\.")[0]).get("kid").asText(), "tenant-a-kid");
        assertEquals(decodeJson(tenantBJws.split("\\.")[0]).get("kid").asText(), "tenant-b-kid");
        assertTrue(verify(tenantBJws, tenantBKeyPair));
        assertFalse(verify(tenantBJws, tenantAKeyPair));
    }

    private static EventPayloadSigningContext context(String tenant, String subject, String deliveryId,
            String eventId, String payloadHash, JsonNode payload) {

        return new EventPayloadSigningContext(tenant, tenant, subject, "dpdp-event-notifications",
                deliveryId, eventId, 1_787_651_970L, payloadHash, payload);
    }

    private static JsonNode decodeJson(String encoded) throws Exception {
        return MAPPER.readTree(Base64.getUrlDecoder().decode(encoded));
    }

    private static boolean verify(String jws, KeyPair keyPair) throws Exception {
        String[] parts = jws.split("\\.");
        Signature verifier = Signature.getInstance("SHA256withRSA");
        verifier.initVerify(keyPair.getPublic());
        verifier.update((parts[0] + "." + parts[1]).getBytes(StandardCharsets.US_ASCII));
        return verifier.verify(Base64.getUrlDecoder().decode(parts[2]));
    }
}
