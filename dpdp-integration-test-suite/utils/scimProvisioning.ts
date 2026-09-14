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
import { isBaseUrl } from './serverConfig'

/**
 * A tenant's SCIM2 surface differs by shape, not just by base URL - confirmed live: a secondary
 * tenant's own users/roles live at "/o/scim2/..." under "internal_org_*" scopes, not the
 * TENANT-typed "/scim2/..." + "internal_*" surface the super tenant exposes. Using the wrong pair
 * fails closed - the wrong path 403s, and the wrong scopes mint a token with none granted at all.
 */
export interface ScimSurface {
  usersUrl: string
  rolesUrl: string
  userResourceIdentifier: string
  roleResourceIdentifier: string
  userScopes: string[]
  roleScopes: string[]
}

export function superTenantScimSurface(): ScimSurface {
  return {
    usersUrl: `${isBaseUrl}/scim2/Users`,
    rolesUrl: `${isBaseUrl}/scim2/v2/Roles`,
    userResourceIdentifier: '/scim2/Users',
    roleResourceIdentifier: '/scim2/Roles',
    userScopes: ['internal_user_mgt_list', 'internal_user_mgt_create'],
    roleScopes: ['internal_role_mgt_view', 'internal_role_mgt_users_update'],
  }
}

export function secondaryTenantScimSurface(domain: string): ScimSurface {
  return {
    usersUrl: `${isBaseUrl}/t/${domain}/o/scim2/Users`,
    rolesUrl: `${isBaseUrl}/t/${domain}/o/scim2/v2/Roles`,
    userResourceIdentifier: '/o/scim2/Users',
    roleResourceIdentifier: '/o/scim2/Roles',
    userScopes: ['internal_org_user_mgt_list', 'internal_org_user_mgt_create'],
    roleScopes: ['internal_org_role_mgt_view', 'internal_org_role_mgt_users_update'],
  }
}

export async function mintScimToken(
  request: APIRequestContext,
  tokenUrl: string,
  clientId: string,
  clientSecret: string,
  scopes: string[],
): Promise<string> {
  const basic = Buffer.from(`${clientId}:${clientSecret}`).toString('base64')
  const response = await request.post(tokenUrl, {
    headers: { Authorization: `Basic ${basic}`, 'Content-Type': 'application/x-www-form-urlencoded' },
    form: { grant_type: 'client_credentials', scope: scopes.join(' ') },
    timeout: 20_000,
  })
  const body = JSON.parse(await response.text()) as { access_token?: string; scope?: string; error?: string }
  if (!body.access_token) {
    throw new Error(
      `Could not mint a SCIM provisioning token from ${tokenUrl} (status ${String(response.status())}): ` +
        (body.error ?? 'no access_token in the response'),
    )
  }
  const granted = new Set((body.scope ?? '').split(' '))
  const missing = scopes.filter((scope) => !granted.has(scope))
  if (missing.length > 0) {
    throw new Error(
      `The client at ${tokenUrl} minted a token missing scope(s): ${missing.join(', ')}. ` +
        'Its authorized-API scopes do not match what this provisioning step requested.',
    )
  }
  return body.access_token
}

async function findScimUserId(
  request: APIRequestContext,
  surface: ScimSurface,
  token: string,
  username: string,
): Promise<string | undefined> {
  const response = await request.get(`${surface.usersUrl}?filter=${encodeURIComponent(`userName eq ${username}`)}`, {
    headers: { Authorization: `Bearer ${token}` },
    timeout: 20_000,
  })
  const body = JSON.parse(await response.text()) as { Resources?: { id: string }[] }
  return body.Resources?.[0]?.id
}

/** Idempotent - returns the existing user's id if `username` already exists on `surface`. */
export async function ensureScimUser(
  request: APIRequestContext,
  surface: ScimSurface,
  token: string,
  username: string,
  password: string,
): Promise<string> {
  const existing = await findScimUserId(request, surface, token, username)
  if (existing) {
    return existing
  }
  const response = await request.post(surface.usersUrl, {
    headers: { Authorization: `Bearer ${token}`, 'Content-Type': 'application/scim+json' },
    data: {
      schemas: ['urn:ietf:params:scim:schemas:core:2.0:User'],
      userName: username,
      password,
      name: { givenName: 'DPDP', familyName: 'E2E' },
      emails: [{ primary: true, value: username }],
    },
    timeout: 20_000,
  })
  const text = await response.text()
  if (!response.ok()) {
    throw new Error(`Creating "${username}" at ${surface.usersUrl} returned HTTP ${String(response.status())}: ${text}`)
  }
  const body = JSON.parse(text) as { id?: string }
  if (!body.id) {
    throw new Error(`Created "${username}" but the response carried no id.`)
  }
  return body.id
}

async function findScimRoleId(
  request: APIRequestContext,
  surface: ScimSurface,
  token: string,
  roleName: string,
): Promise<string | undefined> {
  const response = await request.get(`${surface.rolesUrl}?filter=${encodeURIComponent(`displayName eq ${roleName}`)}`, {
    headers: { Authorization: `Bearer ${token}` },
    timeout: 20_000,
  })
  const body = JSON.parse(await response.text()) as { Resources?: { id: string }[] }
  return body.Resources?.[0]?.id
}

/** Idempotent - SCIM2 `op: add` on `path: users` appends, so re-adding an existing member is a
 * no-op (verified live: other members of the role are never evicted). */
export async function ensureRoleMembership(
  request: APIRequestContext,
  surface: ScimSurface,
  token: string,
  roleName: string,
  userId: string,
): Promise<void> {
  const roleId = await findScimRoleId(request, surface, token, roleName)
  if (!roleId) {
    throw new Error(
      `Role "${roleName}" does not exist at ${surface.rolesUrl}. The accelerator creates every ` +
        'dpdp-consent-* role at tenant-provisioning time - this usually means provisioning failed.',
    )
  }
  const response = await request.patch(`${surface.rolesUrl}/${roleId}`, {
    headers: { Authorization: `Bearer ${token}`, 'Content-Type': 'application/json' },
    data: { Operations: [{ op: 'add', path: 'users', value: [{ value: userId }] }] },
    timeout: 20_000,
  })
  if (!response.ok()) {
    throw new Error(`Assigning role "${roleName}" returned HTTP ${String(response.status())}: ${await response.text()}`)
  }
}
