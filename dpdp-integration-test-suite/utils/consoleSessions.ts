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

import { type Browser, type Locator, type Page } from '@playwright/test'
import { LoginPage } from '../pages/LoginPage'
import type { Persona } from './env'
import { ignoreHttpsErrors } from './serverConfig'

export async function fillLoginForm(page: Page, persona: Persona): Promise<void> {
  const loginPage = new LoginPage(page)
  await loginPage.signIn(persona)
  if (await loginPage.errorMessage.isVisible({ timeout: 5_000 }).catch(() => false)) {
    const message = (await loginPage.errorMessage.textContent())?.trim()
    throw new Error(`Sign-in failed for persona "${persona.username}": ${message ?? 'Login failed.'}`)
  }
}

/**
 * Logs into a Console URL as `persona`, in a fresh context. Retried as a whole (fresh context
 * each attempt): Console's own token exchange sometimes fails server-side in a way the SPA never
 * recovers from - a fresh cookie jar and a fresh authorize round is the only thing that clears it.
 */
export async function loginToConsole(
  browser: Browser,
  consoleUrl: string,
  persona: Persona,
  ready?: (page: Page) => Locator,
): Promise<Page> {
  let lastError: unknown

  for (let attempt = 1; attempt <= 3; attempt += 1) {
    const context = await browser.newContext({ ignoreHTTPSErrors: ignoreHttpsErrors })
    const page = await context.newPage()

    try {
      await page.goto(consoleUrl, { waitUntil: 'domcontentloaded' })
      await page.locator('#usernameUserInput').waitFor({ state: 'visible', timeout: 20_000 })
      await fillLoginForm(page, persona)
      await page.locator('#usernameUserInput').waitFor({ state: 'hidden', timeout: 30_000 })
      await page.waitForLoadState('networkidle', { timeout: 30_000 }).catch(() => undefined)
      if (ready) {
        await ready(page).waitFor({ state: 'visible', timeout: 30_000 })
      }
      return page
    } catch (error) {
      lastError = error
      await context.close()
    }
  }

  throw new Error(
    `Console at ${consoleUrl} never became usable for "${persona.username}" after 3 attempts: ` +
      `${lastError instanceof Error ? lastError.message : String(lastError)}`,
  )
}
