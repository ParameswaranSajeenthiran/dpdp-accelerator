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
import {
  APIError,
  apiRequest,
  apiRequestNoContent,
  apiRequestOptionalContent,
} from '../utils/apiClient'

const authMocks = vi.hoisted(() => ({
  httpRequest: vi.fn(),
  isAuthEnabled: vi.fn<() => boolean>(),
  login: vi.fn<() => Promise<void>>(),
}))

vi.mock('../utils/authClient', () => authMocks)

/** The shape the auth SDK hands back for a completed request. */
function sdkResponse(data: unknown, status = 200): { status: number; data: unknown } {
  return { status, data }
}

/** The SDK rejects on a non-2xx, axios style, with the body on `response`. */
function sdkFailure(status: number, data: unknown): Error {
  return Object.assign(new Error('request failed'), { response: { status, data } })
}

/** The single request config the client handed to the SDK. */
function requestConfig(index = 0): {
  url: string
  method?: string
  headers?: Record<string, string>
  data?: unknown
} {
  return authMocks.httpRequest.mock.calls[index]?.[0] as ReturnType<typeof requestConfig>
}

beforeEach(() => {
  vi.stubEnv('VITE_API_BASE_URL', 'http://api.example/')
  authMocks.isAuthEnabled.mockReturnValue(true)
  authMocks.login.mockResolvedValue()
})

afterEach(() => {
  vi.clearAllMocks()
  vi.unstubAllGlobals()
  vi.unstubAllEnvs()
})

describe('authenticated API client', () => {
  it('sends the request through the auth SDK so the worker can attach the token', async () => {
    authMocks.httpRequest.mockResolvedValue(sdkResponse({ ok: true }))

    await expect(
      apiRequest('/consents', { query: { limit: 10, active: true, ignored: undefined } }),
    ).resolves.toEqual({ ok: true })

    const config = requestConfig()
    expect(config.url).toBe('http://api.example/consents?limit=10&active=true')
    expect(config.method).toBe('GET')
    expect(config.headers?.Accept).toBe('application/json')
    // The page never holds an access token, so it must not set Authorization.
    expect(config.headers?.Authorization).toBeUndefined()
  })

  it('does not overwrite a caller-supplied Authorization header', async () => {
    authMocks.httpRequest.mockResolvedValue(sdkResponse({ ok: true }))

    await apiRequest('/consents', { headers: { Authorization: 'Custom credential' } })

    expect(requestConfig().headers?.Authorization).toBe('Custom credential')
  })

  it('does not start login for a 403 response', async () => {
    authMocks.httpRequest.mockRejectedValue(
      sdkFailure(403, { code: 'FORBIDDEN', message: 'insufficient permissions' }),
    )

    await expect(apiRequest('/admin')).rejects.toMatchObject({
      name: 'APIError',
      status: 403,
      code: 'FORBIDDEN',
      message: 'insufficient permissions',
    })
    expect(authMocks.login).not.toHaveBeenCalled()
  })

  it('prefers the Identity Server description over its terse message', async () => {
    authMocks.httpRequest.mockRejectedValue(
      sdkFailure(400, {
        code: 'CMT-60001',
        message: 'Bad Request',
        description: 'Consent id is not valid.',
      }),
    )

    await expect(apiRequest('/consents/x')).rejects.toMatchObject({
      status: 400,
      code: 'CMT-60001',
      message: 'Consent id is not valid.',
    })
  })

  it('starts login exactly once and fails immediately when unauthorized', async () => {
    authMocks.httpRequest.mockRejectedValue(sdkFailure(401, { code: 'UNAUTHORIZED' }))

    await expect(apiRequest('/consents')).rejects.toMatchObject({ status: 401 })

    // The SDK refreshes silently, so there is nothing left for us to retry.
    expect(authMocks.httpRequest).toHaveBeenCalledOnce()
    expect(authMocks.login).toHaveBeenCalledOnce()
  })

  it('starts login once for an unauthorized no-content request', async () => {
    authMocks.httpRequest.mockRejectedValue(sdkFailure(401, {}))

    await expect(apiRequestNoContent('/consents/1')).rejects.toMatchObject({ status: 401 })

    expect(authMocks.httpRequest).toHaveBeenCalledOnce()
    expect(authMocks.login).toHaveBeenCalledOnce()
  })

  it('does not start login for a 401 when authentication is disabled', async () => {
    authMocks.isAuthEnabled.mockReturnValue(false)
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(new Response(JSON.stringify({}), { status: 401 })),
    )

    await expect(apiRequest('/consents')).rejects.toMatchObject({ status: 401 })

    expect(authMocks.login).not.toHaveBeenCalled()
    expect(authMocks.httpRequest).not.toHaveBeenCalled()
  })

  it('accepts an empty body for a no-content request', async () => {
    authMocks.httpRequest.mockResolvedValue(sdkResponse(undefined, 204))

    await expect(apiRequestNoContent('/consents/1', { method: 'DELETE' })).resolves.toBeUndefined()
    expect(requestConfig().method).toBe('DELETE')
  })

  it('reports an empty body as undefined for optional-content requests', async () => {
    authMocks.httpRequest.mockResolvedValue(sdkResponse('', 200))

    await expect(
      apiRequestOptionalContent('/consents/1/revoke', { method: 'POST' }),
    ).resolves.toBeUndefined()
  })

  it('uses fallback API errors for responses without an error body', async () => {
    authMocks.httpRequest.mockRejectedValue(sdkFailure(502, 'failure'))

    const failure = apiRequest('/consents')
    await expect(failure).rejects.toBeInstanceOf(APIError)
    await expect(failure).rejects.toMatchObject({
      status: 502,
      code: 'API_REQUEST_FAILED',
      message: 'request failed with status 502',
    })
  })

  it('reports a transport failure with no HTTP response as unavailable', async () => {
    authMocks.httpRequest.mockRejectedValue(new Error('network down'))

    await expect(apiRequest('/consents')).rejects.toMatchObject({
      status: 502,
      code: 'API_REQUEST_FAILED',
      message: 'the consent service is unavailable',
    })
  })

  it('treats an empty API base URL as same-origin', async () => {
    vi.stubEnv('VITE_API_BASE_URL', '')
    authMocks.httpRequest.mockResolvedValue(sdkResponse({}))

    await apiRequest('/consents')

    expect(requestConfig().url).toBe(`${window.location.origin}/consents`)
  })

  it('rejects absolute paths before sending a request', async () => {
    await expect(apiRequest('https://example.com/consents')).rejects.toThrow(
      'apiClient path must be relative',
    )
    expect(authMocks.httpRequest).not.toHaveBeenCalled()
  })

  it('rejects a successful response that carries no body', async () => {
    authMocks.httpRequest.mockResolvedValue(sdkResponse(undefined, 204))

    await expect(apiRequest('/empty')).rejects.toThrow('use apiRequestNoContent')
  })
})
