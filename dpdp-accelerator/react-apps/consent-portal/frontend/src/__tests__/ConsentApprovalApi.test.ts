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
  approveMyConsent,
  fetchMyConsents,
  rejectMyConsent,
  revokeMyConsent,
} from '../features/my-consents/api/myConsentsApi'
import { APIError } from '../utils/apiClient'

const transport = vi.hoisted(() => ({
  httpRequest: vi.fn(),
  login: vi.fn(),
}))

vi.mock('../utils/authClient', () => ({
  httpRequest: transport.httpRequest,
  isAuthEnabled: () => true,
  login: transport.login,
}))

const SELF_CONSENTS = '/api/users/v1/me/consents'

interface SentRequest {
  url: string
  method?: string
  data?: unknown
}

afterEach(() => {
  vi.clearAllMocks()
})

function sentRequests(): SentRequest[] {
  return transport.httpRequest.mock.calls.map(([config]) => config as SentRequest)
}

function pathOf(request: SentRequest): string {
  return new URL(request.url).pathname
}

function summary(id: string, state = 'ACTIVE'): Record<string, unknown> {
  return {
    id,
    subjectId: 'admin',
    serviceId: 'dpdp-portal',
    state,
    timestamp: 1785833979893,
    purposes: [{ id: 'purpose-1', name: 'marketing-spike', version: '1.0.0', elements: [] }],
    authorizations: [],
  }
}

function detail(id: string, state = 'ACTIVE'): Record<string, unknown> {
  return summary(id, state)
}

describe('self-service consent API', () => {
  it('requests attributes and relation, over-fetches a page and slices it locally', async () => {
    const summaries = ['c-0', 'c-1', 'c-2', 'c-3', 'c-4'].map((id) => summary(id))
    transport.httpRequest.mockResolvedValue({ status: 200, data: summaries })

    const page = await fetchMyConsents({
      limit: 2,
      offset: 1,
      state: 'PENDING',
      serviceId: 'dpdp-portal',
      relation: 'ANY',
    })

    const [list] = sentRequests()
    const url = new URL(list.url)
    expect(url.pathname).toBe(SELF_CONSENTS)
    expect(Object.fromEntries(url.searchParams)).toEqual({
      // offset + limit + 1: enough rows to fill the page and see if more exist.
      limit: '4',
      // The upstream filter takes a single state, so only the first is sent.
      state: 'PENDING',
      serviceId: 'dpdp-portal',
      relation: 'ANY',
      attributes: 'purposes,authorizations',
    })

    // No per-row detail lookup - attributes already inlined what the table needs.
    expect(sentRequests()).toHaveLength(1)
    expect(page.data.map((consent) => consent.id)).toEqual(['c-1', 'c-2'])
    expect(page.data[0].purposes?.[0]?.name).toBe('marketing-spike')
    expect(page.metadata).toEqual({ total: summaries.length, offset: 1, count: 2, limit: 2 })
  })

  it('omits the state, relation and filter params when unset', async () => {
    transport.httpRequest.mockResolvedValue({ status: 200, data: [] })

    await fetchMyConsents({ limit: 10, offset: 0 })

    expect(Object.fromEntries(new URL(sentRequests()[0].url).searchParams)).toEqual({
      limit: '11',
      attributes: 'purposes,authorizations',
    })
  })

  it('passes a timestamp filter clause through unchanged', async () => {
    transport.httpRequest.mockResolvedValue({ status: 200, data: [] })

    await fetchMyConsents({ limit: 10, offset: 0, filter: 'timestamp ge 1700000000000' })

    expect(Object.fromEntries(new URL(sentRequests()[0].url).searchParams)).toEqual({
      limit: '11',
      filter: 'timestamp ge 1700000000000',
      attributes: 'purposes,authorizations',
    })
  })

  it('approves a pending consent through the authorize endpoint', async () => {
    transport.httpRequest
      .mockResolvedValueOnce({ status: 200, data: detail('consent/123?draft', 'PENDING') })
      .mockResolvedValueOnce({ status: 204, data: undefined })

    await expect(approveMyConsent('consent/123?draft')).resolves.toEqual({ status: 'OK' })

    const [read, authorize] = sentRequests()
    expect(pathOf(read)).toBe(`${SELF_CONSENTS}/consent%2F123%3Fdraft`)
    expect(pathOf(authorize)).toBe(`${SELF_CONSENTS}/consent%2F123%3Fdraft/authorize`)
    expect(authorize.method).toBe('POST')
    expect(JSON.parse(String(authorize.data))).toEqual({ state: 'APPROVED' })
  })

  it('rejects a pending consent through the same endpoint', async () => {
    transport.httpRequest
      .mockResolvedValueOnce({ status: 200, data: detail('c-1', 'PENDING') })
      .mockResolvedValueOnce({ status: 204, data: undefined })

    await expect(rejectMyConsent('c-1')).resolves.toEqual({ status: 'OK' })

    const [, authorize] = sentRequests()
    expect(pathOf(authorize)).toBe(`${SELF_CONSENTS}/c-1/authorize`)
    expect(JSON.parse(String(authorize.data))).toEqual({ state: 'REJECTED' })
  })

  it.each(['REVOKED', 'EXPIRED'])(
    'refuses to approve a %s consent - a withdrawal or lapse stays final',
    async (state) => {
      transport.httpRequest.mockResolvedValue({ status: 200, data: detail('db1f6e7a', state) })

      // The server itself would allow this; the client keeps it final regardless.
      const failure = approveMyConsent('db1f6e7a')
      await expect(failure).rejects.toBeInstanceOf(APIError)
      await expect(failure).rejects.toMatchObject({
        code: 'INVALID_CONSENT_STATE',
        status: 409,
        message: `This consent cannot be approved right now; it is ${state}.`,
      })
      expect(sentRequests().every((request) => request.method === 'GET')).toBe(true)
    },
  )

  it('refuses to reject an already-rejected consent', async () => {
    transport.httpRequest.mockResolvedValue({ status: 200, data: detail('db1f6e7a', 'REJECTED') })

    const failure = rejectMyConsent('db1f6e7a')
    await expect(failure).rejects.toMatchObject({
      code: 'INVALID_CONSENT_STATE',
      status: 409,
      message: 'This consent cannot be rejected right now; it is REJECTED.',
    })
    expect(sentRequests().every((request) => request.method === 'GET')).toBe(true)
  })

  it('allows approving a previously rejected consent - a rejection is reconsiderable', async () => {
    transport.httpRequest
      .mockResolvedValueOnce({ status: 200, data: detail('c-1', 'REJECTED') })
      .mockResolvedValueOnce({ status: 204, data: undefined })

    await expect(approveMyConsent('c-1')).resolves.toEqual({ status: 'OK' })

    const [, authorize] = sentRequests()
    expect(JSON.parse(String(authorize.data))).toEqual({ state: 'APPROVED' })
  })

  it("allows rejecting an active consent - withdraws just the caller's own approval", async () => {
    transport.httpRequest
      .mockResolvedValueOnce({ status: 200, data: detail('c-1', 'ACTIVE') })
      .mockResolvedValueOnce({ status: 204, data: undefined })

    await expect(rejectMyConsent('c-1')).resolves.toEqual({ status: 'OK' })

    const [, authorize] = sentRequests()
    expect(JSON.parse(String(authorize.data))).toEqual({ state: 'REJECTED' })
  })

  it("gates on the given authorizer's own decision, not the aggregate state", async () => {
    transport.httpRequest.mockResolvedValue({
      status: 200,
      data: {
        ...detail('c-1', 'PENDING'),
        authorizations: [{ userId: 'bob', state: 'APPROVED', updatedTime: 1 }],
      },
    })

    // The consent is still PENDING overall (waiting on other authorizers),
    // but bob already approved, so bob cannot approve again.
    const failure = approveMyConsent('c-1', 'bob')
    await expect(failure).rejects.toMatchObject({ code: 'INVALID_CONSENT_STATE' })
  })

  it('revokes consent with POST and no request body', async () => {
    transport.httpRequest.mockResolvedValue({ status: 204, data: undefined })

    await expect(revokeMyConsent('consent-123')).resolves.toEqual({ status: 'OK' })

    const [revoke] = sentRequests()
    expect(pathOf(revoke)).toBe(`${SELF_CONSENTS}/consent-123/revoke`)
    expect(revoke.method).toBe('POST')
    expect(revoke.data).toBeUndefined()
  })
})
