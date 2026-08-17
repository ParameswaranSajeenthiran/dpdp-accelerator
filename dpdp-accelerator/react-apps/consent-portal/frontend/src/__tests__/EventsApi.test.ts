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
  fetchEventById,
  fetchEventDeliveries,
  fetchEventDeliveryHistory,
  fetchEvents,
  publishEvent,
} from '../features/events/api/eventsApi'

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

describe('eventsApi', () => {
  it('calls fetchEvents with search, status, topic, groupId, and pagination query parameters', async () => {
    mockJSONResponse({ items: [], total: 0 })

    await fetchEvents({
      limit: 10,
      offset: 20,
      search: 'consent.revoke',
      status: 'DELIVERED',
      topic: 'consent-events',
      groupId: 'consumer-grp-1',
    })

    const [requestUrl, requestInit] = fetchMock.mock.calls[0] ?? []
    const url = new URL(String(requestUrl))
    expect(url.pathname).toBe('/api/event-notifications/events')
    expect(Object.fromEntries(url.searchParams)).toEqual({
      limit: '10',
      offset: '20',
      search: 'consent.revoke',
      status: 'DELIVERED',
      topic: 'consent-events',
      groupId: 'consumer-grp-1',
    })
    expect(requestInit).toMatchObject({ method: 'GET', credentials: 'include' })
  })

  it('fetches event delivery history by deliveryId', async () => {
    mockJSONResponse({
      deliveryId: 'dlv-123',
      eventId: 'evt-1',
      topic: 'consent-events',
      currentStatus: 'DELIVERED',
      deliveryMode: 'webhook',
      history: [],
    })

    const result = await fetchEventDeliveryHistory('dlv-123')

    const [requestUrl, requestInit] = fetchMock.mock.calls[0] ?? []
    const url = new URL(String(requestUrl))
    expect(url.pathname).toBe('/api/event-notifications/events/dlv-123/history')
    expect(requestInit).toMatchObject({ method: 'GET', credentials: 'include' })
    expect(result.deliveryId).toBe('dlv-123')
  })

  it('posts a new event payload for publishing', async () => {
    mockJSONResponse({ eventId: 'evt-1', topicId: 'topic-1' })

    const payload = {
      topicName: 'consent.revoke',
      groupId: 'group-1',
      purposes: ['MARKETING'],
      payload: { consentId: 'c1', status: 'REVOKED' },
    }

    await publishEvent(payload)

    const [requestUrl, requestInit] = fetchMock.mock.calls[0] ?? []
    const url = new URL(String(requestUrl))
    expect(url.pathname).toBe('/api/event-notifications/events')
    expect(requestInit).toMatchObject({
      method: 'POST',
      credentials: 'include',
      body: JSON.stringify(payload),
    })
  })

  it('fetches event details by eventId', async () => {
    mockJSONResponse({
      eventId: 'evt-101',
      topic: 'consent.revoke',
      payload: '{"key":"val"}',
      purposes: ['MARKETING'],
      occurredAt: 1710000000000,
    })

    const result = await fetchEventById('evt-101')

    const [requestUrl, requestInit] = fetchMock.mock.calls[0] ?? []
    const url = new URL(String(requestUrl))
    expect(url.pathname).toBe('/api/event-notifications/events/evt-101')
    expect(requestInit).toMatchObject({ method: 'GET', credentials: 'include' })
    expect(result.eventId).toBe('evt-101')
  })

  it('fetches event downstream deliveries by eventId', async () => {
    mockJSONResponse({
      items: [
        {
          deliveryId: 'dlv-1',
          eventId: 'evt-101',
          subscriptionId: 'sub-1',
          currentStatus: 'DELIVERED',
        },
      ],
      total: 1,
    })

    const result = await fetchEventDeliveries('evt-101', 10, 0)

    const [requestUrl, requestInit] = fetchMock.mock.calls[0] ?? []
    const url = new URL(String(requestUrl))
    expect(url.pathname).toBe('/api/event-notifications/events/evt-101/deliveries')
    expect(Object.fromEntries(url.searchParams)).toEqual({
      limit: '10',
      offset: '0',
    })
    expect(requestInit).toMatchObject({ method: 'GET', credentials: 'include' })
    expect(result.items).toHaveLength(1)
  })
})
