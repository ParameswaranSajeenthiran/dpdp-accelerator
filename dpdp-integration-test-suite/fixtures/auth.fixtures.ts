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

import { mkdir, open, readFile, rm, writeFile } from 'node:fs/promises'
import path from 'node:path'
import { expect, test as base, type Browser, type Page } from '@playwright/test'
import { ConsentApiClient } from '../clients/ConsentApiClient'
import { LoginPage } from '../pages/LoginPage'
import { authHeadersFromStorageState, type PersonaStorageState } from '../utils/authStorage'
import { consentPurposesApiUrl, env, type Persona, type PersonaName } from '../utils/env'

/**
 * Every fixture here represents an already-authenticated "state" (per the fixtures/ folder's job
 * in this suite), built from a real login driven against the real Identity Server - never a
 * stub. A given persona only ever logs in once for the whole run, the first time any test needs
 * it - see getPersonaState below for how that one login's result is cached to a gitignored file
 * under `.auth/` and read back by every later test/worker that needs the same persona, instead of
 * each one logging in for itself.
 */

/**
 * A place for a test to register the id of an Element or Purpose it created through the UI, so
 * it gets deleted again once the test finishes - without this, every regression run only adds
 * data.
 */
export interface ConsentCleanupTracker {
  trackElement: (id: string) => void
  trackPurpose: (id: string) => void
}

interface Fixtures {
  dataPrincipalConsentApi: ConsentApiClient
  consentAdminConsentApi: ConsentApiClient
  consentCleanupTracker: ConsentCleanupTracker
}

/**
 * WSO2 IS invalidates a session when the same account logs in again elsewhere (e.g. a real
 * browser tab left open, or a previous run's session that never got closed) - which shows up as
 * unexplained timeouts partway through a run. `/api/users/v1/me/sessions` is IS's own
 * self-service session-management API: plain HTTP Basic auth with the account's own credentials
 * is enough (verified live - no OAuth token, no special role needed), and DELETE terminates every
 * active session for that account, including ones from a real browser. Only called once, right
 * before a persona's one real login for the whole run (see getPersonaState) - it can't stop a
 * *new* login (e.g. someone signing into the portal manually) from colliding with this run's own
 * session once it's underway.
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

/**
 * A successful login only proves the consent-admin persona's credentials are valid, not that the
 * account actually holds the `dpdp-consent-admin` role - that role assignment is a manual Console
 * step (see docs/configuration-guide.md step 4) that's easy to forget for a freshly created test
 * account. Without this check, a missing role surfaces as dozens of unrelated, confusing
 * assertion failures scattered across the suite (every seeded Purpose/Element/Consent creation
 * silently 401s/403s) instead of one clear error naming the actual problem, right at this
 * persona's one login for the run.
 */
async function verifyConsentAdminAuthorized(state: PersonaStorageState): Promise<void> {
  const headers = authHeadersFromStorageState(state)
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

/**
 * Drives the real Identity Server login form once and captures the resulting session -
 * getPersonaState is the one place that persists it, to `.auth/<persona>.json`. Reuses the
 * worker's own `browser` (Playwright's built-in `browser` fixture is worker-scoped, i.e. one
 * instance per worker process already) for a throwaway context/page rather than launching a
 * separate browser just for this.
 *
 * This is the one place in the whole suite that actually exercises the real login page - every
 * other fixture/test just reuses the storageState captured here (see getPersonaState), so the
 * login form itself is verified as part of this already-existing flow rather than by a separate,
 * dedicated login test: the assertion below confirms the real form rendered, and the error-message
 * race below turns a bad password into an immediate, clear failure instead of the generic 30s
 * timeout waiting for a callback that a failed login never sends.
 */
async function loginAndCaptureState(browser: Browser, persona: Persona): Promise<PersonaStorageState> {
  const context = await browser.newContext({ ignoreHTTPSErrors: env.ignoreHttpsErrors })
  try {
    const page = await context.newPage()
    const loginPage = new LoginPage(page)

    await page.goto(`${env.portalBaseUrl}/`, { waitUntil: 'networkidle' })
    await expect(page.locator('#usernameUserInput')).toBeVisible()
    await expect(page.locator('#password')).toBeVisible()
    await expect(page.locator('#sign-in-button')).toBeVisible()

    await loginPage.signIn(persona)

    // The callback redirect chain lands back on the SPA; wait for the BFF's own session probe to
    // succeed as proof the login actually completed (a wrong password re-renders the same IS
    // login form instead of navigating anywhere, which this would time out on). Racing this
    // against a *poll* for the error banner - rather than against `errorMessage.waitFor(...)`
    // directly - matters on the happy path: `Promise.race` never cancels its loser, so a raced
    // `waitFor` would keep running in the background after `/me` already won, only to time out on
    // its own ~30s later and get recorded as a failed step in the trace/report even though it
    // never affected the outcome. The poll below stops itself the instant either side settles.
    const outcome = { settled: false }
    const meResponse = page
      .waitForResponse((response) => response.url().endsWith('/me') && response.status() === 200, {
        timeout: 30_000,
      })
      .finally(() => {
        outcome.settled = true
      })
    const errorPoll = (async () => {
      while (!outcome.settled) {
        if (await loginPage.errorMessage.isVisible().catch(() => false)) {
          const message = (await loginPage.errorMessage.textContent())?.trim()
          throw new Error(`Login failed for persona "${persona.username}": ${message ?? 'Login failed.'}`)
        }
        await new Promise((resolve) => {
          setTimeout(resolve, 250)
        })
      }
    })()
    await Promise.race([meResponse, errorPoll])

    return await context.storageState()
  } finally {
    // Closed on every path, including a failed/timed-out login - without this, a misconfigured
    // persona leaks one browser context per attempt (and getPersonaState may retry this from
    // several call sites over the run).
    await context.close()
  }
}

/**
 * Cross-process cache: one gitignored JSON file per persona under `.auth/`, holding exactly the
 * object `context.storageState()` returns. A file can be read by any worker regardless of which
 * one happens to run first, so a persona logs in at most once for the whole run, not once per
 * test or per worker. `global-teardown.ts` deletes this directory at the end of every run, so the
 * next run always starts with a fresh login.
 */
const AUTH_DIR = path.resolve(import.meta.dirname, '..', '.auth')

function authFilePath(personaName: PersonaName): string {
  return path.join(AUTH_DIR, `${personaName}.json`)
}

function lockFilePath(personaName: PersonaName): string {
  return path.join(AUTH_DIR, `${personaName}.lock`)
}

async function readCachedState(personaName: PersonaName): Promise<PersonaStorageState | undefined> {
  try {
    return JSON.parse(await readFile(authFilePath(personaName), 'utf-8')) as PersonaStorageState
  } catch {
    return undefined
  }
}

/**
 * Cross-process mutex for "who gets to log in": `open(path, 'wx')` is an atomic, exclusive
 * filesystem create that fails with EEXIST if the lock file already exists, so at most one
 * worker process ever wins this race for a given persona. Only held for the brief moment of the
 * actual login and file write, not for a whole test - once the file exists, every later caller
 * just reads it, no locking involved. Without this lock, two tests that both find no cached
 * session yet (the very first use of a persona in the run) would both call terminateAllSessions
 * and log in, and whichever finished second would invalidate the first one's session out from
 * under whatever test was already using it.
 */
async function acquireLoginLock(personaName: PersonaName): Promise<boolean> {
  await mkdir(AUTH_DIR, { recursive: true })
  try {
    await (await open(lockFilePath(personaName), 'wx')).close()
    return true
  } catch (error) {
    if ((error as NodeJS.ErrnoException).code === 'EEXIST') {
      return false
    }
    throw error
  }
}

async function releaseLoginLock(personaName: PersonaName): Promise<void> {
  await rm(lockFilePath(personaName), { force: true })
}

/**
 * Polls for the cache file a different worker's in-flight login (see acquireLoginLock) is about
 * to write, instead of attempting a second login of its own. If that other login fails outright,
 * its own test reports the real error; a test blocked here instead times out with the generic
 * message below - check the run for a failed login test first.
 */
async function waitForCachedState(personaName: PersonaName): Promise<PersonaStorageState> {
  const deadline = Date.now() + 60_000
  while (Date.now() < deadline) {
    const cached = await readCachedState(personaName)
    if (cached) {
      return cached
    }
    await new Promise((resolve) => {
      setTimeout(resolve, 500)
    })
  }
  throw new Error(
    `Timed out waiting for another worker to finish logging in as "${personaName}" - check the ` +
      'run for a failed login test, which is the likely real cause.',
  )
}

/**
 * Returns the cached session for `personaName`, logging in for real (after terminating any
 * session left over from elsewhere) only the first time any test in the run needs it - every
 * later call, from any fixture, any test, any worker, just reads the file this first call wrote.
 * Shared by every fixture below, and by the one place outside the fixtures that needs a persona
 * this suite has no always-on fixture for - the ownership-isolation test in
 * `tests/03-consents/03.02-data-principal-viewing-consents.spec.ts` calls this directly for
 * `data-principal-2`.
 */
export async function getPersonaState(
  browser: Browser,
  personaName: PersonaName,
  persona: Persona,
): Promise<PersonaStorageState> {
  const cached = await readCachedState(personaName)
  if (cached) {
    return cached
  }

  const acquiredLock = await acquireLoginLock(personaName)
  if (!acquiredLock) {
    return waitForCachedState(personaName)
  }

  try {
    // Another worker may have finished writing the file between our read above and acquiring
    // the lock just now - re-check before logging in again.
    const cachedAfterLock = await readCachedState(personaName)
    if (cachedAfterLock) {
      return cachedAfterLock
    }

    await terminateAllSessions(persona)
    const state = await loginAndCaptureState(browser, persona)
    if (personaName === 'consent-admin') {
      await verifyConsentAdminAuthorized(state)
    }
    await writeFile(authFilePath(personaName), JSON.stringify(state))
    return state
  } finally {
    await releaseLoginLock(personaName)
  }
}

/**
 * Explicit, per-test alternative to a `dataPrincipalPage`/`consentAdminPage` fixture: each test
 * calls this itself instead of Playwright silently injecting an already-authenticated page. Still
 * goes through getPersonaState, so it's exactly as cheap as the old fixture was - the persona logs
 * in for real only the first time any test in the run calls this, every later call (from any test,
 * any file, any worker) just reuses the cached storageState. The caller owns the returned page's
 * context and must close it itself (`await page.context().close()`) once the test is done with it.
 */
async function loginAs(browser: Browser, personaName: PersonaName, persona: Persona): Promise<Page> {
  const storageState = await getPersonaState(browser, personaName, persona)
  // browser.newContext() here bypasses playwright.config.ts's `use` block entirely (that's only
  // auto-applied to the base test's own default context/page) - baseURL and ignoreHTTPSErrors have
  // to be passed explicitly or relative goto() calls break and the self-signed cert kills every
  // navigation.
  const context = await browser.newContext({
    storageState,
    baseURL: env.portalNavigationBaseUrl,
    ignoreHTTPSErrors: env.ignoreHttpsErrors,
  })
  return context.newPage()
}

export async function loginAsDataPrincipal(browser: Browser): Promise<Page> {
  return loginAs(browser, 'data-principal', env.dataPrincipal)
}

export async function loginAsConsentAdmin(browser: Browser): Promise<Page> {
  return loginAs(browser, 'consent-admin', env.consentAdmin)
}

export const test = base.extend<Fixtures>({
  dataPrincipalConsentApi: async ({ browser, request }, use) => {
    const storageState = await getPersonaState(browser, 'data-principal', env.dataPrincipal)
    await use(new ConsentApiClient(request, authHeadersFromStorageState(storageState)))
  },

  consentAdminConsentApi: async ({ browser, request }, use) => {
    const storageState = await getPersonaState(browser, 'consent-admin', env.consentAdmin)
    await use(new ConsentApiClient(request, authHeadersFromStorageState(storageState)))
  },

  consentCleanupTracker: async ({ consentAdminConsentApi }, use) => {
    const elementIds: string[] = []
    const purposeIds: string[] = []
    await use({
      trackElement: (id) => elementIds.push(id),
      trackPurpose: (id) => purposeIds.push(id),
    })
    // Sequential cleanup, not perf-sensitive.
    for (const id of purposeIds) {
      await consentAdminConsentApi.deletePurpose(id).catch(() => undefined)
    }
    for (const id of elementIds) {
      await consentAdminConsentApi.deleteElement(id).catch(() => undefined)
    }
  },
})

export { expect } from '@playwright/test'

/**
 * Ownership-isolation tests need a second, distinct real Data Principal account, which a real
 * environment can't fabricate on demand the way a stubbed IdP could. Those tests call this to
 * decide whether to run at all, and skip themselves with a clear reason when it's false. The
 * actual login-success check for this persona happens lazily on first use inside
 * `getPersonaState`, the same way every other persona's fixture implicitly relies on its own
 * login succeeding.
 */
export function hasSecondDataPrincipal(): boolean {
  return Boolean(env.secondDataPrincipal())
}
