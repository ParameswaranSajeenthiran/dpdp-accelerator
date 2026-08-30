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
import { cleanup, fireEvent, render, screen } from '@testing-library/react'
import { AcrylicOrangeTheme, OxygenUIThemeProvider } from '@wso2/oxygen-ui'
import { I18nextProvider } from 'react-i18next'
import { afterEach, describe, expect, it, vi } from 'vitest'
import ConsentLifecycleSection from '../features/my-consents/components/details/ConsentLifecycleSection'
import i18n from '../i18n/i18n'
import type { ConsentStatusAuditEntry } from '../types/consentHistory'
import { CONSENT_HISTORY_SCOPES, type ScopeRequirement } from '../utils/scopes'
import TestAuthorizationProvider from './TestAuthorizationProvider'

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

const CONSENT_ID = 'c8f3a1e0-92b4-4d2a-9e77-1a2b3c4d5e6f'
const SELF_SCOPE: ScopeRequirement[] = [[CONSENT_HISTORY_SCOPES.STATUS_HISTORY_VIEW_SELF]]
const ADMIN_SCOPE: ScopeRequirement[] = [[CONSENT_HISTORY_SCOPES.STATUS_HISTORY_VIEW_ANY]]
const SELF_SCOPE_WITH_SNAPSHOT: ScopeRequirement[] = [
  [CONSENT_HISTORY_SCOPES.STATUS_HISTORY_VIEW_SELF],
  [CONSENT_HISTORY_SCOPES.HISTORY_VIEW_SELF],
]

function entry(overrides: Partial<ConsentStatusAuditEntry> = {}): ConsentStatusAuditEntry {
  return {
    currentStatus: 'ACTIVE',
    actionType: 'AUTHORIZE_APPROVE',
    actionBy: 'admin.reviewer@wso2.com',
    actionTime: 1785835726132,
    ...overrides,
  }
}

function renderSection(variant: 'self' | 'admin', scopes: ScopeRequirement[]): void {
  render(
    <OxygenUIThemeProvider theme={AcrylicOrangeTheme}>
      <I18nextProvider i18n={i18n}>
        <QueryClientProvider
          client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}
        >
          <TestAuthorizationProvider scopes={scopes}>
            <ConsentLifecycleSection consentId={CONSENT_ID} variant={variant} />
          </TestAuthorizationProvider>
        </QueryClientProvider>
      </I18nextProvider>
    </OxygenUIThemeProvider>,
  )
}

afterEach(() => {
  cleanup()
  vi.clearAllMocks()
})

describe('ConsentLifecycleSection', () => {
  it('renders nothing without the status-history scope', () => {
    const { container } = render(
      <OxygenUIThemeProvider theme={AcrylicOrangeTheme}>
        <I18nextProvider i18n={i18n}>
          <QueryClientProvider client={new QueryClient()}>
            <TestAuthorizationProvider scopes={[]}>
              <ConsentLifecycleSection consentId={CONSENT_ID} variant="self" />
            </TestAuthorizationProvider>
          </QueryClientProvider>
        </I18nextProvider>
      </OxygenUIThemeProvider>,
    )

    expect(container).toBeEmptyDOMElement()
  })

  it('renders the timeline for the self variant off the self API', async () => {
    consentHistoryApi.fetchMyConsentStatusHistory.mockResolvedValue({
      consentId: CONSENT_ID,
      statusHistory: [entry()],
      pagination: { limit: 100, offset: 0, totalCount: 1 },
    })

    renderSection('self', SELF_SCOPE)

    expect(await screen.findByText('Approved by admin.reviewer@wso2.com')).toBeInTheDocument()
    expect(consentHistoryApi.fetchMyConsentStatusHistory).toHaveBeenCalledWith(CONSENT_ID, {
      limit: 100,
      offset: 0,
    })
    expect(adminConsentHistoryApi.fetchAdminConsentStatusHistory).not.toHaveBeenCalled()
  })

  it('renders the timeline for the admin variant off the admin API', async () => {
    adminConsentHistoryApi.fetchAdminConsentStatusHistory.mockResolvedValue({
      consentId: CONSENT_ID,
      statusHistory: [
        entry({ actionType: 'REVOKE', currentStatus: 'REVOKED', previousStatus: 'ACTIVE' }),
      ],
      pagination: { limit: 100, offset: 0, totalCount: 1 },
    })

    renderSection('admin', ADMIN_SCOPE)

    expect(await screen.findByText('Revoked by admin.reviewer@wso2.com')).toBeInTheDocument()
    expect(consentHistoryApi.fetchMyConsentStatusHistory).not.toHaveBeenCalled()
  })

  it('shows a system actor distinctly for EXPIRE entries', async () => {
    consentHistoryApi.fetchMyConsentStatusHistory.mockResolvedValue({
      consentId: CONSENT_ID,
      statusHistory: [
        entry({
          actionType: 'EXPIRE',
          actionBy: 'SYSTEM',
          currentStatus: 'EXPIRED',
          previousStatus: 'ACTIVE',
        }),
      ],
      pagination: { limit: 100, offset: 0, totalCount: 1 },
    })

    renderSection('self', SELF_SCOPE)

    expect(await screen.findByText('Expired by System')).toBeInTheDocument()
    expect(screen.queryByText(/SYSTEM/)).not.toBeInTheDocument()
  })

  it('shows an empty state when there is no history', async () => {
    consentHistoryApi.fetchMyConsentStatusHistory.mockResolvedValue({
      consentId: CONSENT_ID,
      statusHistory: [],
      pagination: { limit: 100, offset: 0, totalCount: 0 },
    })

    renderSection('self', SELF_SCOPE)

    expect(await screen.findByText('No lifecycle events are available.')).toBeInTheDocument()
  })

  it('only offers "View Full Snapshot History" with the snapshot scope', async () => {
    consentHistoryApi.fetchMyConsentStatusHistory.mockResolvedValue({
      consentId: CONSENT_ID,
      statusHistory: [entry()],
      pagination: { limit: 100, offset: 0, totalCount: 1 },
    })

    renderSection('self', SELF_SCOPE)

    await screen.findByText('Approved by admin.reviewer@wso2.com')
    expect(
      screen.queryByRole('button', { name: 'View Full Snapshot History' }),
    ).not.toBeInTheDocument()
  })

  it('opens the full snapshot dialog and loads the full history on demand', async () => {
    consentHistoryApi.fetchMyConsentStatusHistory.mockResolvedValue({
      consentId: CONSENT_ID,
      statusHistory: [entry()],
      pagination: { limit: 100, offset: 0, totalCount: 1 },
    })
    consentHistoryApi.fetchMyConsentFullHistory.mockResolvedValue({
      consentId: CONSENT_ID,
      history: [],
      pagination: { limit: 100, offset: 0, totalCount: 0 },
    })

    renderSection('self', SELF_SCOPE_WITH_SNAPSHOT)

    const openButton = await screen.findByRole('button', { name: 'View Full Snapshot History' })
    expect(consentHistoryApi.fetchMyConsentFullHistory).not.toHaveBeenCalled()

    fireEvent.click(openButton)

    expect(
      await screen.findByRole('heading', { name: 'View Full Snapshot History', level: 2 }),
    ).toBeInTheDocument()
    expect(consentHistoryApi.fetchMyConsentFullHistory).toHaveBeenCalledWith(CONSENT_ID, {
      limit: 100,
      offset: 0,
    })
  })

  it('restricts the snapshot diff to expiryTime, properties and authorizations', async () => {
    consentHistoryApi.fetchMyConsentStatusHistory.mockResolvedValue({
      consentId: CONSENT_ID,
      statusHistory: [entry()],
      pagination: { limit: 100, offset: 0, totalCount: 1 },
    })
    consentHistoryApi.fetchMyConsentFullHistory.mockResolvedValue({
      consentId: CONSENT_ID,
      history: [
        {
          actionType: 'CREATE',
          actionBy: 'jane@wso2.com',
          actionTime: 1785835726132,
          snapshot: {
            expiryTime: 1_800_000_000_000,
            properties: { region: 'EU' },
            authorizations: [
              { userId: 'jane@wso2.com', type: 'SELF', status: 'APPROVED', updatedTime: 1 },
            ],
          },
        },
      ],
      pagination: { limit: 100, offset: 0, totalCount: 1 },
    })

    renderSection('self', SELF_SCOPE_WITH_SNAPSHOT)

    fireEvent.click(await screen.findByRole('button', { name: 'View Full Snapshot History' }))
    fireEvent.click(await screen.findByRole('button', { name: /Consent created/ }))

    expect(await screen.findByText('EU')).toBeInTheDocument()
    expect(screen.queryByText('customer-360-portal')).not.toBeInTheDocument()
  })
})
