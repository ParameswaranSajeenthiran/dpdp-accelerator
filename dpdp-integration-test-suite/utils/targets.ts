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

import { env, tenantPortalUrl, type Persona } from './env'
import { readRunState, type PersonaCredential } from './runState'

export type TargetName = 'multi-tenant' | 'super-tenant'

/**
 * Resolved from the running Playwright project's name (see playwright.config.ts) - never from a
 * parameter a spec file passes, so no test needs to know which of the two it is running under.
 * Only ever called from fixtures/auth.fixtures.ts, which only ever runs inside the
 * "multi-tenant"/"super-tenant" projects, both of which depend on "user-setup" - by the time this
 * resolves, every persona below is guaranteed to already exist.
 */
export interface Target {
  name: TargetName
  /** undefined for the super tenant - every URL helper in utils/env.ts already treats an
   * undefined tenantDomain as "no /t/<tenant> segment". */
  tenantDomain?: string
  /** Trailing-slash form, passed straight to browser.newContext({ baseURL }) - see the long
   * comment on env.ts's portalNavigationBaseUrl for why the trailing slash matters. */
  portalBaseUrl: string
  personas: {
    consentAdmin: Persona
    user: Persona
    user2: Persona
    dpo: Persona
  }
}

function requirePersona(credential: PersonaCredential | undefined, where: string): Persona {
  if (!credential) {
    throw new Error(
      `No ${where} persona in .e2e-run-state.json. The "user-setup" project must run before any ` +
        'test that needs it - see playwright.config.ts.',
    )
  }
  return credential
}

export function resolveTarget(projectName: string): Target {
  if (projectName === 'super-tenant') {
    return {
      name: 'super-tenant',
      tenantDomain: undefined,
      portalBaseUrl: env.portalNavigationBaseUrl,
      personas: {
        consentAdmin: env.consentAdmin,
        user: env.user,
        user2: requirePersona(env.secondUser(), 'super-tenant user2'),
        dpo: env.dpo,
      },
    }
  }

  if (projectName === 'multi-tenant') {
    const { tenant } = readRunState()
    if (!tenant) {
      throw new Error(
        'No tenant in .e2e-run-state.json. The "tenant-setup" project must run before any test ' +
          'that needs it - see playwright.config.ts.',
      )
    }
    return {
      name: 'multi-tenant',
      tenantDomain: tenant.domain,
      portalBaseUrl: `${tenantPortalUrl(tenant.domain)}/`,
      personas: {
        consentAdmin: requirePersona(tenant.personas?.consentAdmin, 'tenant consentAdmin'),
        user: requirePersona(tenant.personas?.user, 'tenant user'),
        user2: requirePersona(tenant.personas?.user2, 'tenant user2'),
        dpo: requirePersona(tenant.personas?.dpo, 'tenant dpo'),
      },
    }
  }

  throw new Error(`Unknown project "${projectName}" - expected "multi-tenant" or "super-tenant".`)
}
