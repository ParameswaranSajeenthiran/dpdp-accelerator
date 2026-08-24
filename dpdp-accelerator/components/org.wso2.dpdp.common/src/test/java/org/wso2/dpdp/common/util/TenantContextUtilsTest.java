/*
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

package org.wso2.dpdp.common.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class TenantContextUtilsTest {

    @Test
    void extractsTheTenantDomainFromATenantQualifiedPath() {
        assertEquals("example.com",
                TenantContextUtils.extractOrgId("/t/example.com/api/dpdp/complaints/v1/complaints", "carbon.super"));
    }

    @Test
    void extractsTheTenantDomainWhenTheTenantSegmentIsNotAtTheStartOfThePath() {
        assertEquals("example.com",
                TenantContextUtils.extractOrgId("/api/dpdp/complaints/t/example.com/complaints", "carbon.super"));
    }

    @Test
    void fallsBackToTheDefaultWhenThePathHasNoTenantSegment() {
        assertEquals("carbon.super",
                TenantContextUtils.extractOrgId("/api/dpdp/complaints/v1/complaints", "carbon.super"));
    }

    @Test
    void fallsBackToTheDefaultWhenThePathIsNull() {
        assertEquals("carbon.super", TenantContextUtils.extractOrgId(null, "carbon.super"));
    }

    @Test
    void decodesAPercentEncodedTenantDomain() {
        assertEquals("acme corp",
                TenantContextUtils.extractOrgId("/t/acme%20corp/api/dpdp/complaints", "carbon.super"));
    }

    @Test
    void doesNotMatchAtSegmentWhoseNameMerelyStartsWithT() {
        assertEquals("carbon.super",
                TenantContextUtils.extractOrgId("/tenants/example.com/complaints", "carbon.super"));
    }

    // ---- extractOrgIdFromUsername ----

    @Test
    void extractsTheTenantDomainFromATenantQualifiedUsername() {
        assertEquals("example.com",
                TenantContextUtils.extractOrgIdFromUsername("alice@example.com", "carbon.super"));
    }

    @Test
    void usesTheLastAtSignWhenTheLocalPartIsItselfAnEmailAddress() {
        assertEquals("example.com",
                TenantContextUtils.extractOrgIdFromUsername("alice@corp.io@example.com", "carbon.super"));
    }

    @Test
    void fallsBackToTheDefaultForAnUnqualifiedSuperTenantUsername() {
        assertEquals("carbon.super", TenantContextUtils.extractOrgIdFromUsername("admin", "carbon.super"));
    }

    @Test
    void fallsBackToTheDefaultWhenTheUsernameEndsWithAtSign() {
        assertEquals("carbon.super", TenantContextUtils.extractOrgIdFromUsername("admin@", "carbon.super"));
    }

    @Test
    void returnsNullWhenTheUsernameIsNullSinceTheTenantCannotBeDetermined() {
        assertNull(TenantContextUtils.extractOrgIdFromUsername(null, "carbon.super"));
    }
}
