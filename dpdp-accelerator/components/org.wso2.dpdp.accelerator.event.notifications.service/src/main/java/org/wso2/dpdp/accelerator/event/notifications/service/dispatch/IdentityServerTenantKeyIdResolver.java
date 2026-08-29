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

import com.nimbusds.jose.JWSAlgorithm;
import org.wso2.carbon.identity.oauth2.util.OAuth2Util;

import java.security.cert.Certificate;

/** Delegates key-ID calculation to Identity Server's configured OAuth provider. */
final class IdentityServerTenantKeyIdResolver implements TenantKeyIdResolver {

    @Override
    public String resolve(Certificate certificate, String tenantDomain) throws Exception {
        return OAuth2Util.getKID(certificate, JWSAlgorithm.RS256, tenantDomain);
    }
}
