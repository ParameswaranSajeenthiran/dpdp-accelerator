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

import java.util.concurrent.atomic.AtomicReference;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.expectThrows;

/** Tests tenant normalization and validation around Identity Server issuer resolution. */
public class IdentityServerTenantIssuerResolverTest {

    @Test
    public void testDelegatesNormalizedTenantDomain() throws Exception {
        AtomicReference<String> resolvedTenant = new AtomicReference<>();
        IdentityServerTenantIssuerResolver resolver = new IdentityServerTenantIssuerResolver(tenantDomain -> {
            resolvedTenant.set(tenantDomain);
            return "https://is.example/t/tenant.example/oauth2/token";
        });

        String issuer = resolver.resolve(" tenant.example ");

        assertEquals(resolvedTenant.get(), "tenant.example");
        assertEquals(issuer, "https://is.example/t/tenant.example/oauth2/token");
    }

    @Test
    public void testRejectsMissingTenantDomain() {
        IdentityServerTenantIssuerResolver resolver = new IdentityServerTenantIssuerResolver(tenantDomain -> "unused");

        expectThrows(IllegalArgumentException.class, () -> resolver.resolve(" "));
    }

    @Test
    public void testRejectsBlankOrNullIssuer() {
        IdentityServerTenantIssuerResolver blankResolver =
                new IdentityServerTenantIssuerResolver(tenantDomain -> " ");
        IdentityServerTenantIssuerResolver nullResolver =
                new IdentityServerTenantIssuerResolver(tenantDomain -> null);

        expectThrows(IllegalStateException.class, () -> blankResolver.resolve("tenant.example"));
        expectThrows(IllegalStateException.class, () -> nullResolver.resolve("tenant.example"));
    }
}
