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

import { request as playwrightRequest, test } from '@playwright/test'
import { env, scim2UsersUrl, type Persona } from './env'
import { provisioningHeaders } from './provisioningClient'
import { readRunState } from './runState'
import { mintScimToken, secondaryTenantScimSurface, superTenantScimSurface, type ScimSurface } from './scimProvisioning'
import { resolveTarget } from './targets'

/**
 * Creates and removes disposable user accounts through SCIM2, for the account-deletion test.
 *
 * That test destroys the account it signs in as, so it cannot use any of the shared personas in
 * fixtures/auth.fixtures.ts - those log in once and are reused by every later test in the run.
 * The account here exists for one test and is gone by the end of it, either because the test
 * deleted it through the portal (the point of the test) or because the cleanup below caught
 * what a failure left behind.
 *
 * Target-aware: which SCIM admin surface/token this creates the account through depends on
 * whether the running project is "multi-tenant" or "super-tenant" (see resolveScimAdminContext) -
 * a throwaway account created on the wrong surface would not exist in the store the tenant-
 * qualified portal actually authenticates against.
 */

const SCIM2_USER_SCHEMA = 'urn:ietf:params:scim:schemas:core:2.0:User'

/**
 * The admin surface + bearer token this file's SCIM2 admin calls (create/view/delete the
 * throwaway account, assign it a role) authenticate with. Resolved once per call from the
 * running project:
 *  - super-tenant: the existing, already-working provisioning client (config.provisioningClient,
 *    see utils/provisioningClient.ts) against the classic `/scim2/*` surface.
 *  - multi-tenant: the per-run tenant's own M2M provisioning client (minted and persisted to
 *    .e2e-run-state.json by tests/01-provisioning/01.02-user-provisioning.spec.ts) against the
 *    org-admin `/o/scim2/*` surface - the same surface that spec already proves works for
 *    managing this tenant's personas.
 */
interface ScimAdminContext {
  surface: ScimSurface
  headers: Record<string, string>
}

async function resolveScimAdminContext(): Promise<ScimAdminContext> {
  const target = resolveTarget(test.info().project.name)
  if (target.name === 'super-tenant') {
    return { surface: superTenantScimSurface(), headers: await provisioningHeaders() }
  }

  const { tenant } = readRunState()
  if (!tenant?.provisioningClient) {
    throw new Error(
      'No tenant provisioningClient in .e2e-run-state.json. The "user-setup" project ' +
        '(tests/01-provisioning/01.02-user-provisioning.spec.ts) must run before any test that ' +
        'needs it - see playwright.config.ts.',
    )
  }
  const surface = secondaryTenantScimSurface(tenant.domain)
  const requestContext = await playwrightRequest.newContext({ ignoreHTTPSErrors: env.ignoreHttpsErrors })
  try {
    const token = await mintScimToken(
      requestContext,
      `${env.identityServerBaseUrl}/t/${tenant.domain}/oauth2/token`,
      tenant.provisioningClient.clientId,
      tenant.provisioningClient.clientSecret,
      [...surface.userScopes, ...surface.roleScopes],
    )
    return { surface, headers: { Authorization: `Bearer ${token}` } }
  } finally {
    await requestContext.dispose()
  }
}

export interface ThrowawayUser extends Persona {
  /** SCIM2 resource id, for the cleanup path. */
  id: string
}

/**
 * Creates a user and assigns it the portal's regular-user role, so the session it signs in with
 * carries `account:self:delete` exactly the way a real portal user's does.
 */
export async function createThrowawayUser(
  roleName: string,
  usernamePrefix: string,
): Promise<ThrowawayUser> {
  const ctx = await resolveScimAdminContext()

  // Unique per run: a leftover account from an interrupted run must not collide with this one.
  // Email-shaped because the accelerator enforces it - SCIM2 rejects a bare name with 31301.
  const username = `${usernamePrefix}-${Date.now().toString(36)}@dpdp.test`
  const password = `Throwaway#${Math.random().toString(36).slice(2, 10)}A1`

  const response = await fetch(ctx.surface.usersUrl, {
    method: 'POST',
    headers: { ...ctx.headers, 'Content-Type': 'application/json' },
    body: JSON.stringify({
      schemas: [SCIM2_USER_SCHEMA],
      // Unqualified: SCIM2 puts the user in the primary user store. Prefixing a store
      // name that doesn't exist on the server fails with "Invalid user store name".
      userName: username,
      password,
      name: { givenName: 'Throwaway', familyName: 'Account' },
      // The username is already an address; appending a domain again is rejected.
      emails: [{ primary: true, value: username }],
    }),
    signal: AbortSignal.timeout(20_000),
  })

  if (response.status !== 201) {
    throw new Error(
      `Could not create the throwaway user "${username}" via SCIM2 (status ${String(response.status)}): ` +
        `${await response.text()}. The provisioning client needs the SCIM2 user-management scopes - ` +
        'run npm run bootstrap:provisioning-app (super tenant) or re-run "user-setup" (multi-tenant).',
    )
  }

  const created = (await response.json()) as { id?: string }
  if (!created.id) {
    throw new Error(`SCIM2 created "${username}" but returned no resource id.`)
  }

  await assignRole(ctx, created.id, roleName)
  return { id: created.id, username, password }
}

/**
 * Adds the user to an existing application role by patching the role's member list. The role is
 * provisioned per tenant by the accelerator, so this looks it up rather than creating it.
 */
async function assignRole(ctx: ScimAdminContext, userId: string, roleName: string): Promise<void> {
  const searchResponse = await fetch(
    `${ctx.surface.rolesUrl}?filter=${encodeURIComponent(`displayName eq ${roleName}`)}`,
    { headers: ctx.headers, signal: AbortSignal.timeout(20_000) },
  )
  if (!searchResponse.ok) {
    throw new Error(
      `Could not look up the "${roleName}" role (status ${String(searchResponse.status)}): ` +
        `${await searchResponse.text()}`,
    )
  }

  const found = (await searchResponse.json()) as { Resources?: { id?: string }[] }
  const roleId = found.Resources?.[0]?.id
  if (!roleId) {
    throw new Error(
      `The "${roleName}" role does not exist in this tenant. It is provisioned automatically - ` +
        `see docs/content/configuration-guide.md, "Recovering a broken tenant".`,
    )
  }

  const patchResponse = await fetch(`${ctx.surface.rolesUrl}/${roleId}`, {
    method: 'PATCH',
    headers: { ...ctx.headers, 'Content-Type': 'application/json' },
    body: JSON.stringify({
      schemas: ['urn:ietf:params:scim:api:messages:2.0:PatchOp'],
      Operations: [{ op: 'add', path: 'users', value: [{ value: userId }] }],
    }),
    signal: AbortSignal.timeout(20_000),
  })
  if (!patchResponse.ok) {
    throw new Error(
      `Could not add the throwaway user to "${roleName}" (status ${String(patchResponse.status)}): ` +
        `${await patchResponse.text()}`,
    )
  }
}

/**
 * Best-effort cleanup: the account is often gone already when the test passed.
 *
 * Where an approval workflow is associated with Delete User the delete only
 * records a request (202) and the account survives, so this approves the
 * resulting task too - otherwise every run would leave a throwaway account and
 * a pending approval behind on the server.
 */
export async function deleteThrowawayUser(userId: string, username?: string): Promise<void> {
  const ctx = await resolveScimAdminContext()
  const response = await fetch(`${ctx.surface.usersUrl}/${userId}`, {
    method: 'DELETE',
    headers: ctx.headers,
    signal: AbortSignal.timeout(20_000),
  }).catch(() => undefined)

  // 204 means it is already gone. Anything else under a workflow leaves the
  // account alive behind a pending task: 202 when this delete raised it, 400
  // when the test itself already did ("pending workflow already defined").
  if (response?.status === 204 || !username) {
    return
  }
  await approvePendingDeletion(username)
}

/**
 * The approval-task endpoints below are `/me/` resources - they act as the signed-in user, so a
 * client_credentials token (which carries no user) cannot address them. This stays on Basic auth
 * as an actual signed-in admin persona, and is best-effort: it only runs where a Delete User
 * approval workflow is configured, which is not the default, and it no-ops just as harmlessly on
 * a deployment where Basic auth itself is disabled for the tenant (IS 7.3 disables it for any
 * root organization created after the cutoff in compatibility-settings-metadata.json - true for
 * every per-run multi-tenant tenant this suite creates).
 */
function basicAdminHeaders(admin: Persona): Record<string, string> {
  const credentials = Buffer.from(`${admin.username}:${admin.password}`).toString('base64')
  return {
    Authorization: `Basic ${credentials}`,
    'Content-Type': 'application/json',
    Accept: 'application/json',
  }
}

/** Approves the pending Delete User task for one username, if the admin has it. */
async function approvePendingDeletion(username: string): Promise<void> {
  const target = resolveTarget(test.info().project.name)
  // Basic auth - the only credential these /me/ endpoints accept - is disabled for every root
  // organization created after the cutoff in IS 7.3's compatibility-settings-metadata.json, which
  // is every per-run tenant this suite creates. The machinery below stays for the day that
  // changes; today it can only ever 401 under multi-tenant.
  if (target.name !== 'super-tenant') {
    return
  }

  try {
    // The account this workflow's admin surface must sign in as differs per target: the super
    // tenant's own admin for the super tenant, the tenant owner (the only account guaranteed to
    // administer this specific per-run tenant) for multi-tenant.
    let admin: Persona = env.superAdmin
    if (target.name !== 'super-tenant') {
      const { tenant } = readRunState()
      if (!tenant) {
        throw new Error('No tenant in .e2e-run-state.json.')
      }
      admin = tenant.owner
    }
    const prefix = target.tenantDomain ? `/t/${target.tenantDomain}` : ''

    const listed = await fetch(`${env.identityServerBaseUrl}${prefix}/api/users/v2/me/approval-tasks`, {
      headers: basicAdminHeaders(admin),
      signal: AbortSignal.timeout(20_000),
    })
    if (!listed.ok) {
      return
    }
    const tasks = (await listed.json()) as { id: string; approvalStatus: string }[]
    for (const task of tasks.filter((t) => t.approvalStatus === 'READY')) {
      const detailResponse = await fetch(
        `${env.identityServerBaseUrl}${prefix}/api/users/v2/me/approval-tasks/${task.id}`,
        { headers: basicAdminHeaders(admin), signal: AbortSignal.timeout(20_000) },
      )
      if (!detailResponse.ok) {
        continue
      }
      const detail = (await detailResponse.json()) as { properties?: { key: string; value: string }[] }
      const taskUser = detail.properties?.find((p) => p.key === 'Username')?.value
      if (taskUser !== username) {
        continue
      }
      await fetch(
        `${env.identityServerBaseUrl}${prefix}/api/users/v2/me/approval-tasks/${task.id}/state`,
        {
          method: 'PUT',
          headers: basicAdminHeaders(admin),
          body: JSON.stringify({ action: 'APPROVE' }),
          signal: AbortSignal.timeout(20_000),
        },
      )
      return
    }
  } catch {
    // Cleanup is best effort - a leftover account must not fail the test that
    // already made its assertions.
  }
}

/**
 * Whether the account still exists. Used to prove the deletion actually reached the user store,
 * rather than trusting the portal's own redirect.
 */
export async function userExists(userId: string): Promise<boolean> {
  const ctx = await resolveScimAdminContext()
  const response = await fetch(`${ctx.surface.usersUrl}/${userId}`, {
    headers: ctx.headers,
    signal: AbortSignal.timeout(20_000),
  })
  return response.status === 200
}

/**
 * Attempts to delete some *other* user with a portal session's own access token, to prove
 * `account:self:delete` does not authorize it. This deliberately targets the CLASSIC (non-org)
 * SCIM2 Users surface, tenant-qualified where applicable - the same surface the portal's own
 * self-delete uses for `/scim2/Me` (see fixtures/auth.fixtures.ts / the account-deletion test),
 * not utils/scimProvisioning.ts's org-admin surface this file's other admin operations use.
 * Returns the status so the caller can assert on it.
 */
export async function attemptDeleteAsUser(bearerToken: string, userId: string): Promise<number> {
  const target = resolveTarget(test.info().project.name)
  const response = await fetch(scim2UsersUrl(`/${userId}`, target.tenantDomain), {
    method: 'DELETE',
    headers: { Authorization: `Bearer ${bearerToken}`, Accept: 'application/json' },
    signal: AbortSignal.timeout(20_000),
  })
  return response.status
}
