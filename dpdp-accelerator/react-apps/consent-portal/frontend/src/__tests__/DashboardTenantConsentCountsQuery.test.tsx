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
import { cleanup, renderHook, waitFor } from '@testing-library/react'
import type { PropsWithChildren } from 'react'
import { afterEach, describe, expect, it, vi } from 'vitest'
import useDashboardTenantConsentCountsQuery from '../features/dashboard/hooks/useDashboardTenantConsentCountsQuery'

const adminConsentsApi = vi.hoisted(() => ({ fetchAdminConsents: vi.fn() }))

vi.mock('../features/admin-consents/api/adminConsentsApi', () => adminConsentsApi)

afterEach(() => {
  cleanup()
  vi.clearAllMocks()
})

function renderCounts() {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  const wrapper = ({ children }: PropsWithChildren): React.JSX.Element => (
    <QueryClientProvider client={queryClient}>{children}</QueryClientProvider>
  )
  return renderHook(() => useDashboardTenantConsentCountsQuery(true), { wrapper })
}

function page(count: number, next?: string) {
  return {
    totalResults: count,
    links: next ? [{ rel: 'next', href: `https://x?after=${next}` }] : [],
    Consents: Array.from({ length: count }, (_, index) => ({
      id: `c-${String(index)}`,
      subjectId: 'user-1',
      serviceId: 'svc',
      state: 'ACTIVE',
      timestamp: index,
    })),
  }
}

describe('useDashboardTenantConsentCountsQuery', () => {
  it('sums an exact count across multiple pages, not just the first page', async () => {
    adminConsentsApi.fetchAdminConsents.mockImplementation(
      (params: { state?: string; after?: string }): Promise<unknown> => {
        if (params.state === 'ACTIVE') {
          return Promise.resolve(params.after ? page(3) : page(200, '200'))
        }
        return Promise.resolve(page(0))
      },
    )

    const { result } = renderCounts()

    await waitFor(() =>
      expect(result.current.data?.active).toEqual({ count: 203, isAtLeast: false }),
    )
    expect(result.current.data?.pending).toEqual({ count: 0, isAtLeast: false })
  })

  it('reports isAtLeast once the safety ceiling is hit, instead of looping forever', async () => {
    adminConsentsApi.fetchAdminConsents.mockImplementation(
      (params: { state?: string; after?: string }): Promise<unknown> => {
        // Every page for PENDING claims there is a next page, so only the safety ceiling
        // (COUNT_MAX_PAGES) stops this - a real bug here would hang the dashboard.
        if (params.state === 'PENDING') return Promise.resolve(page(200, 'more'))
        return Promise.resolve(page(0))
      },
    )

    const { result } = renderCounts()

    await waitFor(() => expect(result.current.data?.pending.isAtLeast).toBe(true))
    expect(result.current.data?.pending.count).toBe(200 * 100)
  })
})
