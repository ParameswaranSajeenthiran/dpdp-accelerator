/**
 * Copyright (c) 2026, WSO2 LLC. (https://www.wso2.com).
 * <p>
 * WSO2 LLC. licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 *     http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.wso2.dpdp.accelerator.event.notifications.common.util;

import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * Computes HMAC-SHA256 signatures used to authenticate outbound webhook payloads.
 *
 * <p>Subscribers verify the {@code Event-Signature} header on incoming POSTs by recomputing
 * {@code HMAC-SHA256(sharedSecret, payload)} and comparing it to the header value. Returning a
 * stable lowercase hex string keeps the comparison cheap on the receiver side.</p>
 */
public final class HmacSigner {

    private static final String HMAC_ALGORITHM = "HmacSHA256";
    private static final char[] HEX = "0123456789abcdef".toCharArray();

    private HmacSigner() {
    }

    /**
     * Returns the lowercase hex-encoded HMAC-SHA256 of {@code payload} keyed by {@code secret}.
     *
     * <p>Returns {@code null} if either argument is {@code null} or blank; callers should treat
     * that as "do not sign" rather than as a signing error.</p>
     *
     * @param secret  shared secret stored on the subscription
     * @param payload the bytes that will be sent in the POST body
     * @return hex digest, or {@code null} when there is nothing to sign
     */
    public static String sign(String secret, String payload) {
        if (secret == null || secret.isEmpty() || payload == null) {
            return null;
        }
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), HMAC_ALGORITHM));
            byte[] digest = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
            return toHex(digest);
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            // HmacSHA256 is mandated by the JCA spec and a UTF-8 byte array is always a valid
            // key material; neither branch is reachable in practice on a JDK that supports
            // outbound HTTPS. Surface as a runtime exception rather than swallowing.
            throw new IllegalStateException(
                    "Unable to compute HMAC-SHA256 signature for webhook payload.", e);
        }
    }

    private static String toHex(byte[] bytes) {
        char[] out = new char[bytes.length * 2];
        for (int i = 0, j = 0; i < bytes.length; i++) {
            int v = bytes[i] & 0xFF;
            out[j++] = HEX[v >>> 4];
            out[j++] = HEX[v & 0x0F];
        }
        return new String(out);
    }
}
