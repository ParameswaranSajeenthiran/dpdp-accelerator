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

import { existsSync } from 'node:fs'
import { test as base, type Page } from '@playwright/test'
import { ConsentApiClient } from '../clients/ConsentApiClient'
import { authHeadersFromStorageState } from '../utils/authStorage'
import { authStateFile, env } from '../utils/env'

/**
 * Every fixture here represents an already-authenticated "state" (per the fixtures/ folder's
 * job in this suite) built from a real login global-setup.ts performed once, up front, against
 * the real Identity Server - never a login this fixture performs itself, since re-logging in
 * per test would make most of the suite as slow as a single browser-driven login flow.
 */

/**
 * A place for a test to register the id of an Element or Purpose it created through the UI, so
 * it gets deleted again once the test finishes - without this, every regression run only adds
 * data (see tests/consents/plan.md on tolerating a persistent environment). The one deliberate
 * exception is the realistic demo dataset (see utils/consentCleanup.ts's RICH exports,
 * consent-lifecycle-demo.spec.ts, and seed-demo-data.spec.ts's seed test) - that's meant to stay
 * in the environment permanently as a realistic backdrop, so nothing in this suite ever tracks it
 * here.
 */
export interface ConsentCleanupTracker {
  trackElement: (id: string) => void
  trackPurpose: (id: string) => void
}

interface Fixtures {
  dataPrincipalPage: Page
  consentAdminPage: Page
  dataPrincipalConsentApi: ConsentApiClient
  consentAdminConsentApi: ConsentApiClient
  consentCleanupTracker: ConsentCleanupTracker
}

export const test = base.extend<Fixtures>({
  dataPrincipalPage: async ({ browser }, use) => {
    // browser.newContext() here bypasses playwright.config.ts's `use` block entirely (that's
    // only auto-applied to the base test's own default context/page) - baseURL and
    // ignoreHTTPSErrors have to be passed explicitly or relative goto() calls break and the
    // self-signed cert kills every navigation.
    const context = await browser.newContext({
      storageState: authStateFile('data-principal'),
      baseURL: env.portalNavigationBaseUrl,
      ignoreHTTPSErrors: env.ignoreHttpsErrors,
    })
    const page = await context.newPage()
    await use(page)
    await context.close()
  },

  consentAdminPage: async ({ browser }, use) => {
    const context = await browser.newContext({
      storageState: authStateFile('consent-admin'),
      baseURL: env.portalNavigationBaseUrl,
      ignoreHTTPSErrors: env.ignoreHttpsErrors,
    })
    const page = await context.newPage()
    await use(page)
    await context.close()
  },

  dataPrincipalConsentApi: async ({ request }, use) => {
    await use(new ConsentApiClient(request, authHeadersFromStorageState(authStateFile('data-principal'))))
  },

  consentAdminConsentApi: async ({ request }, use) => {
    await use(new ConsentApiClient(request, authHeadersFromStorageState(authStateFile('consent-admin'))))
  },

  consentCleanupTracker: async ({ consentAdminConsentApi }, use) => {
    const elementIds: string[] = []
    const purposeIds: string[] = []
    await use({
      trackElement: (id) => elementIds.push(id),
      trackPurpose: (id) => purposeIds.push(id),
    })
    // Purposes first: an Element referenced by a Purpose 409s on delete. Both tolerate failure -
    // a 409 here just means something else (typically a Consent) still needs it, which the
    // consent-mgt v2 API has no way to force through (Consents can only be revoked, never
    // deleted) - so it's left in place rather than treated as a test failure.
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
 * decide whether to run at all, and skip themselves with a clear reason when it's false.
 */
export function hasSecondDataPrincipal(): boolean {
  return Boolean(env.secondDataPrincipal()) && existsSync(authStateFile('data-principal-2'))
}
