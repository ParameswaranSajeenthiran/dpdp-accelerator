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

import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

const sdk = vi.hoisted(() => ({
  getBasicUserInfo: vi.fn(),
  getDecodedIDToken: vi.fn(),
  initialize: vi.fn(),
  isAuthenticated: vi.fn(),
  signIn: vi.fn(),
  signOut: vi.fn(),
}))

vi.mock('@asgardeo/auth-spa', () => ({
  AsgardeoSPAClient: { getInstance: () => sdk },
  Storage: { WebWorker: 'webWorker' },
}))

/** Fresh module instance so the one-shot initialisation latch is not shared. */
async function loadAuthClient() {
  vi.resetModules()
  return import('../utils/authClient')
}

function jsonResponse(payload: unknown, status = 200): Response {
  return new Response(JSON.stringify(payload), {
    status,
    headers: { 'Content-Type': 'application/json' },
  })
}

/** No deployment config and no parked authorization code. */
function respondNotFound(): void {
  vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response('', { status: 404 })))
}

beforeEach(() => {
  vi.stubEnv('VITE_AUTH_ENABLED', 'true')
  sdk.initialize.mockResolvedValue(true)
  sdk.isAuthenticated.mockResolvedValue(false)
  sdk.signIn.mockResolvedValue(undefined)
  sdk.signOut.mockResolvedValue(true)
  respondNotFound()
})

afterEach(() => {
  vi.clearAllMocks()
  vi.restoreAllMocks()
  vi.unstubAllGlobals()
  vi.unstubAllEnvs()
  sessionStorage.clear()
  window.history.replaceState({}, '', '/')
})

describe('authClient initialisation', () => {
  it('configures a public client with worker held tokens against the tenant base', async () => {
    const { initAuth } = await loadAuthClient()

    await initAuth()

    expect(sdk.initialize).toHaveBeenCalledOnce()
    const config = sdk.initialize.mock.calls[0]?.[0]
    expect(config).toMatchObject({
      baseUrl: window.location.origin,
      clientID: 'DPDP_CONSENT_PORTAL',
      enablePKCE: true,
      // Unslashed: the slashed form costs a 302 back to this one.
      signInRedirectURL: `${window.location.origin}/consent-portal`,
      signOutRedirectURL: `${window.location.origin}/consent-portal`,
      storage: 'webWorker',
    })
    expect(config.resourceServerURLs).toEqual([window.location.origin])
    expect(config.scope).toContain('internal_consent_mgt_consent_view')
    expect(config.scope).not.toContain('SYSTEM')
  })

  it('takes the client id and scopes from the deployment configuration', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn(async (input: RequestInfo | URL) =>
        String(input).endsWith('/deployment.config.json')
          ? jsonResponse({ clientID: 'TENANT_PORTAL', scope: ['openid', 'internal_login'] })
          : new Response('', { status: 404 }),
      ),
    )
    const { initAuth } = await loadAuthClient()

    await initAuth()

    expect(sdk.initialize.mock.calls[0]?.[0]).toMatchObject({
      clientID: 'TENANT_PORTAL',
      scope: ['openid', 'internal_login'],
    })
  })

  it('initialises once even when called concurrently', async () => {
    const { initAuth } = await loadAuthClient()

    await Promise.all([initAuth(), initAuth(), initAuth()])

    expect(sdk.initialize).toHaveBeenCalledOnce()
  })

  it('allows a retry after a failed initialisation', async () => {
    sdk.initialize.mockRejectedValueOnce(new Error('boom'))
    const { initAuth } = await loadAuthClient()

    await expect(initAuth()).rejects.toThrow('boom')
    await expect(initAuth()).resolves.toBeUndefined()
    expect(sdk.initialize).toHaveBeenCalledTimes(2)
  })
})

describe('signing in', () => {
  it('reports an existing session without touching the Identity Server', async () => {
    sdk.isAuthenticated.mockResolvedValue(true)
    const { ensureSignedIn } = await loadAuthClient()

    await expect(ensureSignedIn()).resolves.toBe(true)
    expect(sdk.signIn).not.toHaveBeenCalled()
  })

  it('completes the flow with the code the shell parked in the session', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn(async (input: RequestInfo | URL) =>
        String(input).endsWith('/consent-portal/auth')
          ? jsonResponse({ authCode: 'code-1', sessionState: 'session-1', state: 'state-1' })
          : new Response('', { status: 404 }),
      ),
    )
    sdk.isAuthenticated.mockResolvedValueOnce(false).mockResolvedValue(true)
    const { ensureSignedIn } = await loadAuthClient()

    await expect(ensureSignedIn()).resolves.toBe(true)
    expect(sdk.signIn).toHaveBeenCalledWith(
      { callOnlyOnRedirect: false },
      'code-1',
      'session-1',
      'state-1',
    )
  })

  it('ignores an empty handoff and starts a fresh sign-in', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn(async (input: RequestInfo | URL) =>
        String(input).endsWith('/consent-portal/auth')
          ? jsonResponse({ authCode: '', sessionState: '', state: '' })
          : new Response('', { status: 404 }),
      ),
    )
    const { ensureSignedIn } = await loadAuthClient()

    await expect(ensureSignedIn()).resolves.toBe(false)
    expect(sdk.signIn).toHaveBeenCalledWith()
  })

  it('redirects to the Identity Server when the shell is absent, as in dev', async () => {
    const { ensureSignedIn } = await loadAuthClient()

    await expect(ensureSignedIn()).resolves.toBe(false)
    expect(sdk.signIn).toHaveBeenCalledWith()
  })
})

describe('returning to the requested route', () => {
  it('remembers the route, relative to the base, before leaving for the server', async () => {
    window.history.replaceState({}, '', '/consent-portal/consents?status=ACTIVE')
    const { ensureSignedIn, takeReturnPath } = await loadAuthClient()

    await ensureSignedIn()

    expect(takeReturnPath()).toBe('/consents?status=ACTIVE')
  })

  it('strips the tenant prefix so the router can navigate by the route alone', async () => {
    window.history.replaceState({}, '', '/t/wso2.com/consent-portal/purposes/42')
    const { ensureSignedIn, takeReturnPath } = await loadAuthClient()

    await ensureSignedIn()

    expect(takeReturnPath()).toBe('/purposes/42')
  })

  it('records nothing when the sign-in starts from the application home', async () => {
    window.history.replaceState({}, '', '/consent-portal/')
    const { ensureSignedIn, takeReturnPath } = await loadAuthClient()

    await ensureSignedIn()

    expect(takeReturnPath()).toBeUndefined()
  })

  it('hands the route over exactly once', async () => {
    window.history.replaceState({}, '', '/consent-portal/elements')
    const { ensureSignedIn, takeReturnPath } = await loadAuthClient()

    await ensureSignedIn()

    expect(takeReturnPath()).toBe('/elements')
    expect(takeReturnPath()).toBeUndefined()
  })

  it('discards a stored value that would navigate off site', async () => {
    sessionStorage.setItem('consent-portal.returnPath', '//evil.example/consents')
    const { takeReturnPath } = await loadAuthClient()

    expect(takeReturnPath()).toBeUndefined()
    expect(sessionStorage.getItem('consent-portal.returnPath')).toBeNull()
  })

  it('completes the sign-in even when session storage is unavailable', async () => {
    window.history.replaceState({}, '', '/consent-portal/consents')
    vi.spyOn(Storage.prototype, 'setItem').mockImplementation(() => {
      throw new Error('storage disabled')
    })
    const { ensureSignedIn } = await loadAuthClient()

    await expect(ensureSignedIn()).resolves.toBe(false)
    expect(sdk.signIn).toHaveBeenCalledWith()
  })
})

describe('session helpers', () => {
  it('signs out through the SDK', async () => {
    const { logout } = await loadAuthClient()

    await logout()

    expect(sdk.signOut).toHaveBeenCalledOnce()
  })

  it('reads profile claims from the ID token', async () => {
    sdk.getDecodedIDToken.mockResolvedValue({ username: 'alice' })
    const { getUserProfile } = await loadAuthClient()

    await expect(getUserProfile()).resolves.toEqual({ username: 'alice' })
  })

  it('does nothing when authentication is switched off', async () => {
    vi.stubEnv('VITE_AUTH_ENABLED', 'false')
    const { ensureSignedIn, logout, isAuthenticated } = await loadAuthClient()

    await expect(ensureSignedIn()).resolves.toBe(true)
    await expect(isAuthenticated()).resolves.toBe(true)
    await logout()

    expect(sdk.initialize).not.toHaveBeenCalled()
    expect(sdk.signIn).not.toHaveBeenCalled()
    expect(sdk.signOut).not.toHaveBeenCalled()
  })
})
