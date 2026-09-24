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
import { AcrylicOrangeTheme, CssBaseline, OxygenUIThemeProvider } from '@wso2/oxygen-ui'
import { cleanup, render, screen, waitFor } from '@testing-library/react'
import { I18nextProvider } from 'react-i18next'
import { MemoryRouter } from 'react-router-dom'
import { afterEach, describe, expect, it, vi } from 'vitest'
import DashboardPage from '../features/dashboard/DashboardPage'
import i18n from '../i18n/i18n'
import { REQUIRED_SCOPES, type ScopeRequirement } from '../utils/scopes'
import TestAuthorizationProvider from './TestAuthorizationProvider'

const myConsentsApi = vi.hoisted(() => ({
  fetchMyConsentsRaw: vi.fn(),
  fetchMyConsents: vi.fn(),
}))
const adminConsentsApi = vi.hoisted(() => ({ fetchAdminConsents: vi.fn() }))
const catalogApi = vi.hoisted(() => ({ fetchPurposes: vi.fn(), fetchElements: vi.fn() }))
const complaintsApi = vi.hoisted(() => ({ fetchMyComplaintsTotal: vi.fn() }))

vi.mock('../features/my-consents/api/myConsentsApi', () => myConsentsApi)
vi.mock('../features/admin-consents/api/adminConsentsApi', () => adminConsentsApi)
vi.mock('../features/catalog/api/catalogApi', () => catalogApi)
vi.mock('../features/complaints/api/complaintsApi', () => complaintsApi)

afterEach(() => {
  cleanup()
  vi.clearAllMocks()
})

function renderDashboard(scopes: ScopeRequirement[]): void {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } })

  render(
    <OxygenUIThemeProvider theme={AcrylicOrangeTheme}>
      <CssBaseline />
      <I18nextProvider i18n={i18n}>
        <QueryClientProvider client={queryClient}>
          <MemoryRouter initialEntries={['/dashboard']}>
            <TestAuthorizationProvider scopes={scopes}>
              <DashboardPage />
            </TestAuthorizationProvider>
          </MemoryRouter>
        </QueryClientProvider>
      </I18nextProvider>
    </OxygenUIThemeProvider>,
  )
}

function rawConsents(count: number): unknown[] {
  return Array.from({ length: count }, (_, index) => ({
    id: `c-${String(index)}`,
    subjectId: 'user-1',
    serviceId: 'svc',
    state: 'ACTIVE',
    timestamp: index,
  }))
}

describe('DashboardPage', () => {
  it('shows a regular user their own consent and complaint counts', async () => {
    // Total (101, over the 100 cap - "100+"), Pending (3, exact); every other count uses its
    // own distinct number so assertions can't accidentally match the wrong tile.
    myConsentsApi.fetchMyConsentsRaw.mockImplementation(
      (params: { state?: string }): Promise<unknown[]> => {
        if (params.state === 'PENDING') return Promise.resolve(rawConsents(3))
        if (params.state === 'ACTIVE') return Promise.resolve(rawConsents(35))
        if (params.state === 'REJECTED') return Promise.resolve(rawConsents(36))
        if (params.state === 'REVOKED') return Promise.resolve(rawConsents(37))
        if (params.state === 'EXPIRED') return Promise.resolve(rawConsents(38))
        return Promise.resolve(rawConsents(101)) // total, unfiltered
      },
    )
    myConsentsApi.fetchMyConsents.mockResolvedValue({
      data: [{ id: 'p1', subjectId: 'user-1', serviceId: 'svc', state: 'PENDING', timestamp: 1 }],
      metadata: { total: 1, offset: 0, count: 1, limit: 100 },
    })
    complaintsApi.fetchMyComplaintsTotal.mockImplementation((status?: string) =>
      Promise.resolve(
        {
          undefined: 60,
          OPEN: 61,
          IN_PROGRESS: 62,
          WAITING_ON_CLIENT: 63,
          AWAITING_INTERNAL_REVIEW: 64,
          RESOLVED: 65,
        }[String(status)] ?? 0,
      ),
    )

    renderDashboard([REQUIRED_SCOPES.CONSENTS_READ_SELF, REQUIRED_SCOPES.COMPLAINTS_READ_SELF])

    expect(screen.getByText('Consents by status')).toBeInTheDocument()
    expect(await screen.findByText('100+')).toBeInTheDocument() // total
    // The pending count appears twice: the status tile and the "Needs your attention" badge.
    await waitFor(() => {
      expect(screen.getAllByText('3')).toHaveLength(2)
    })
    expect(screen.getByText('Needs your attention')).toBeInTheDocument()
    expect(screen.getByText('Complaints')).toBeInTheDocument()
    // Without relation=ANY the server returns only consents the user is the subject of, hiding
    // the ones awaiting their decision as an authorizer (wso2/dpdp-accelerator#274).
    expect(myConsentsApi.fetchMyConsents).toHaveBeenCalledWith(
      expect.objectContaining({ state: 'PENDING', relation: 'ANY' }),
    )
    expect(myConsentsApi.fetchMyConsentsRaw).toHaveBeenCalledTimes(6)
    myConsentsApi.fetchMyConsentsRaw.mock.calls.forEach(([params]) => {
      expect(params).toMatchObject({ relation: 'ANY' })
    })
    expect(adminConsentsApi.fetchAdminConsents).not.toHaveBeenCalled()
    expect(catalogApi.fetchPurposes).not.toHaveBeenCalled()
    expect(catalogApi.fetchElements).not.toHaveBeenCalled()
  })

  it('shows an admin exact tenant-wide consent, purposes, and elements counts', async () => {
    adminConsentsApi.fetchAdminConsents.mockImplementation(
      (params: { state?: string }): Promise<unknown> => {
        if (params.state === undefined) {
          // Total: a full page with a next link still available - "100+".
          return Promise.resolve({
            totalResults: 100,
            links: [{ rel: 'next', href: 'https://x?after=Mg==' }],
            Consents: rawConsents(100),
          })
        }
        const counts: Record<string, number> = {
          ACTIVE: 29,
          PENDING: 6,
          REJECTED: 0,
          REVOKED: 0,
          EXPIRED: 0,
        }
        const count = counts[params.state] ?? 0
        return Promise.resolve({ totalResults: count, links: [], Consents: rawConsents(count) })
      },
    )
    catalogApi.fetchPurposes.mockResolvedValue({
      totalResults: 8,
      links: [],
      Purposes: Array.from({ length: 8 }, (_, index) => ({ id: `p-${String(index)}` })),
    })
    catalogApi.fetchElements.mockResolvedValue({
      totalResults: 12,
      links: [],
      Elements: Array.from({ length: 12 }, (_, index) => ({ id: `e-${String(index)}` })),
    })

    renderDashboard([
      REQUIRED_SCOPES.CONSENTS_READ_ANY,
      REQUIRED_SCOPES.PURPOSES_READ,
      REQUIRED_SCOPES.ELEMENTS_READ,
    ])

    expect(await screen.findByText('100+')).toBeInTheDocument()
    expect(screen.getByText('29')).toBeInTheDocument()
    await waitFor(() => {
      expect(screen.getByText('8')).toBeInTheDocument()
    })
    expect(screen.getByText('12')).toBeInTheDocument()
    expect(
      screen.getByText('An overview of consent activity across all users.'),
    ).toBeInTheDocument()
    expect(myConsentsApi.fetchMyConsentsRaw).not.toHaveBeenCalled()
    expect(myConsentsApi.fetchMyConsents).not.toHaveBeenCalled()
    expect(screen.queryByText('Complaints')).not.toBeInTheDocument()
    expect(screen.queryByText('Needs your attention')).not.toBeInTheDocument()
  })

  it('shows an error, not a false "no pending consents" empty state, when that fetch fails', async () => {
    myConsentsApi.fetchMyConsentsRaw.mockResolvedValue(rawConsents(1))
    // The pending-list fetch (fetchMyConsents, not fetchMyConsentsRaw) fails independently of
    // the state-count queries, which still succeed.
    myConsentsApi.fetchMyConsents.mockRejectedValue(new Error('network error'))
    complaintsApi.fetchMyComplaintsTotal.mockResolvedValue(0)

    renderDashboard([REQUIRED_SCOPES.CONSENTS_READ_SELF])

    expect(await screen.findByText('Unable to load your dashboard right now.')).toBeInTheDocument()
    expect(screen.queryByText('You have no pending consents to review.')).not.toBeInTheDocument()
  })

  it('shows "-", not a false 0, for complaint counts when that fetch fails', async () => {
    myConsentsApi.fetchMyConsentsRaw.mockResolvedValue(rawConsents(1))
    myConsentsApi.fetchMyConsents.mockResolvedValue({
      data: [],
      metadata: { total: 0, offset: 0, count: 0, limit: 100 },
    })
    complaintsApi.fetchMyComplaintsTotal.mockRejectedValue(new Error('network error'))

    renderDashboard([REQUIRED_SCOPES.CONSENTS_READ_SELF, REQUIRED_SCOPES.COMPLAINTS_READ_SELF])

    expect(await screen.findByText('Unable to load your complaints right now.')).toBeInTheDocument()
    expect(screen.getByText('Total complaints')).toBeInTheDocument()
    // Six complaint tiles - all "-", never a false "0" implying a real (empty) count.
    expect(screen.queryAllByText('0')).toHaveLength(0)
    expect(screen.getAllByText('-').length).toBeGreaterThanOrEqual(6)
  })

  it('shows a DPO-only session no consent, catalog, or complaint widgets', () => {
    renderDashboard([REQUIRED_SCOPES.COMPLAINTS_READ_ANY])

    expect(screen.queryByText('Consents by status')).not.toBeInTheDocument()
    expect(screen.queryByText('Complaints')).not.toBeInTheDocument()
    expect(myConsentsApi.fetchMyConsentsRaw).not.toHaveBeenCalled()
    expect(adminConsentsApi.fetchAdminConsents).not.toHaveBeenCalled()
    expect(catalogApi.fetchPurposes).not.toHaveBeenCalled()
    expect(catalogApi.fetchElements).not.toHaveBeenCalled()
    expect(complaintsApi.fetchMyComplaintsTotal).not.toHaveBeenCalled()
  })
})
