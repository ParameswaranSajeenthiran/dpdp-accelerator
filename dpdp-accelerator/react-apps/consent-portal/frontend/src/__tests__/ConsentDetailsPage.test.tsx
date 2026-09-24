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
import { cleanup, fireEvent, render, screen, waitFor, within } from '@testing-library/react'
import { AcrylicOrangeTheme, CssBaseline, OxygenUIThemeProvider } from '@wso2/oxygen-ui'
import { I18nextProvider } from 'react-i18next'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { afterEach, describe, expect, it, vi } from 'vitest'
import ConsentDetailsPage from '../features/my-consents/ConsentDetailsPage'
import i18n from '../i18n/i18n'
import type { ConsentDetail } from '../types/consent'
import { APIError } from '../utils/apiClient'
import { REQUIRED_SCOPES, type ScopeRequirement } from '../utils/scopes'
import TestAuthorizationProvider from './TestAuthorizationProvider'

const consentsApi = vi.hoisted(() => ({
  fetchMyConsentByID: vi.fn(),
  approveMyConsent: vi.fn(),
  rejectMyConsent: vi.fn(),
  revokeMyConsent: vi.fn(),
  fetchMyConsents: vi.fn(),
}))

vi.mock('../features/my-consents/api/myConsentsApi', () => consentsApi)

const EMPTY_STATUS_HISTORY = {
  consentId: '',
  statusHistory: [],
  pagination: { limit: 100, offset: 0, totalCount: 0 },
}

const consentHistoryApi = vi.hoisted(() => ({
  fetchMyConsentStatusHistory: vi.fn(),
  fetchMyConsentFullHistory: vi.fn(),
}))
const adminConsentHistoryApi = vi.hoisted(() => ({
  fetchAdminConsentStatusHistory: vi.fn(),
  fetchAdminConsentFullHistory: vi.fn(),
}))

vi.mock('../features/my-consents/api/consentHistoryApi', () => consentHistoryApi)
vi.mock('../features/admin-consents/api/consentHistoryApi', () => adminConsentHistoryApi)

const CONSENT_ID = '06168ee0-f82a-4b0f-87ea-2a37600ec3f2'
// The signed-in identity every TestAuthorizationProvider session uses.
const CURRENT_USER_ID = 'test-user'

function buildConsent(state: string, overrides: Partial<ConsentDetail> = {}): ConsentDetail {
  return {
    id: CONSENT_ID,
    subjectId: 'admin',
    serviceId: 'dpdp-portal',
    state,
    language: 'en',
    timestamp: 1785835726132,
    purposes: [
      {
        id: '690eb7ef-3a32-4439-b006-2d47f2fb6885',
        name: 'marketing-spike',
        type: 'CONSENT',
        versionId: 'cc689174-c91a-449d-ae85-05c33cab1721',
        version: '1.0.0',
        elements: [
          {
            id: '415976b9-85b3-409c-b195-35a2733b0afb',
            name: 'email-spike',
            displayName: 'Email Address',
          },
        ],
        properties: {},
      },
    ],
    authorizations: [{ userId: 'admin', state: 'APPROVED', updatedTime: 1785835726345 }],
    properties: {},
    ...overrides,
  }
}

function renderPage(scopes: ScopeRequirement[]): void {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } })

  render(
    <OxygenUIThemeProvider theme={AcrylicOrangeTheme}>
      <CssBaseline />
      <I18nextProvider i18n={i18n}>
        <QueryClientProvider client={queryClient}>
          <MemoryRouter initialEntries={[`/consents/${CONSENT_ID}`]}>
            <TestAuthorizationProvider scopes={scopes}>
              <Routes>
                <Route path="/consents/:id" element={<ConsentDetailsPage />} />
              </Routes>
            </TestAuthorizationProvider>
          </MemoryRouter>
        </QueryClientProvider>
      </I18nextProvider>
    </OxygenUIThemeProvider>,
  )
}

function renderConsentDetailsPage(
  state: string,
  scopes: ScopeRequirement[] = Object.values(REQUIRED_SCOPES),
  overrides: Partial<ConsentDetail> = {},
): void {
  consentsApi.fetchMyConsentByID.mockResolvedValue(buildConsent(state, overrides))
  consentHistoryApi.fetchMyConsentStatusHistory.mockResolvedValue(EMPTY_STATUS_HISTORY)
  adminConsentHistoryApi.fetchAdminConsentStatusHistory.mockResolvedValue(EMPTY_STATUS_HISTORY)
  renderPage(scopes)
}

afterEach(() => {
  cleanup()
  vi.clearAllMocks()
})

describe('ConsentDetailsPage lifecycle actions - caller with their own authorization entry', () => {
  it('shows approve and reject while their own decision is pending, without revoke', async () => {
    renderConsentDetailsPage('PENDING', Object.values(REQUIRED_SCOPES), {
      authorizations: [{ userId: CURRENT_USER_ID, state: 'PENDING', updatedTime: 1 }],
    })

    expect(await screen.findByRole('button', { name: 'Approve' })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Reject' })).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Revoke' })).not.toBeInTheDocument()
  })

  it('shows only revoke once active - a decision cannot be reopened by rejecting', async () => {
    renderConsentDetailsPage('ACTIVE', Object.values(REQUIRED_SCOPES), {
      authorizations: [{ userId: CURRENT_USER_ID, state: 'APPROVED', updatedTime: 1 }],
    })

    expect(await screen.findByRole('button', { name: 'Revoke' })).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Approve' })).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Reject' })).not.toBeInTheDocument()
  })

  it('shows no action, only a message, once the caller has rejected it themself', async () => {
    renderConsentDetailsPage('REJECTED', Object.values(REQUIRED_SCOPES), {
      authorizations: [{ userId: CURRENT_USER_ID, state: 'REJECTED', updatedTime: 1 }],
    })

    expect(await screen.findByText("You've rejected this consent.")).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Approve' })).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Reject' })).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Revoke' })).not.toBeInTheDocument()
  })

  it('shows a waiting message, not a button, once decided but the aggregate is still pending on someone else', async () => {
    renderConsentDetailsPage('PENDING', Object.values(REQUIRED_SCOPES), {
      authorizations: [
        { userId: CURRENT_USER_ID, state: 'APPROVED', updatedTime: 1 },
        { userId: 'co-authoriser', state: 'PENDING', updatedTime: 2 },
      ],
    })

    expect(
      await screen.findByText("You've made your decision. Waiting for the rest to decide."),
    ).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Approve' })).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Reject' })).not.toBeInTheDocument()
  })

  it('hides lifecycle actions without the consent write scope', async () => {
    // Self-service writes are gated on internal_login, which a session scoped
    // to the catalogue alone does not carry.
    renderConsentDetailsPage('PENDING', [REQUIRED_SCOPES.PURPOSES_READ], {
      authorizations: [{ userId: CURRENT_USER_ID, state: 'PENDING', updatedTime: 1 }],
    })

    expect(await screen.findByRole('heading', { name: 'Consent Details' })).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Approve' })).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Reject' })).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Revoke' })).not.toBeInTheDocument()
  })

  it.each(['REVOKED', 'EXPIRED'])(
    'shows no lifecycle action for %s consents, overriding even a stale approved entry - a withdrawal or lapse stays final',
    async (state) => {
      renderConsentDetailsPage(state, Object.values(REQUIRED_SCOPES), {
        authorizations: [{ userId: CURRENT_USER_ID, state: 'APPROVED', updatedTime: 1 }],
      })

      expect(await screen.findByRole('heading', { name: 'Consent Details' })).toBeInTheDocument()
      expect(screen.queryByRole('button', { name: 'Approve' })).not.toBeInTheDocument()
      expect(screen.queryByRole('button', { name: 'Reject' })).not.toBeInTheDocument()
      expect(screen.queryByRole('button', { name: 'Revoke' })).not.toBeInTheDocument()
      expect(
        await screen.findByText(
          state === 'REVOKED' ? 'This consent has been revoked.' : 'This consent has expired.',
        ),
      ).toBeInTheDocument()
    },
  )
})

describe('ConsentDetailsPage lifecycle actions - Direct Consent (no one else named)', () => {
  it('lets the subject revoke it once active', async () => {
    renderConsentDetailsPage('ACTIVE', Object.values(REQUIRED_SCOPES), {
      subjectId: CURRENT_USER_ID,
      authorizations: [],
    })

    expect(await screen.findByRole('button', { name: 'Revoke' })).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Approve' })).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Reject' })).not.toBeInTheDocument()
  })
})

describe('ConsentDetailsPage lifecycle actions - pure observer (subject with no authorization entry)', () => {
  it.each([
    ['PENDING', 'Waiting for authoriser approval.'],
    ['ACTIVE', 'This consent has been approved.'],
    ['REJECTED', 'This consent has been rejected.'],
  ])(
    'shows only a status message for a %s consent, never a button',
    async (state, expectedText) => {
      // The default fixture's only authorization entry belongs to "admin", not
      // the signed-in "test-user" - an observer on someone else's decision.
      renderConsentDetailsPage(state)

      expect(await screen.findByText(expectedText)).toBeInTheDocument()
      expect(screen.queryByRole('button', { name: 'Approve' })).not.toBeInTheDocument()
      expect(screen.queryByRole('button', { name: 'Reject' })).not.toBeInTheDocument()
      expect(screen.queryByRole('button', { name: 'Revoke' })).not.toBeInTheDocument()
    },
  )
})

describe('ConsentDetailsPage content', () => {
  it('renders subject, service and purposes from the native payload', async () => {
    renderConsentDetailsPage('ACTIVE')

    expect(await screen.findByRole('heading', { name: 'Consent Details' })).toBeInTheDocument()
    expect(screen.getByText('marketing-spike')).toBeInTheDocument()
    expect(screen.getByText('1.0.0')).toBeInTheDocument()
    expect(screen.getAllByText('admin').length).toBeGreaterThan(0)
    expect(screen.getByText('dpdp-portal')).toBeInTheDocument()
  })

  it('renders consent properties in a key/value table', async () => {
    renderConsentDetailsPage('ACTIVE', Object.values(REQUIRED_SCOPES), {
      properties: { dataCategory: 'financial', region: 'EU' },
    })

    expect(await screen.findByRole('heading', { name: 'Properties' })).toBeInTheDocument()
    expect(screen.getByText('dataCategory')).toBeInTheDocument()
    expect(screen.getByText('financial')).toBeInTheDocument()
    expect(screen.getByText('region')).toBeInTheDocument()
    expect(screen.getByText('EU')).toBeInTheDocument()
  })

  it('shows an empty state when a consent has no properties', async () => {
    renderConsentDetailsPage('ACTIVE')

    expect(
      await screen.findByText('No properties are associated with this consent.'),
    ).toBeInTheDocument()
  })

  it('lists authorizations by username with their state', async () => {
    renderConsentDetailsPage('ACTIVE')

    const authorizationsTable = await screen.findByRole('table', { name: 'Authorizations' })
    const rows = within(authorizationsTable).getAllByRole('row')

    expect(within(rows[1]).getByText('admin')).toBeInTheDocument()
    expect(within(rows[1]).getByText('Approved')).toBeInTheDocument()
  })

  it('renders no consent lifecycle history section', async () => {
    renderConsentDetailsPage('ACTIVE')

    expect(await screen.findByRole('heading', { name: 'Consent Details' })).toBeInTheDocument()
    expect(screen.queryByRole('table', { name: 'Consent lifecycle' })).not.toBeInTheDocument()
    expect(screen.queryByText('View Resources')).not.toBeInTheDocument()
  })

  it('surfaces the API message when approving a consent that is not PENDING', async () => {
    consentsApi.fetchMyConsentByID.mockResolvedValue(
      buildConsent('PENDING', {
        authorizations: [{ userId: CURRENT_USER_ID, state: 'PENDING', updatedTime: 1 }],
      }),
    )
    consentHistoryApi.fetchMyConsentStatusHistory.mockResolvedValue(EMPTY_STATUS_HISTORY)
    adminConsentHistoryApi.fetchAdminConsentStatusHistory.mockResolvedValue(EMPTY_STATUS_HISTORY)
    consentsApi.approveMyConsent.mockRejectedValue(
      new APIError(409, 'INVALID_CONSENT_STATE', 'Consent is not in PENDING state.'),
    )

    renderPage(Object.values(REQUIRED_SCOPES))

    fireEvent.click(await screen.findByRole('button', { name: 'Approve' }))
    fireEvent.click(await screen.findByRole('button', { name: 'Approve Consent' }))

    await waitFor(() => {
      expect(screen.getByRole('alert')).toHaveTextContent('Consent is not in PENDING state.')
    })
    expect(consentsApi.approveMyConsent).toHaveBeenCalledWith(CONSENT_ID, CURRENT_USER_ID)
  })
})
