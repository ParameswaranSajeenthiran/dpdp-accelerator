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

import { env } from './utils/env'

// Node's global fetch (unlike Playwright's own browser/request APIs) has no per-call option to
// ignore an untrusted certificate - it only honors this process-wide env var. The shipped
// Identity Server certificate is self-signed, so without this every plain fetch() below fails
// with a generic "fetch failed" before ever reaching the reachability check it's meant to guard.
if (env.ignoreHttpsErrors) {
  process.env.NODE_TLS_REJECT_UNAUTHORIZED = '0'
}

/**
 * Runs once before the whole suite, in Playwright's own separate globalSetup process. This only
 * checks that the target environment is actually up - it does NOT log any persona in. Login
 * happens lazily instead, the first time any test in the run actually needs a given persona - see
 * fixtures/auth.fixtures.ts's getPersonaState, which caches the result to a file under `.auth/`
 * so every worker process can reuse it without needing to log in itself.
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

export default async function globalSetup(): Promise<void> {
  // The same endpoint the BFF's TokenValidator itself depends on (identity.server.internal.base.url
  // + /oauth2/jwks) - reachability here is a direct precondition for every login this suite does.
  await checkReachable(`${env.identityServerBaseUrl}/oauth2/jwks`, 'Identity Server')
  await checkReachable(`${env.portalBaseUrl}/`, 'Consent portal')
}
