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

import type { Page } from '@playwright/test'
import type { Persona } from '../utils/env'

/**
 * The real WSO2 Identity Server basic-auth login form (authenticationendpoint/login.do),
 * reached by following the portal's own OAuth2/PKCE authorize redirect - never hit directly
 * with hand-built authorize query params, since the portal's BFF (`/auth/login`) is what
 * correctly constructs that URL (client id, PKCE challenge, requested scopes, state).
 */
export class LoginPage {
  constructor(private readonly page: Page) {}

  async signIn(persona: Persona): Promise<void> {
    // A first-run "cookie/privacy notice" banner sometimes covers the form; harmless to skip
    // if it isn't there.
    const dismissBanner = this.page.getByRole('button', { name: 'Got it' })
    if (await dismissBanner.isVisible().catch(() => false)) {
      await dismissBanner.click()
    }

    await this.page.locator('#usernameUserInput').fill(persona.username)
    await this.page.locator('#password').fill(persona.password)
    await this.page.locator('#sign-in-button').click()
  }
}
