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

import java.net.URI;
import java.nio.charset.StandardCharsets;

/**
 * Builds webhook verification URIs while preserving an existing callback query.
 */
public final class WebhookVerificationUriBuilder {

    private static final char[] HEX = "0123456789ABCDEF".toCharArray();

    private WebhookVerificationUriBuilder() {
    }

    public static URI build(URI callbackUri, String topic, String challenge) {

        if (callbackUri == null) {
            throw new IllegalArgumentException("Callback URI cannot be null.");
        }
        if (callbackUri.getRawFragment() != null) {
            throw new IllegalArgumentException("Callback URL fragments are not permitted.");
        }

        StringBuilder uri = new StringBuilder(callbackUri.toASCIIString());
        String rawQuery = callbackUri.getRawQuery();
        if (rawQuery == null) {
            uri.append('?');
        } else if (!rawQuery.isEmpty()) {
            uri.append('&');
        }
        uri.append("hub.mode=subscribe")
                .append("&hub.topic=").append(encodeQueryParameter(topic))
                .append("&hub.challenge=").append(encodeQueryParameter(challenge));
        return URI.create(uri.toString());
    }

    private static String encodeQueryParameter(String value) {

        if (value == null) {
            throw new IllegalArgumentException("Verification query parameter cannot be null.");
        }
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        StringBuilder encoded = new StringBuilder(bytes.length);
        for (byte current : bytes) {
            int character = current & 0xFF;
            if (isUnreserved(character)) {
                encoded.append((char) character);
            } else {
                encoded.append('%')
                        .append(HEX[character >>> 4])
                        .append(HEX[character & 0x0F]);
            }
        }
        return encoded.toString();
    }

    private static boolean isUnreserved(int character) {

        return character >= 'a' && character <= 'z'
                || character >= 'A' && character <= 'Z'
                || character >= '0' && character <= '9'
                || character == '-' || character == '.' || character == '_' || character == '~';
    }
}
