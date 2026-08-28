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

import java.security.PrivateKey;

/** Tenant-specific private key and the identifier of its matching certificate. */
final class TenantSigningKey {

    private final PrivateKey privateKey;
    private final String keyId;
    private final String certificateThumbprint;

    TenantSigningKey(PrivateKey privateKey, String keyId, String certificateThumbprint) {
        this.privateKey = privateKey;
        this.keyId = keyId;
        this.certificateThumbprint = certificateThumbprint;
    }

    PrivateKey getPrivateKey() {
        return privateKey;
    }

    String getKeyId() {
        return keyId;
    }

    String getCertificateThumbprint() {
        return certificateThumbprint;
    }
}
