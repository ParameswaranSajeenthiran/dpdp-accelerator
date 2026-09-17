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

import { test, expect, request as playwrightRequest } from '@playwright/test'
import { config, updateLocalConfig } from '../../utils/config'
import { isBaseUrl, superAdmin } from '../../utils/serverConfig'
import { superTenantScimSurface } from '../../utils/scimProvisioning'
import { bootstrapProvisioningToken, provisionPersonas, type PersonaCredential } from '../../utils/tenantProvisioning'

// Deliberately does not import utils/env.ts - see this plan's top-level note. This is the file
// that CREATES what env.ts's persona fields require, so it cannot depend on them already existing.

// Split out from 01.02-user-provisioning (which provisions the per-run tenant) so the
// "super-tenant" project's own setup dependency never has to depend on "tenant-setup" - the super
// tenant already exists, so nothing here needs a tenant created first. See playwright.config.ts.
test.describe('Test-account provisioning', () => {
  test("01.03.01 - Provisions the super tenant's four personas and assigns their roles", async () => {
    const surface = superTenantScimSurface()
    const consoleUrl = `${isBaseUrl}/console`
    const managementApiBase = `${isBaseUrl}/api/server/v1`
    const tokenUrl = `${isBaseUrl}/oauth2/token`

    const { token } = await bootstrapProvisioningToken(consoleUrl, managementApiBase, tokenUrl, superAdmin, surface)

    const usernames = {
      consentAdmin: config.personas.consentAdmin.username,
      user: config.personas.user.username,
      user2: config.personas.user2.username,
      dpo: config.personas.dpo.username,
    }
    const existing: Partial<Record<'consentAdmin' | 'user' | 'user2' | 'dpo', PersonaCredential>> = {}
    for (const key of ['consentAdmin', 'user', 'user2', 'dpo'] as const) {
      const configured = config.personas[key]
      if (configured.password) {
        existing[key] = { username: configured.username, password: configured.password }
      }
    }

    const apiContext = await playwrightRequest.newContext({ ignoreHTTPSErrors: true })
    try {
      const personas = await provisionPersonas(
        apiContext,
        surface,
        token,
        usernames,
        {
          consentAdmin: config.personaRoles.consentAdmin,
          user: config.personaRoles.user,
          dpo: config.personaRoles.dpo,
        },
        existing,
      )
      updateLocalConfig({
        personas: {
          consentAdmin: { password: personas.consentAdmin.password },
          user: { password: personas.user.password },
          user2: { password: personas.user2.password },
          dpo: { password: personas.dpo.password },
        },
      })

      expect(personas.consentAdmin.password).toBeTruthy()
      expect(personas.user.password).toBeTruthy()
      expect(personas.user2.password).toBeTruthy()
      expect(personas.dpo.password).toBeTruthy()
    } finally {
      await apiContext.dispose()
    }
  })
})
