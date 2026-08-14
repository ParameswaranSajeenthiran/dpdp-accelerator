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

import { readFileSync } from 'node:fs'

// Matches react-apps/consent-portal PortalConstants.java / frontend authClient.ts's split-token
// cookie contract: part 1 is readable and becomes the Authorization header, part 2 is HttpOnly
// and is sent as a plain Cookie header here since this client has no browser cookie jar.
const ACCESS_TOKEN_PART1_COOKIE = 'portal-at-p1'
const ACCESS_TOKEN_PART2_COOKIE = 'portal-at-p2'

export interface AuthHeaders {
  Authorization: string
  Cookie: string
}

interface StorageStateCookie {
  name: string
  value: string
}

interface StorageState {
  cookies: StorageStateCookie[]
}

function readCookie(storageStatePath: string, name: string): string {
  const state = JSON.parse(readFileSync(storageStatePath, 'utf-8')) as StorageState
  const cookie = state.cookies.find((candidate) => candidate.name === name)
  if (!cookie) {
    throw new Error(
      `Cookie "${name}" was not found in ${storageStatePath}. Re-run global setup - login may have failed.`,
    )
  }
  return cookie.value
}

/**
 * Reads a Playwright storageState file produced by global-setup.ts's real login and turns it
 * into the two headers a raw (non-browser) API call needs to authenticate as that persona
 * against the BFF, which expects the split-token contract (Authorization: part 1, Cookie: part 2).
 */
export function authHeadersFromStorageState(storageStatePath: string): AuthHeaders {
  return {
    Authorization: `Bearer ${readCookie(storageStatePath, ACCESS_TOKEN_PART1_COOKIE)}`,
    Cookie: `${ACCESS_TOKEN_PART2_COOKIE}=${readCookie(storageStatePath, ACCESS_TOKEN_PART2_COOKIE)}`,
  }
}
