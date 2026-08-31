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
  fetchAdminConsentFullHistory,
  fetchAdminConsentStatusHistory,
} from '../features/admin-consents/api/consentHistoryApi'
import {
  fetchMyConsentFullHistory,
  fetchMyConsentStatusHistory,
} from '../features/my-consents/api/consentHistoryApi'

const transport = vi.hoisted(() => ({
  httpRequest: vi.fn(),
  login: vi.fn(),
}))

vi.mock('../utils/authClient', () => ({
  httpRequest: transport.httpRequest,
  isAuthEnabled: () => true,
  login: transport.login,
}))

const CONSENT_HISTORY_API = '/api/dpdp/consent-mgt/v1'

afterEach(() => {
  vi.clearAllMocks()
})

function respondWith(payload: unknown, status = 200): void {
  transport.httpRequest.mockResolvedValue({ status, data: payload })
}

function sentRequest(index = 0): { url: string; method?: string } {
  return transport.httpRequest.mock.calls[index]?.[0] as ReturnType<typeof sentRequest>
}

describe('consent history API', () => {
  it('fetches the self status-history endpoint with limit and offset', async () => {
    respondWith({
      consentId: 'c1',
      statusHistory: [],
      pagination: { limit: 100, offset: 0, totalCount: 0 },
    })

    await fetchMyConsentStatusHistory('c1', { limit: 100, offset: 0 })

    const url = new URL(sentRequest().url)
    expect(url.pathname).toBe(`${CONSENT_HISTORY_API}/me/consents/c1/status-history`)
    expect(Object.fromEntries(url.searchParams)).toEqual({ limit: '100', offset: '0' })
    expect(sentRequest().method).toBe('GET')
  })

  it('fetches the self full-history endpoint with limit and offset', async () => {
    respondWith({
      consentId: 'c1',
      history: [],
      pagination: { limit: 100, offset: 0, totalCount: 0 },
    })

    await fetchMyConsentFullHistory('c1', { limit: 100, offset: 0 })

    const url = new URL(sentRequest().url)
    expect(url.pathname).toBe(`${CONSENT_HISTORY_API}/me/consents/c1/history`)
    expect(Object.fromEntries(url.searchParams)).toEqual({ limit: '100', offset: '0' })
  })

  it('fetches the admin status-history endpoint under /consents, not /me/consents', async () => {
    respondWith({
      consentId: 'c1',
      statusHistory: [],
      pagination: { limit: 100, offset: 0, totalCount: 0 },
    })

    await fetchAdminConsentStatusHistory('c1', { limit: 100, offset: 0 })

    expect(new URL(sentRequest().url).pathname).toBe(
      `${CONSENT_HISTORY_API}/consents/c1/status-history`,
    )
  })

  it('fetches the admin full-history endpoint under /consents, not /me/consents', async () => {
    respondWith({
      consentId: 'c1',
      history: [],
      pagination: { limit: 100, offset: 0, totalCount: 0 },
    })

    await fetchAdminConsentFullHistory('c1', { limit: 100, offset: 0 })

    expect(new URL(sentRequest().url).pathname).toBe(`${CONSENT_HISTORY_API}/consents/c1/history`)
  })

  it('URL-encodes the consent ID', async () => {
    respondWith({
      consentId: 'c/1',
      statusHistory: [],
      pagination: { limit: 100, offset: 0, totalCount: 0 },
    })

    await fetchMyConsentStatusHistory('c/1', { limit: 100, offset: 0 })

    expect(new URL(sentRequest().url).pathname).toBe(
      `${CONSENT_HISTORY_API}/me/consents/c%2F1/status-history`,
    )
  })
})
