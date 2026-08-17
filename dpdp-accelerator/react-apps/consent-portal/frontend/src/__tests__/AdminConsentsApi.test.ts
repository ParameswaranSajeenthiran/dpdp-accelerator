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

import { afterEach, describe, expect, it, vi } from 'vitest'
import {
  fetchAdminConsentByID,
  fetchAdminConsents,
  revokeAdminConsent,
} from '../features/admin-consents/api/adminConsentsApi'
import { getNextCursor, getPreviousCursor } from '../utils/cursorPagination'

const transport = vi.hoisted(() => ({
  httpRequest: vi.fn(),
  login: vi.fn(),
}))

vi.mock('../utils/authClient', () => ({
  httpRequest: transport.httpRequest,
  isAuthEnabled: () => true,
  login: transport.login,
}))

const CONSENT_MGT_V2 = '/api/identity/consent-mgt/v2.0'

afterEach(() => {
  vi.clearAllMocks()
})

function respondWith(payload: unknown, status = 200): void {
  transport.httpRequest.mockResolvedValue({ status, data: payload })
}

/** The request config the api handed to the auth SDK. */
function sentRequest(index = 0): { url: string; method?: string; data?: unknown } {
  return transport.httpRequest.mock.calls[index]?.[0] as ReturnType<typeof sentRequest>
}

describe('administrative consent API', () => {
  it('sends the supported cursor and filter parameters to the consent management API', async () => {
    respondWith({ totalResults: 0, links: [], Consents: [] })

    await fetchAdminConsents({
      limit: 25,
      after: 'Mg==',
      subjectId: 'admin',
      serviceId: 'dpdp-portal',
      state: 'ACTIVE',
    })

    const url = new URL(sentRequest().url)
    expect(url.pathname).toBe(`${CONSENT_MGT_V2}/consents`)
    expect(Object.fromEntries(url.searchParams)).toEqual({
      limit: '25',
      after: 'Mg==',
      subjectId: 'admin',
      serviceId: 'dpdp-portal',
      state: 'ACTIVE',
    })
    expect(sentRequest().method).toBe('GET')
  })

  it('sends a before cursor when paging backwards and omits unset filters', async () => {
    respondWith({ totalResults: 0, links: [], Consents: [] })

    await fetchAdminConsents({ limit: 10, before: 'MQ==' })

    expect(Object.fromEntries(new URL(sentRequest().url).searchParams)).toEqual({
      limit: '10',
      before: 'MQ==',
    })
  })

  it('reads next and previous cursors out of the returned links', () => {
    const links = [
      {
        rel: 'next',
        href: 'https://localhost:9443/api/identity/consent-mgt/v2.0/consents?limit=2&after=Mg==',
      },
      {
        rel: 'previous',
        href: 'https://localhost:9443/api/identity/consent-mgt/v2.0/consents?limit=2&before=MA==',
      },
    ]

    expect(getNextCursor(links)).toBe('Mg==')
    expect(getPreviousCursor(links)).toBe('MA==')
    expect(getNextCursor([])).toBeUndefined()
    expect(getPreviousCursor(undefined)).toBeUndefined()
  })

  it('loads encoded consent details without extra query parameters', async () => {
    respondWith({ id: 'consent/123' })

    await fetchAdminConsentByID('consent/123')

    const url = new URL(sentRequest().url)
    expect(url.pathname).toBe(`${CONSENT_MGT_V2}/consents/consent%2F123`)
    expect(Object.fromEntries(url.searchParams)).toEqual({})
  })

  it('revokes with no request body and reports success', async () => {
    // The Identity Server answers revoke with 204 and no body.
    respondWith(undefined, 204)

    await expect(revokeAdminConsent('consent-123')).resolves.toEqual({ status: 'OK' })

    const request = sentRequest()
    expect(new URL(request.url).pathname).toBe(`${CONSENT_MGT_V2}/consents/consent-123/revoke`)
    expect(request.method).toBe('POST')
    expect(request.data).toBeUndefined()
  })
})
