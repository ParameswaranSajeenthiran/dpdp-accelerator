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

import { chromium, type APIRequestContext, type Browser } from '@playwright/test'
import { LoginPage } from '../pages/LoginPage'
import type { Persona } from './env'
import { ignoreHttpsErrors } from './serverConfig'

export interface ManagementSession {
  browser: Browser
  request: APIRequestContext
  token: string
}

/**
 * Signs into a Console (super tenant's or a specific tenant's own) and returns a request context
 * authenticated as that session - generalized from scripts/bootstrap-provisioning-app.ts's
 * private openConsoleSession. The Console keeps its token in a web worker (never readable from a
 * cookie or page script), so the only way to get it is to intercept an outgoing management-API
 * request; the token is also bound to the session's own cookie, so every call below has to go
 * through this same context's request object, not a bare fetch().
 */
export async function openManagementSession(consoleUrl: string, persona: Persona): Promise<ManagementSession> {
  const browser = await chromium.launch()
  try {
    const context = await browser.newContext({ ignoreHTTPSErrors: ignoreHttpsErrors })
    const page = await context.newPage()

    let resolveToken: (token: string) => void
    const token = new Promise<string>((resolve) => {
      resolveToken = resolve
    })
    page.on('request', (request) => {
      if (!request.url().includes('/api/server/v1/')) {
        return
      }
      const authorization = request.headers()['authorization']
      if (authorization?.toLowerCase().startsWith('bearer ')) {
        resolveToken(authorization.slice('bearer '.length))
      }
    })

    await page.goto(consoleUrl, { waitUntil: 'domcontentloaded' })
    const loginPage = new LoginPage(page)
    await loginPage.signIn(persona)

    const failed = loginPage.errorMessage
      .waitFor({ state: 'visible', timeout: 60_000 })
      .then(async () => {
        const message = (await loginPage.errorMessage.textContent())?.trim()
        throw new Error(`Console sign-in failed for ${persona.username}: ${message ?? 'the login form reported an error'}.`)
      })

    let timer: ReturnType<typeof setTimeout> | undefined
    const timeout = new Promise<never>((_, reject) => {
      timer = setTimeout(
        () =>
          reject(
            new Error(`Signed in to ${consoleUrl}, but no management-API call carried a bearer token within 60s.`),
          ),
        60_000,
      )
    })

    try {
      return { browser, request: context.request, token: await Promise.race([token, failed, timeout]) }
    } finally {
      clearTimeout(timer)
    }
  } catch (error) {
    await browser.close()
    throw error
  }
}

async function managementApiCall(
  session: ManagementSession,
  baseApiUrl: string,
  method: 'GET' | 'POST',
  path: string,
  body?: unknown,
): Promise<{ status: number; location: string | null; json: unknown }> {
  const url = `${baseApiUrl}${path}`
  const options = {
    headers: {
      Authorization: `Bearer ${session.token}`,
      ...(body === undefined ? {} : { 'Content-Type': 'application/json' }),
    },
    ...(body === undefined ? {} : { data: body }),
    timeout: 30_000,
  }
  const response = method === 'GET' ? await session.request.get(url, options) : await session.request.post(url, options)

  const text = await response.text()
  let json: unknown
  if (text.length > 0) {
    try {
      json = JSON.parse(text)
    } catch {
      json = text
    }
  }
  if (!response.ok()) {
    throw new Error(`${method} ${path} returned HTTP ${String(response.status())}: ${text}`)
  }
  return { status: response.status(), location: response.headers()['location'] ?? null, json }
}

export async function findApiResourceId(
  session: ManagementSession,
  baseApiUrl: string,
  identifier: string,
): Promise<string | undefined> {
  const { json } = await managementApiCall(
    session,
    baseApiUrl,
    'GET',
    `/api-resources?filter=${encodeURIComponent(`identifier eq ${identifier}`)}`,
  )
  const resources = (json as { apiResources?: { id: string; identifier: string }[] }).apiResources ?? []
  return resources.find((resource) => resource.identifier === identifier)?.id
}

export async function findApplicationId(
  session: ManagementSession,
  baseApiUrl: string,
  name: string,
): Promise<string | undefined> {
  const { json } = await managementApiCall(
    session,
    baseApiUrl,
    'GET',
    `/applications?filter=${encodeURIComponent(`name eq "${name}"`)}`,
  )
  const applications = (json as { applications?: { id: string; name: string }[] }).applications ?? []
  return applications.find((application) => application.name === name)?.id
}

export async function createM2mApplication(
  session: ManagementSession,
  baseApiUrl: string,
  name: string,
  description: string,
): Promise<string> {
  const { location } = await managementApiCall(session, baseApiUrl, 'POST', '/applications', {
    name,
    description,
    templateId: 'm2m-application',
    inboundProtocolConfiguration: { oidc: { grantTypes: ['client_credentials'] } },
  })
  const id = location?.split('/').pop()
  if (!id) {
    throw new Error(`${name}: the Identity Server accepted the application but returned no Location header.`)
  }
  return id
}

/** Idempotent - a no-op if `apiResourceId` is already authorized on `applicationId`. */
export async function authorizeApiResource(
  session: ManagementSession,
  baseApiUrl: string,
  applicationId: string,
  apiResourceId: string,
  scopes: string[],
): Promise<void> {
  const { json } = await managementApiCall(session, baseApiUrl, 'GET', `/applications/${applicationId}/authorized-apis`)
  const existing = (json as { id: string }[] | undefined) ?? []
  if (existing.some((authorized) => authorized.id === apiResourceId)) {
    return
  }
  await managementApiCall(session, baseApiUrl, 'POST', `/applications/${applicationId}/authorized-apis`, {
    id: apiResourceId,
    policyIdentifier: 'RBAC',
    scopes,
  })
}

export async function readOidcCredentials(
  session: ManagementSession,
  baseApiUrl: string,
  applicationId: string,
): Promise<{ clientId: string; clientSecret: string }> {
  const { json } = await managementApiCall(session, baseApiUrl, 'GET', `/applications/${applicationId}/inbound-protocols/oidc`)
  const oidc = json as { clientId?: string; clientSecret?: string }
  if (!oidc.clientId || !oidc.clientSecret) {
    throw new Error(`Application ${applicationId} has no OIDC client id/secret.`)
  }
  return { clientId: oidc.clientId, clientSecret: oidc.clientSecret }
}
