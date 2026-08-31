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
import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react'
import { AcrylicOrangeTheme, CssBaseline, OxygenUIThemeProvider } from '@wso2/oxygen-ui'
import { I18nextProvider } from 'react-i18next'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { afterEach, describe, expect, it, vi } from 'vitest'
import ConsentDetailsPage from '../features/my-consents/ConsentDetailsPage'
import i18n from '../i18n/i18n'
import type { ConsentDetail } from '../types/consent'
import { REQUIRED_SCOPES, type ScopeRequirement } from '../utils/scopes'
import TestAuthorizationProvider from './TestAuthorizationProvider'

const adminConsentsApi = vi.hoisted(() => ({
  fetchAdminConsentByID: vi.fn(),
  revokeAdminConsent: vi.fn(),
}))

const myConsentsApi = vi.hoisted(() => ({
  approveMyConsent: vi.fn(),
  rejectMyConsent: vi.fn(),
  revokeMyConsent: vi.fn(),
  fetchMyConsentByID: vi.fn(),
  fetchMyConsents: vi.fn(),
}))

vi.mock('../features/admin-consents/api/adminConsentsApi', () => adminConsentsApi)
vi.mock('../features/my-consents/api/myConsentsApi', () => myConsentsApi)

const CONSENT_ID = '06168ee0-f82a-4b0f-87ea-2a37600ec3f2'
// The signed-in identity every TestAuthorizationProvider session uses.
const CURRENT_USER_ID = 'test-user'

function buildConsent(state: string, overrides: Partial<ConsentDetail> = {}): ConsentDetail {
  return {
    id: CONSENT_ID,
    subjectId: 'someone-else',
    serviceId: 'dpdp-portal',
    state,
    timestamp: 1785835726132,
    purposes: [],
    authorizations: [],
    properties: {},
    ...overrides,
  }
}

function renderAdminDetailPage(scopes: ScopeRequirement[]): void {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } })

  render(
    <OxygenUIThemeProvider theme={AcrylicOrangeTheme}>
      <CssBaseline />
      <I18nextProvider i18n={i18n}>
        <QueryClientProvider client={queryClient}>
          <MemoryRouter initialEntries={[`/administration/consents/${CONSENT_ID}`]}>
            <TestAuthorizationProvider scopes={scopes}>
              <Routes>
                <Route
                  path="/administration/consents/:id"
                  element={<ConsentDetailsPage variant="admin" />}
                />
              </Routes>
            </TestAuthorizationProvider>
          </MemoryRouter>
        </QueryClientProvider>
      </I18nextProvider>
    </OxygenUIThemeProvider>,
  )
}

afterEach(() => {
  cleanup()
  vi.clearAllMocks()
})

describe('ConsentDetailsPage admin variant - acting on a consent as its own stakeholder', () => {
  it('offers only revoke for an uninvolved consent when the admin has the any-consent scope', async () => {
    adminConsentsApi.fetchAdminConsentByID.mockResolvedValue(buildConsent('ACTIVE'))

    renderAdminDetailPage([REQUIRED_SCOPES.CONSENTS_READ_ANY, REQUIRED_SCOPES.CONSENTS_WRITE_ANY])

    expect(await screen.findByRole('button', { name: 'Revoke' })).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Approve' })).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Reject' })).not.toBeInTheDocument()
  })

  it('offers no lifecycle action for an uninvolved consent without the any-consent write scope', async () => {
    adminConsentsApi.fetchAdminConsentByID.mockResolvedValue(buildConsent('ACTIVE'))

    renderAdminDetailPage([REQUIRED_SCOPES.CONSENTS_READ_ANY, REQUIRED_SCOPES.CONSENTS_WRITE_SELF])

    expect(await screen.findByRole('heading', { name: 'Consent Details' })).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Revoke' })).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Approve' })).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Reject' })).not.toBeInTheDocument()
  })

  it('offers approve, reject and revoke when the admin is themselves the subject', async () => {
    adminConsentsApi.fetchAdminConsentByID.mockResolvedValue(
      buildConsent('PENDING', { subjectId: CURRENT_USER_ID }),
    )

    renderAdminDetailPage([REQUIRED_SCOPES.CONSENTS_READ_ANY, REQUIRED_SCOPES.CONSENTS_WRITE_SELF])

    expect(await screen.findByRole('button', { name: 'Approve' })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Reject' })).toBeInTheDocument()
  })

  it('offers approve and reject when the admin is listed as an authorizer, not the subject', async () => {
    adminConsentsApi.fetchAdminConsentByID.mockResolvedValue(
      buildConsent('PENDING', {
        authorizations: [{ userId: CURRENT_USER_ID, state: 'PENDING', updatedTime: 1 }],
      }),
    )

    renderAdminDetailPage([REQUIRED_SCOPES.CONSENTS_READ_ANY, REQUIRED_SCOPES.CONSENTS_WRITE_SELF])

    expect(await screen.findByRole('button', { name: 'Approve' })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Reject' })).toBeInTheDocument()
  })

  it('offers only reject when the admin, as authorizer, already approved - the aggregate is still pending on others', async () => {
    adminConsentsApi.fetchAdminConsentByID.mockResolvedValue(
      buildConsent('PENDING', {
        authorizations: [{ userId: CURRENT_USER_ID, state: 'APPROVED', updatedTime: 1 }],
      }),
    )

    renderAdminDetailPage([REQUIRED_SCOPES.CONSENTS_READ_ANY, REQUIRED_SCOPES.CONSENTS_WRITE_SELF])

    expect(await screen.findByRole('button', { name: 'Reject' })).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Approve' })).not.toBeInTheDocument()
  })

  it('lets an admin who is also the subject revoke via the any-consent scope even without write-self', async () => {
    adminConsentsApi.fetchAdminConsentByID.mockResolvedValue(
      buildConsent('ACTIVE', { subjectId: CURRENT_USER_ID }),
    )

    renderAdminDetailPage([REQUIRED_SCOPES.CONSENTS_READ_ANY, REQUIRED_SCOPES.CONSENTS_WRITE_ANY])

    expect(await screen.findByRole('button', { name: 'Revoke' })).toBeInTheDocument()
  })

  it('calls the self-service authorize endpoint (not the admin API) when approving from the admin view', async () => {
    adminConsentsApi.fetchAdminConsentByID.mockResolvedValue(
      buildConsent('PENDING', { subjectId: CURRENT_USER_ID }),
    )
    myConsentsApi.approveMyConsent.mockResolvedValue({ status: 'OK' })

    renderAdminDetailPage([REQUIRED_SCOPES.CONSENTS_READ_ANY, REQUIRED_SCOPES.CONSENTS_WRITE_SELF])

    fireEvent.click(await screen.findByRole('button', { name: 'Approve' }))
    fireEvent.click(await screen.findByRole('button', { name: 'Approve Consent' }))

    await waitFor(() => {
      expect(myConsentsApi.approveMyConsent).toHaveBeenCalledWith(CONSENT_ID, CURRENT_USER_ID)
    })
  })
})
