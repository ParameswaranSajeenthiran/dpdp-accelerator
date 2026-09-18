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

const myConsentsApi = vi.hoisted(() => ({ fetchMyConsents: vi.fn() }))
const adminConsentsApi = vi.hoisted(() => ({ fetchAdminConsents: vi.fn() }))
const complaintsApi = vi.hoisted(() => ({ listMyComplaints: vi.fn() }))

vi.mock('../features/my-consents/api/myConsentsApi', () => myConsentsApi)
vi.mock('../features/admin-consents/api/adminConsentsApi', () => adminConsentsApi)
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

describe('DashboardPage', () => {
  it('shows a regular user their own consent and complaint stats, not a tenant-wide view', async () => {
    myConsentsApi.fetchMyConsents.mockResolvedValue({
      data: [
        { id: 'c1', subjectId: 'user-1', serviceId: 'svc', state: 'ACTIVE', timestamp: 1 },
        { id: 'c2', subjectId: 'user-1', serviceId: 'svc', state: 'ACTIVE', timestamp: 2 },
        { id: 'c3', subjectId: 'user-1', serviceId: 'svc', state: 'ACTIVE', timestamp: 3 },
      ],
      metadata: { total: 3, offset: 0, count: 3, limit: 100 },
    })
    complaintsApi.listMyComplaints.mockResolvedValue({
      data: [
        { id: 'k1', status: 'OPEN' },
        { id: 'k2', status: 'IN_PROGRESS' },
        { id: 'k3', status: 'RESOLVED' },
      ],
      metadata: { total: 3, offset: 0, count: 3, limit: 100 },
    })

    renderDashboard([REQUIRED_SCOPES.CONSENTS_READ_SELF, REQUIRED_SCOPES.COMPLAINTS_READ_SELF])

    expect(await screen.findByText('Your complaints')).toBeInTheDocument()
    expect(screen.getByText('Active consents')).toBeInTheDocument()
    expect(screen.getByText('Needs your attention')).toBeInTheDocument()
    expect(screen.getByText('Open complaints')).toBeInTheDocument()
    expect(screen.getByText('Resolved complaints')).toBeInTheDocument()
    // Three active consents, two complaints not yet resolved (OPEN + IN_PROGRESS), one resolved.
    await waitFor(() => {
      expect(screen.getByText('3')).toBeInTheDocument()
      expect(screen.getByText('2')).toBeInTheDocument()
      expect(screen.getByText('1')).toBeInTheDocument()
    })
    expect(adminConsentsApi.fetchAdminConsents).not.toHaveBeenCalled()
  })

  it('shows an admin the exact tenant-wide totals, not a count of the paged sample', async () => {
    // The unfiltered sample backing the purposes/services breakdowns deliberately disagrees
    // with the per-state totals below - a real tenant can hold far more consents than that
    // sample ever pages through, so the stat tiles must come from an exhaustive per-state
    // count instead.
    function consentsOfState(state: string, count: number) {
      return Array.from({ length: count }, (_, index) => ({
        id: `${state}-${String(index)}`,
        subjectId: 'user-1',
        serviceId: 'svc',
        state,
        timestamp: index,
      }))
    }
    adminConsentsApi.fetchAdminConsents.mockImplementation(
      (params: { state?: string }): Promise<unknown> => {
        if (params.state === 'ACTIVE') {
          return Promise.resolve({
            totalResults: 42,
            links: [],
            Consents: consentsOfState('ACTIVE', 42),
          })
        }
        if (params.state === 'PENDING') {
          return Promise.resolve({
            totalResults: 7,
            links: [],
            Consents: consentsOfState('PENDING', 7),
          })
        }
        return Promise.resolve({
          totalResults: 1,
          links: [],
          Consents: [
            { id: 'c1', subjectId: 'user-1', serviceId: 'svc', state: 'ACTIVE', timestamp: 1 },
          ],
        })
      },
    )

    renderDashboard([REQUIRED_SCOPES.CONSENTS_READ_ANY])

    expect(await screen.findByText('42')).toBeInTheDocument()
    expect(screen.getByText('7')).toBeInTheDocument()
    expect(
      screen.getByText('An overview of consent activity across all users.'),
    ).toBeInTheDocument()
    expect(myConsentsApi.fetchMyConsents).not.toHaveBeenCalled()
    expect(screen.queryByText('Your complaints')).not.toBeInTheDocument()
    // A pending consent is waiting on the data subject, not the admin - nothing here is
    // actually "needing review" from an admin, so the section is dropped for that view.
    expect(screen.queryByText('Needs your attention')).not.toBeInTheDocument()
    expect(screen.queryByText('You have no pending consents to review.')).not.toBeInTheDocument()
  })

  it('shows a DPO-only session no consent or complaint widgets, since both would duplicate other pages', () => {
    renderDashboard([REQUIRED_SCOPES.COMPLAINTS_READ_ANY])

    expect(screen.queryByText('Active consents')).not.toBeInTheDocument()
    expect(screen.queryByText('Your complaints')).not.toBeInTheDocument()
    expect(myConsentsApi.fetchMyConsents).not.toHaveBeenCalled()
    expect(adminConsentsApi.fetchAdminConsents).not.toHaveBeenCalled()
    expect(complaintsApi.listMyComplaints).not.toHaveBeenCalled()
  })
})
