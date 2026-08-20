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

import { config as loadDotenv } from 'dotenv'
import path from 'node:path'

// .env.example is committed and carries non-secret defaults; a gitignored .env overrides it
// with the real per-environment values (credentials, non-default hosts). Loaded in this order
// so .env.example always applies first and .env only overrides what it actually sets.
loadDotenv({ path: path.resolve(import.meta.dirname, '..', '.env.example') })
loadDotenv({ path: path.resolve(import.meta.dirname, '..', '.env'), override: true })

function required(name: string): string {
  const value = process.env[name]
  if (!value) {
    throw new Error(
      `Missing required environment variable "${name}". Copy .env.example to .env and fill it in - see README.md.`,
    )
  }
  return value
}

function optional(name: string): string | undefined {
  const value = process.env[name]
  return value && value.length > 0 ? value : undefined
}

function trimTrailingSlash(value: string): string {
  return value.endsWith('/') ? value.slice(0, -1) : value
}

export interface Persona {
  username: string
  password: string
}

const rawPortalBaseUrl = trimTrailingSlash(required('PORTAL_BASE_URL'))

export const env = {
  portalBaseUrl: rawPortalBaseUrl,
  // Playwright's `baseURL` resolves a leading-slash relative goto() (e.g. page.goto('/consents'),
  // used throughout pages/) per the WHATWG URL spec: a leading slash REPLACES the base's own path
  // rather than appending to it. Without the trailing slash here, page.goto('/consents') against
  // baseURL "https://host:9443/consent-portal" resolves to "https://host:9443/consents" - outside
  // the portal entirely, and outside the deployment.toml rule that exempts "(.*)/consent-portal(.*)"
  // from the Identity Server's own auth valve - which is exactly the confusing 401 this cost hours
  // chasing as an OAuth/IS problem before landing here. portalBaseUrl itself stays slash-free since
  // the consent API URL helpers below need that form.
  portalNavigationBaseUrl: `${rawPortalBaseUrl}/`,
  identityServerBaseUrl: trimTrailingSlash(required('IS_BASE_URL')),
  ignoreHttpsErrors: (process.env.IGNORE_HTTPS_ERRORS ?? 'true') === 'true',

  user: {
    username: required('TEST_USER_USERNAME'),
    password: required('TEST_USER_PASSWORD'),
  } satisfies Persona,

  // Must be a real user assigned the dpdp-consent-admin role (created manually in the Console
  // per docs/configuration-guide.md step 4; role membership itself is not automated - assign it
  // via the Console app after creating the role). Grants every internal_consent_mgt_* scope, so
  // this single persona both drives the admin consent registry UI and creates
  // Purposes/Elements/Consents via the API as test setup for the UI layer.
  consentAdmin: {
    username: required('TEST_CONSENT_ADMIN_USERNAME'),
    password: required('TEST_CONSENT_ADMIN_PASSWORD'),
  } satisfies Persona,

  /**
   * Optional: a second user account, used only by ownership-isolation tests that
   * need two distinct real users. Those tests skip themselves when this isn't configured,
   * since a real environment can't fabricate extra user accounts the way a stubbed IdP could.
   */
  secondUser: (): Persona | undefined => {
    const username = optional('TEST_USER_2_USERNAME')
    const password = optional('TEST_USER_2_PASSWORD')
    return username && password ? { username, password } : undefined
  },
}

// The BFF's own consent routes (AdminApiServlet / MyConsentsServlet), proxied 1:1 to WSO2 IS's
// consent-mgt v2 API - see clients/ConsentApiClient.ts for the full contract.
export function myConsentsApiUrl(path: string): string {
  return `${env.portalBaseUrl}/me/consents${path}`
}

export function adminConsentsApiUrl(path: string): string {
  return `${env.portalBaseUrl}/api/consents${path}`
}

export function consentPurposesApiUrl(path: string): string {
  return `${env.portalBaseUrl}/api/consent-purposes${path}`
}

export function consentElementsApiUrl(path: string): string {
  return `${env.portalBaseUrl}/api/consent-elements${path}`
}

export type PersonaName = 'user' | 'user-2' | 'consent-admin'
