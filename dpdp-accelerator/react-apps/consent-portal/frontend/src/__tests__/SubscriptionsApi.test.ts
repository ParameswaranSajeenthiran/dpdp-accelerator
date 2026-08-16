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
  createSubscription,
  deleteSubscription,
  fetchSubscriptionById,
  fetchSubscriptionEventHistory,
  fetchSubscriptionEvents,
  fetchSubscriptions,
  verifySubscription,
} from '../features/events/api/subscriptionsApi'

const fetchMock = vi.fn()

afterEach(() => {
  fetchMock.mockReset()
  vi.unstubAllGlobals()
})

function mockJSONResponse(payload: unknown = {}): void {
  vi.stubGlobal('fetch', fetchMock)
  fetchMock.mockResolvedValue({
    ok: true,
    status: 200,
    json: async () => payload,
  })
}

describe('subscriptionsApi', () => {
  it('calls fetchSubscriptions with search and pagination query parameters', async () => {
    mockJSONResponse({ items: [], total: 0 })

    await fetchSubscriptions({
      limit: 10,
      offset: 20,
      status: 'active',
      search: 'consent.revoke',
    })

    const [requestUrl, requestInit] = fetchMock.mock.calls[0] ?? []
    const url = new URL(String(requestUrl))
    expect(url.pathname).toBe('/api/event-notifications/subscriptions')
    expect(Object.fromEntries(url.searchParams)).toEqual({
      limit: '10',
      offset: '20',
      status: 'active',
      search: 'consent.revoke',
    })
    expect(requestInit).toMatchObject({ method: 'GET', credentials: 'include' })
  })

  it('fetches a single subscription by ID with URI encoding', async () => {
    mockJSONResponse({ subscriptionId: 'sub/123' })

    await fetchSubscriptionById('sub/123')

    const [requestUrl] = fetchMock.mock.calls[0] ?? []
    const url = new URL(String(requestUrl))
    expect(url.pathname).toBe('/api/event-notifications/subscriptions/sub%2F123')
  })

  it('posts a new subscription payload', async () => {
    mockJSONResponse({ subscriptionId: 'sub-new', status: 'ACTIVE' })

    const payload = {
      topic: 'consent.revoke',
      groupId: 'group-1',
      filter: { type: 'specific' as const, purposes: ['MARKETING'] },
      delivery: {
        mode: 'webhook' as const,
        callbackUrl: 'https://example.com/callback',
        sharedSecret: 'secret123',
      },
    }

    await createSubscription(payload)

    const [requestUrl, requestInit] = fetchMock.mock.calls[0] ?? []
    const url = new URL(String(requestUrl))
    expect(url.pathname).toBe('/api/event-notifications/subscriptions')
    expect(requestInit).toMatchObject({
      method: 'POST',
      credentials: 'include',
      body: JSON.stringify(payload),
    })
  })

  it('deletes a subscription by ID', async () => {
    mockJSONResponse({ subscriptionId: 'sub-1', status: 'DELETED' })

    await deleteSubscription('sub-1')

    const [requestUrl, requestInit] = fetchMock.mock.calls[0] ?? []
    const url = new URL(String(requestUrl))
    expect(url.pathname).toBe('/api/event-notifications/subscriptions/sub-1')
    expect(requestInit).toMatchObject({ method: 'DELETE', credentials: 'include' })
  })

  it('triggers verification on a subscription', async () => {
    mockJSONResponse({ subscriptionId: 'sub-1', status: 'ACTIVE' })

    await verifySubscription('sub-1')

    const [requestUrl, requestInit] = fetchMock.mock.calls[0] ?? []
    const url = new URL(String(requestUrl))
    expect(url.pathname).toBe('/api/event-notifications/subscriptions/sub-1/verify')
    expect(requestInit).toMatchObject({ method: 'POST', credentials: 'include' })
  })

  it('fetches subscription delivery events with pagination', async () => {
    mockJSONResponse({ items: [], total: 0 })

    await fetchSubscriptionEvents('sub-1', { limit: 5, offset: 10 })

    const [requestUrl] = fetchMock.mock.calls[0] ?? []
    const url = new URL(String(requestUrl))
    expect(url.pathname).toBe('/api/event-notifications/subscriptions/sub-1/events')
    expect(Object.fromEntries(url.searchParams)).toEqual({
      limit: '5',
      offset: '10',
    })
  })

  it('fetches detailed delivery attempt history', async () => {
    mockJSONResponse({ deliveryId: 'dlv-1', history: [] })

    await fetchSubscriptionEventHistory('sub-1', 'dlv-1')

    const [requestUrl] = fetchMock.mock.calls[0] ?? []
    const url = new URL(String(requestUrl))
    expect(url.pathname).toBe('/api/event-notifications/subscriptions/sub-1/events/dlv-1')
  })
})
