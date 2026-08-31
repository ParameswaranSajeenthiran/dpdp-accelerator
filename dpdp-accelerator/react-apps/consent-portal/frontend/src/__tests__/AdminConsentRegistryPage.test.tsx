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
import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react'
import { I18nextProvider } from 'react-i18next'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { afterEach, describe, expect, it, vi } from 'vitest'
import AdminConsentRegistryPage from '../features/admin-consents/AdminConsentRegistryPage'
import i18n from '../i18n/i18n'
import type { AdminConsentListQueryParams } from '../types/consent'
import { REQUIRED_SCOPES } from '../utils/scopes'
import TestAuthorizationProvider from './TestAuthorizationProvider'

const adminConsentsApi = vi.hoisted(() => ({
  fetchAdminConsents: vi.fn(),
  fetchAdminConsentByID: vi.fn(),
  revokeAdminConsent: vi.fn(),
}))

// The network calls are stubbed, but the filter builder is pure and the page
// depends on the string it produces - keep the real one.
vi.mock('../features/admin-consents/api/adminConsentsApi', async (importOriginal) => ({
  ...(await importOriginal<typeof import('../features/admin-consents/api/adminConsentsApi')>()),
  ...adminConsentsApi,
}))

const NEXT_LINK = {
  rel: 'next',
  href: 'https://localhost:9443/api/identity/consent-mgt/v2.0/consents?limit=10&after=Mg==',
}

afterEach(() => {
  cleanup()
  vi.clearAllMocks()
})

function renderAdminPage(initialEntry = '/administration/consents'): void {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } })

  render(
    <OxygenUIThemeProvider theme={AcrylicOrangeTheme}>
      <CssBaseline />
      <I18nextProvider i18n={i18n}>
        <QueryClientProvider client={queryClient}>
          <MemoryRouter initialEntries={[initialEntry]}>
            <TestAuthorizationProvider scopes={[REQUIRED_SCOPES.CONSENTS_READ_ANY]}>
              <Routes>
                <Route path="/administration/consents" element={<AdminConsentRegistryPage />} />
              </Routes>
            </TestAuthorizationProvider>
          </MemoryRouter>
        </QueryClientProvider>
      </I18nextProvider>
    </OxygenUIThemeProvider>,
  )
}

/** The list parameters the page asked the consent management API for. */
function listParams(index = 0): AdminConsentListQueryParams {
  return adminConsentsApi.fetchAdminConsents.mock.calls[index]?.[0] as AdminConsentListQueryParams
}

describe('AdminConsentRegistryPage', () => {
  it('renders the native Consents envelope and labels subjects as users', async () => {
    adminConsentsApi.fetchAdminConsents.mockResolvedValue({
      totalResults: 2,
      links: [NEXT_LINK],
      Consents: [
        {
          id: 'db0759de-c098-4f44-b78d-6718226db8b2',
          subjectId: 'admin',
          serviceId: 'dpdp-portal-spike',
          state: 'PENDING',
          timestamp: 1785833928316,
        },
      ],
    })

    renderAdminPage()

    expect(await screen.findByText('admin')).toBeInTheDocument()
    expect(screen.getByText('dpdp-portal-spike')).toBeInTheDocument()
    expect(screen.getByRole('columnheader', { name: 'User' })).toBeInTheDocument()
    expect(screen.getByText('Pending')).toBeInTheDocument()

    expect(listParams()).toEqual({
      limit: 10,
      after: undefined,
      before: undefined,
      userId: undefined,
      relation: 'ANY',
      serviceId: undefined,
      state: undefined,
      purposeId: undefined,
      filter: undefined,
    })
  })

  it('renders purpose names from rows the api expanded with a detail lookup', async () => {
    adminConsentsApi.fetchAdminConsents.mockResolvedValue({
      totalResults: 1,
      links: [],
      Consents: [
        {
          id: 'db0759de-c098-4f44-b78d-6718226db8b2',
          subjectId: 'admin',
          serviceId: 'dpdp-portal-spike',
          state: 'ACTIVE',
          timestamp: 1785833928316,
          purposes: [
            {
              id: 'purpose-1',
              name: 'marketing-spike',
              type: 'CONSENT',
              versionId: 'version-1',
              version: '1.0.0',
              elements: [],
            },
          ],
        },
      ],
    })

    renderAdminPage()

    expect(await screen.findByText('marketing-spike')).toBeInTheDocument()
    expect(screen.getByRole('columnheader', { name: 'Purposes' })).toBeInTheDocument()
  })

  it('pages forward with the after cursor taken from links', async () => {
    adminConsentsApi.fetchAdminConsents.mockResolvedValue({
      totalResults: 2,
      links: [NEXT_LINK],
      Consents: [
        {
          id: 'consent-1',
          subjectId: 'admin',
          serviceId: 'dpdp-portal',
          state: 'ACTIVE',
          timestamp: 1785833928316,
        },
      ],
    })

    renderAdminPage()

    const nextButton = await screen.findByRole('button', { name: 'Next' })
    await waitFor(() => expect(nextButton).toBeEnabled())
    expect(screen.getByRole('button', { name: 'Previous' })).toBeDisabled()

    fireEvent.click(nextButton)

    await waitFor(() => {
      const cursors = adminConsentsApi.fetchAdminConsents.mock.calls.map(
        ([params]) => (params as AdminConsentListQueryParams).after,
      )
      expect(cursors).toContain('Mg==')
    })
  })

  it('uses the consent details endpoint for a Consent ID search', async () => {
    adminConsentsApi.fetchAdminConsentByID.mockResolvedValue({
      id: 'consent/123',
      subjectId: 'admin',
      serviceId: 'dpdp-portal',
      state: 'ACTIVE',
      timestamp: 1785833928316,
      purposes: [
        {
          id: 'purpose-1',
          name: 'marketing-spike',
          type: 'CONSENT',
          versionId: 'version-1',
          version: '1.0.0',
          elements: [],
        },
      ],
      authorizations: [],
    })

    renderAdminPage('/administration/consents?consentId=consent%2F123')

    expect(await screen.findByText('marketing-spike')).toBeInTheDocument()
    // A Consent ID search reads the one consent instead of listing.
    expect(adminConsentsApi.fetchAdminConsentByID).toHaveBeenCalledWith('consent/123')
    expect(adminConsentsApi.fetchAdminConsents).not.toHaveBeenCalled()
    expect(screen.getByRole('link', { name: 'View' })).toHaveAttribute(
      'href',
      '/administration/consents/consent%2F123',
    )
  })
})
