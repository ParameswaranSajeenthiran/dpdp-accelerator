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

import type { APIRequestContext } from '@playwright/test'
import type { Persona } from './env'
import {
  authorizeApiResource,
  createM2mApplication,
  findApiResourceId,
  findApplicationId,
  openManagementSession,
  readOidcCredentials,
} from './managementApi'
import { ensureRoleMembership, ensureScimUser, mintScimToken, type ScimSurface } from './scimProvisioning'
import { generatePassword } from './testData'

export interface PersonaCredential {
  username: string
  password: string
}

export interface ProvisionedPersonas {
  consentAdmin: PersonaCredential
  user: PersonaCredential
  user2: PersonaCredential
  dpo: PersonaCredential
}

export interface ProvisioningToken {
  token: string
  /** Persisted by the tenant path (see .e2e-run-state.json's TenantRunState.provisioningClient) so
   * utils/throwawayUser.ts can mint further on-demand SCIM tokens for the same tenant later in the
   * run, without repeating this whole browser-driven bootstrap. */
  clientId: string
  clientSecret: string
}

const APP_NAME = 'DPDP E2E Provisioning'
const APP_DESCRIPTION =
  'Machine-to-machine client the DPDP integration test suite provisions its test accounts with. ' +
  'Created by tests/01-provisioning/01.02-user-provisioning.spec.ts - not part of the accelerator.'

/**
 * Finds-or-creates the M2M client this suite provisions personas with on whichever target
 * `surface` describes, authorizes it, and mints a token - all idempotent, safe to call every
 * run. `consoleUrl`/`managementApiBase`/`tokenUrl` are the three URLs for that target (super
 * tenant, or a specific tenant); `signInAs` is whoever can administer that Console.
 */
export async function bootstrapProvisioningToken(
  consoleUrl: string,
  managementApiBase: string,
  tokenUrl: string,
  signInAs: Persona,
  surface: ScimSurface,
): Promise<ProvisioningToken> {
  const session = await openManagementSession(consoleUrl, signInAs)
  try {
    let applicationId = await findApplicationId(session, managementApiBase, APP_NAME)
    if (!applicationId) {
      applicationId = await createM2mApplication(session, managementApiBase, APP_NAME, APP_DESCRIPTION)
    }

    const userApiId = await findApiResourceId(session, managementApiBase, surface.userResourceIdentifier)
    if (!userApiId) {
      throw new Error(`No API resource "${surface.userResourceIdentifier}" at ${managementApiBase}.`)
    }
    await authorizeApiResource(session, managementApiBase, applicationId, userApiId, surface.userScopes)

    const roleApiId = await findApiResourceId(session, managementApiBase, surface.roleResourceIdentifier)
    if (!roleApiId) {
      throw new Error(`No API resource "${surface.roleResourceIdentifier}" at ${managementApiBase}.`)
    }
    await authorizeApiResource(session, managementApiBase, applicationId, roleApiId, surface.roleScopes)

    const { clientId, clientSecret } = await readOidcCredentials(session, managementApiBase, applicationId)
    const token = await mintScimToken(session.request, tokenUrl, clientId, clientSecret, [
      ...surface.userScopes,
      ...surface.roleScopes,
    ])
    return { token, clientId, clientSecret }
  } finally {
    await session.browser.close()
  }
}

/**
 * Ensures all four personas exist on `surface` with their roles. Reuses a persona's credential
 * from `existing` (a resumed run, or an already-provisioned long-lived target) rather than
 * generating a new password for it - regenerating on every call would break both resumability and
 * ensureScimUser's own idempotency, since it identifies an existing account by username only.
 */
export async function provisionPersonas(
  request: APIRequestContext,
  surface: ScimSurface,
  token: string,
  usernames: { consentAdmin: string; user: string; user2: string; dpo: string },
  roles: { consentAdmin: string; user: string; dpo: string },
  existing: Partial<ProvisionedPersonas>,
): Promise<ProvisionedPersonas> {
  const personas: ProvisionedPersonas = {
    consentAdmin: existing.consentAdmin ?? { username: usernames.consentAdmin, password: generatePassword() },
    user: existing.user ?? { username: usernames.user, password: generatePassword() },
    user2: existing.user2 ?? { username: usernames.user2, password: generatePassword() },
    dpo: existing.dpo ?? { username: usernames.dpo, password: generatePassword() },
  }
  const roleFor: Record<keyof ProvisionedPersonas, string> = {
    consentAdmin: roles.consentAdmin,
    user: roles.user,
    user2: roles.user,
    dpo: roles.dpo,
  }

  for (const key of Object.keys(personas) as (keyof ProvisionedPersonas)[]) {
    const { username, password } = personas[key]
    const userId = await ensureScimUser(request, surface, token, username, password)
    await ensureRoleMembership(request, surface, token, roleFor[key], userId)
  }

  return personas
}
