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

import type { BrowserContext } from '@playwright/test'

// Matches react-apps/consent-portal PortalConstants.java / frontend authClient.ts's split-token
// cookie contract: part 1 is readable and becomes the Authorization header, part 2 is HttpOnly
// and is sent as a plain Cookie header here since this client has no browser cookie jar.
const ACCESS_TOKEN_PART1_COOKIE = 'portal-at-p1'
const ACCESS_TOKEN_PART2_COOKIE = 'portal-at-p2'

export interface AuthHeaders {
  Authorization: string
  Cookie: string
}

/**
 * The exact shape `BrowserContext.storageState()` returns (and `browser.newContext({storageState})`
 * accepts back) when called with no `path`. fixtures/auth.fixtures.ts's getPersonaState is the
 * one place that persists this, JSON-serialized as-is, to `.auth/<persona>.json`.
 */
export type PersonaStorageState = Awaited<ReturnType<BrowserContext['storageState']>>

function readCookie(state: PersonaStorageState, name: string): string {
  const cookie = state.cookies.find((candidate) => candidate.name === name)
  if (!cookie) {
    throw new Error(`Cookie "${name}" was not found in the persona's session state - login may have failed.`)
  }
  return cookie.value
}

/**
 * Turns an in-memory storageState object (from a real login driven in fixtures/auth.fixtures.ts)
 * into the two headers a raw (non-browser) API call needs to authenticate as that persona
 * against the BFF, which expects the split-token contract (Authorization: part 1, Cookie: part 2).
 */
export function authHeadersFromStorageState(state: PersonaStorageState): AuthHeaders {
  return {
    Authorization: `Bearer ${readCookie(state, ACCESS_TOKEN_PART1_COOKIE)}`,
    Cookie: `${ACCESS_TOKEN_PART2_COOKIE}=${readCookie(state, ACCESS_TOKEN_PART2_COOKIE)}`,
  }
}
