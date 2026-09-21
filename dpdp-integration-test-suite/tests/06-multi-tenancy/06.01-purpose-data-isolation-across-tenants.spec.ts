/*
 * Copyright (c) 2026, WSO2 LLC. (https://www.wso2.com).
 *
 * WSO2 LLC. licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

import { test, expect, getPersonaState } from '../../fixtures/auth.fixtures'
import { ConsentApiClient } from '../../clients/ConsentApiClient'
import { authHeadersFromPersonaState } from '../../utils/authStorage'
import { resolveTarget } from '../../utils/targets'
import { uniquePurposeName } from '../../utils/testData'

/**
 * docs/content/configuration-guide.md's claim that "consents, catalog data, roles and sessions are all
 * partitioned per tenant by the server" has no automated coverage before this. Only meaningful
 * under the "multi-tenant" project: that is the only run with two tenants already provisioned to
 * compare (this run's own per-run tenant, and the super tenant) - see playwright.config.ts's
 * testIgnore on the "super-tenant" project, which has no second tenant to test isolation against
 * and skips this file entirely.
 *
 * `consentAdminConsentApi` is already tenant-qualified to this run's own per-run tenant (see
 * fixtures/auth.fixtures.ts). The super tenant's own consent-admin is a distinct account (never
 * assume the same username string means the same principal across tenants), so it needs its own
 * login here, via `getPersonaState`'s explicit target override rather than a second throwaway
 * tenant - no new tenant creation needed for this.
 */
test.describe('Purpose data isolation across tenants (API)', () => {
  test('06.01.01 - A Purpose created in one tenant is invisible from the other, and vice versa', async ({
    browser,
    request,
    consentAdminConsentApi,
  }) => {
    const superTenantTarget = resolveTarget('super-tenant')
    const superTenantAdminState = await getPersonaState(
      browser,
      'consent-admin',
      superTenantTarget.personas.consentAdmin,
      superTenantTarget,
    )
    const superTenantConsentApi = new ConsentApiClient(
      request,
      authHeadersFromPersonaState(superTenantAdminState),
      superTenantTarget.tenantDomain,
    )

    const tenantPurposeName = uniquePurposeName()
    const superTenantPurposeName = uniquePurposeName()

    const tenantCreate = await consentAdminConsentApi.createPurpose({
      name: tenantPurposeName,
      type: 'Policy',
      version: 'v1',
    })
    expect(tenantCreate.ok()).toBeTruthy()

    const superTenantCreate = await superTenantConsentApi.createPurpose({
      name: superTenantPurposeName,
      type: 'Policy',
      version: 'v1',
    })
    expect(superTenantCreate.ok()).toBeTruthy()

    // Not visible from carbon.super. Field is capitalized ("Purposes") in the consent-mgt v2
    // API's own list response.
    const foundInSuperTenant = await superTenantConsentApi.findPurposeByName(tenantPurposeName)
    expect(foundInSuperTenant.ok()).toBeTruthy()
    expect((await foundInSuperTenant.json()).totalResults).toBe(0)

    // ...and vice versa: the super tenant's Purpose is not visible from inside the tenant.
    const foundInTenant = await consentAdminConsentApi.findPurposeByName(superTenantPurposeName)
    expect(foundInTenant.ok()).toBeTruthy()
    expect((await foundInTenant.json()).totalResults).toBe(0)
  })
})
