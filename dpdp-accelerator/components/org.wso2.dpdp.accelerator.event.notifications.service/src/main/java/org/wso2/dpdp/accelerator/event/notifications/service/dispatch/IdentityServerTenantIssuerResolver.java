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

import org.wso2.carbon.identity.oauth2.util.OAuth2Util;

/** Delegates tenant issuer resolution to Identity Server. */
final class IdentityServerTenantIssuerResolver implements TenantIssuerResolver {

    private final TenantIssuerResolver issuerLocationResolver;

    IdentityServerTenantIssuerResolver() {
        this(OAuth2Util::getIssuerLocation);
    }

    IdentityServerTenantIssuerResolver(TenantIssuerResolver issuerLocationResolver) {
        this.issuerLocationResolver = issuerLocationResolver;
    }

    @Override
    public String resolve(String tenantDomain) throws Exception {
        if (tenantDomain == null || tenantDomain.trim().isEmpty()) {
            throw new IllegalArgumentException("Tenant domain is required for event payload issuer resolution.");
        }

        String normalizedTenantDomain = tenantDomain.trim();
        String issuer = issuerLocationResolver.resolve(normalizedTenantDomain);
        if (issuer == null || issuer.trim().isEmpty()) {
            throw new IllegalStateException("Identity Server tenant issuer is unavailable.");
        }
        return issuer;
    }
}
