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

import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { act, cleanup, renderHook } from '@testing-library/react'
import type { PropsWithChildren } from 'react'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { WEBHOOK_VERIFICATION_MONITOR_POLICY } from '../features/events/constants'
import {
  normalizeSubscriptionStatus,
  useWebhookVerificationMonitor,
} from '../features/events/hooks/useWebhookVerificationMonitor'

const subscriptionsApi = vi.hoisted(() => ({
  fetchSubscriptionById: vi.fn(),
}))

vi.mock('../features/events/api/subscriptionsApi', async (importOriginal) => ({
  ...(await importOriginal<typeof import('../features/events/api/subscriptionsApi')>()),
  fetchSubscriptionById: subscriptionsApi.fetchSubscriptionById,
}))

afterEach(() => {
  cleanup()
  vi.clearAllMocks()
  vi.useRealTimers()
})

describe('webhook verification monitor', () => {
  it('polls while pending and stops after the backend reports stale', async () => {
    vi.useFakeTimers()
    subscriptionsApi.fetchSubscriptionById
      .mockResolvedValueOnce({ subscriptionId: 'sub-1', status: 'PENDING' })
      .mockResolvedValueOnce({ subscriptionId: 'sub-1', status: 'STALE' })

    const queryClient = new QueryClient({
      defaultOptions: { queries: { retry: false } },
    })
    const wrapper = ({ children }: PropsWithChildren): React.JSX.Element => (
      <QueryClientProvider client={queryClient}>{children}</QueryClientProvider>
    )

    const { result } = renderHook(() => useWebhookVerificationMonitor('sub-1'), { wrapper })

    await vi.waitFor(() => expect(result.current.data?.status).toBe('PENDING'))
    await act(async () => {
      await vi.advanceTimersByTimeAsync(WEBHOOK_VERIFICATION_MONITOR_POLICY.pollIntervalMs)
    })
    await vi.waitFor(() => expect(result.current.data?.status).toBe('STALE'))

    await act(async () => {
      await vi.advanceTimersByTimeAsync(WEBHOOK_VERIFICATION_MONITOR_POLICY.pollIntervalMs * 2)
    })
    expect(subscriptionsApi.fetchSubscriptionById).toHaveBeenCalledTimes(2)
  })

  it('normalizes terminal statuses returned by the API', () => {
    expect(normalizeSubscriptionStatus(' stale ')).toBe('STALE')
  })

  it('owns the observation timeout and stops polling when it expires', async () => {
    vi.useFakeTimers()
    subscriptionsApi.fetchSubscriptionById.mockResolvedValue({
      subscriptionId: 'sub-1',
      status: 'PENDING',
    })

    const queryClient = new QueryClient({
      defaultOptions: { queries: { retry: false } },
    })
    const wrapper = ({ children }: PropsWithChildren): React.JSX.Element => (
      <QueryClientProvider client={queryClient}>{children}</QueryClientProvider>
    )

    const { result } = renderHook(() => useWebhookVerificationMonitor('sub-1'), { wrapper })

    await vi.waitFor(() => expect(result.current.data?.status).toBe('PENDING'))
    await act(async () => {
      await vi.advanceTimersByTimeAsync(WEBHOOK_VERIFICATION_MONITOR_POLICY.observationTimeoutMs)
    })
    await vi.waitFor(() => expect(result.current.timedOut).toBe(true))

    const callsAtTimeout = subscriptionsApi.fetchSubscriptionById.mock.calls.length
    await act(async () => {
      await vi.advanceTimersByTimeAsync(WEBHOOK_VERIFICATION_MONITOR_POLICY.pollIntervalMs * 2)
    })
    expect(subscriptionsApi.fetchSubscriptionById).toHaveBeenCalledTimes(callsAtTimeout)
  })
})
