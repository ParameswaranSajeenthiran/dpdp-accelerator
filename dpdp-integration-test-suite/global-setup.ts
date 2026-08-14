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

import { mkdirSync } from 'node:fs'
import { chromium } from '@playwright/test'
import { LoginPage } from './pages/LoginPage'
import { authHeadersFromStorageState } from './utils/authStorage'
import { authStateFile, consentPurposesApiUrl, env, type Persona, type PersonaName } from './utils/env'

// Node's global fetch (unlike Playwright's own browser/request APIs) has no per-call option to
// ignore an untrusted certificate - it only honors this process-wide env var. The shipped
// Identity Server certificate is self-signed, so without this every plain fetch() below fails
// with a generic "fetch failed" before ever reaching the reachability check it's meant to guard.
if (env.ignoreHttpsErrors) {
  process.env.NODE_TLS_REJECT_UNAUTHORIZED = '0'
}

/**
 * Runs once before the whole suite. This is a real environment (no fake IdP to inject tokens
 * into), so the only way to get an authenticated session is to actually drive the Identity
 * Server's login form once per persona - done here, up front, rather than in every test, and
 * the resulting storageState is what fixtures/auth.fixtures.ts and utils/authStorage.ts reuse
 * for the rest of the run.
 */
async function checkReachable(url: string, label: string): Promise<void> {
  try {
    const response = await fetch(url, { signal: AbortSignal.timeout(10_000) })
    if (response.status >= 500) {
      throw new Error(`${label} at ${url} responded with ${String(response.status)}`)
    }
  } catch (error) {
    throw new Error(
      `${label} at ${url} is not reachable. Is the WSO2 IS instance with the DPDP accelerator ` +
        `deployed actually running? See README.md. Cause: ${(error as Error).message}`,
    )
  }
}

/**
 * WSO2 IS invalidates a session when the same account logs in again elsewhere (e.g. a real
 * browser tab left open, or a previous Playwright run's session that never got closed) - which
 * shows up as unexplained timeouts partway through a run. `/api/users/v1/me/sessions` is IS's own
 * self-service session-management API: plain HTTP Basic auth with the account's own credentials
 * is enough (verified live - no OAuth token, no special role needed), and DELETE terminates every
 * active session for that account, including ones from a real browser. This only clears sessions
 * that already existed before this run starts - it can't stop a *new* login (e.g. someone signing
 * into the portal manually) from colliding with this run's own session once it's underway.
 */
async function terminateAllSessions(persona: Persona): Promise<void> {
  const credentials = Buffer.from(`${persona.username}:${persona.password}`).toString('base64')
  try {
    const response = await fetch(`${env.identityServerBaseUrl}/api/users/v1/me/sessions`, {
      method: 'DELETE',
      headers: { Authorization: `Basic ${credentials}` },
      signal: AbortSignal.timeout(10_000),
    })
    if (response.status !== 204) {
      console.warn(
        `Could not terminate ${persona.username}'s existing sessions (status ${String(response.status)}) - proceeding anyway.`,
      )
    }
  } catch (error) {
    console.warn(
      `Could not terminate ${persona.username}'s existing sessions - proceeding anyway. Cause: ${(error as Error).message}`,
    )
  }
}

async function loginAndSave(persona: Persona, personaName: PersonaName): Promise<void> {
  const browser = await chromium.launch()
  const context = await browser.newContext({ ignoreHTTPSErrors: env.ignoreHttpsErrors })
  const page = await context.newPage()

  await page.goto(`${env.portalBaseUrl}/`, { waitUntil: 'networkidle' })
  await new LoginPage(page).signIn(persona)

  // The callback redirect chain lands back on the SPA; wait for the BFF's own session probe to
  // succeed as proof the login actually completed (a wrong password re-renders the same IS
  // login form instead of navigating anywhere, which this would time out on).
  await page.waitForResponse(
    (response) => response.url().endsWith('/me') && response.status() === 200,
    { timeout: 30_000 },
  )

  await context.storageState({ path: authStateFile(personaName) })
  await browser.close()
}

/**
 * A successful login only proves the consent-admin persona's credentials are valid, not that the
 * account actually holds the `dpdp-consent-admin` role - that role assignment is a manual Console
 * step (see docs/configuration-guide.md step 4) that's easy to forget for a freshly created test
 * account. Without this check, a missing role surfaces as dozens of unrelated, confusing
 * assertion failures scattered across the suite (every seeded Purpose/Element/Consent creation
 * silently 401s/403s) instead of one clear error naming the actual problem, right at setup.
 */
async function verifyConsentAdminAuthorized(): Promise<void> {
  const headers = authHeadersFromStorageState(authStateFile('consent-admin'))
  const response = await fetch(consentPurposesApiUrl(''), {
    headers: { ...headers },
    signal: AbortSignal.timeout(10_000),
  })
  if (response.status === 401 || response.status === 403) {
    throw new Error(
      `TEST_CONSENT_ADMIN_USERNAME ("${env.consentAdmin.username}") logged in successfully but ` +
        `is not authorized for the consent-management admin API (got ${String(response.status)} ` +
        `from ${consentPurposesApiUrl('')}). Assign this account the dpdp-consent-admin role in ` +
        `the Console - see docs/configuration-guide.md step 4.`,
    )
  }
}

export default async function globalSetup(): Promise<void> {
  mkdirSync(env.authStateDir, { recursive: true })

  // The same endpoint the BFF's TokenValidator itself depends on (identity.server.internal.base.url
  // + /oauth2/jwks) - reachability here is a direct precondition for every login this suite does.
  await checkReachable(`${env.identityServerBaseUrl}/oauth2/jwks`, 'Identity Server')
  await checkReachable(`${env.portalBaseUrl}/`, 'Consent portal')

  await terminateAllSessions(env.dataPrincipal)
  await loginAndSave(env.dataPrincipal, 'data-principal')

  await terminateAllSessions(env.consentAdmin)
  await loginAndSave(env.consentAdmin, 'consent-admin')
  await verifyConsentAdminAuthorized()

  const secondPrincipal = env.secondDataPrincipal()
  if (secondPrincipal) {
    await terminateAllSessions(secondPrincipal)
    await loginAndSave(secondPrincipal, 'data-principal-2')
  } else {
    console.log(
      'TEST_DATA_PRINCIPAL_2_USERNAME/PASSWORD not set - ownership-isolation tests that need a ' +
        'second real user will skip themselves.',
    )
  }
}
