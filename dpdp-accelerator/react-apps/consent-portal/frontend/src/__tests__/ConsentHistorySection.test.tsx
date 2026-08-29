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
import { AcrylicOrangeTheme, OxygenUIThemeProvider } from '@wso2/oxygen-ui'
import { I18nextProvider } from 'react-i18next'
import { afterEach, describe, expect, it, vi } from 'vitest'
import ConsentHistorySection from '../features/my-consents/components/details/ConsentHistorySection'
import i18n from '../i18n/i18n'
import type { ConsentStatusAuditEntry } from '../types/consentHistory'
import { REQUIRED_SCOPES, type ScopeRequirement } from '../utils/scopes'
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

function entry(overrides: Partial<ConsentStatusAuditEntry> = {}): ConsentStatusAuditEntry {
  return {
    currentStatus: 'ACTIVE',
    actionType: 'AUTHORIZE_APPROVE',
    actionBy: 'admin.reviewer@wso2.com',
    actionTime: 1785835726132,
    ...overrides,
  }
}

function renderSection(
  variant: 'self' | 'admin',
  scopes: ScopeRequirement[],
  queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } }),
): void {
  render(
    <OxygenUIThemeProvider theme={AcrylicOrangeTheme}>
      <I18nextProvider i18n={i18n}>
        <QueryClientProvider client={queryClient}>
          <TestAuthorizationProvider scopes={scopes}>
            <ConsentHistorySection consentId={CONSENT_ID} variant={variant} />
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

describe('ConsentHistorySection', () => {
  it('renders nothing without the status-history scope', () => {
    const { container } = render(
      <OxygenUIThemeProvider theme={AcrylicOrangeTheme}>
        <I18nextProvider i18n={i18n}>
          <QueryClientProvider client={new QueryClient()}>
            <TestAuthorizationProvider scopes={[REQUIRED_SCOPES.PURPOSES_READ]}>
              <ConsentHistorySection consentId={CONSENT_ID} variant="self" />
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
      pagination: { limit: 5, offset: 0, totalCount: 1 },
    })

    renderSection('self', [REQUIRED_SCOPES.CONSENT_STATUS_HISTORY_READ_SELF])

    expect(await screen.findByText('Approved')).toBeInTheDocument()
    expect(screen.getByText('admin.reviewer@wso2.com')).toBeInTheDocument()
    expect(consentHistoryApi.fetchMyConsentStatusHistory).toHaveBeenCalledWith(CONSENT_ID, {
      limit: 5,
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
      pagination: { limit: 5, offset: 0, totalCount: 1 },
    })

    renderSection('admin', [REQUIRED_SCOPES.CONSENT_STATUS_HISTORY_READ_ANY])

    // "Revoked" appears twice: the action label and the current-status chip.
    expect((await screen.findAllByText('Revoked')).length).toBeGreaterThanOrEqual(1)
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
      pagination: { limit: 5, offset: 0, totalCount: 1 },
    })

    renderSection('self', [REQUIRED_SCOPES.CONSENT_STATUS_HISTORY_READ_SELF])

    // "Expired" appears twice: the action label and the current-status chip.
    expect((await screen.findAllByText('Expired')).length).toBeGreaterThanOrEqual(1)
    expect(screen.getByText('System')).toBeInTheDocument()
    expect(screen.queryByText('SYSTEM')).not.toBeInTheDocument()
  })

  it('shows an empty state when there is no history', async () => {
    consentHistoryApi.fetchMyConsentStatusHistory.mockResolvedValue({
      consentId: CONSENT_ID,
      statusHistory: [],
      pagination: { limit: 5, offset: 0, totalCount: 0 },
    })

    renderSection('self', [REQUIRED_SCOPES.CONSENT_STATUS_HISTORY_READ_SELF])

    expect(await screen.findByText('No history is recorded for this consent.')).toBeInTheDocument()
  })

  it('grows the page size and refetches when Load more is clicked', async () => {
    consentHistoryApi.fetchMyConsentStatusHistory.mockResolvedValue({
      consentId: CONSENT_ID,
      statusHistory: [entry()],
      pagination: { limit: 5, offset: 0, totalCount: 12 },
    })

    renderSection('self', [REQUIRED_SCOPES.CONSENT_STATUS_HISTORY_READ_SELF])

    const loadMore = await screen.findByRole('button', { name: /Load more/ })
    fireEvent.click(loadMore)

    await waitFor(() => {
      expect(consentHistoryApi.fetchMyConsentStatusHistory).toHaveBeenCalledWith(CONSENT_ID, {
        limit: 10,
        offset: 0,
      })
    })
  })

  it('only offers "View Full Snapshot History" with the snapshot scope', async () => {
    consentHistoryApi.fetchMyConsentStatusHistory.mockResolvedValue({
      consentId: CONSENT_ID,
      statusHistory: [entry()],
      pagination: { limit: 5, offset: 0, totalCount: 1 },
    })

    renderSection('self', [REQUIRED_SCOPES.CONSENT_STATUS_HISTORY_READ_SELF])

    await screen.findByText('Approved')
    expect(
      screen.queryByRole('button', { name: 'View Full Snapshot History' }),
    ).not.toBeInTheDocument()
  })

  it('opens the full snapshot dialog and loads the full history on demand', async () => {
    consentHistoryApi.fetchMyConsentStatusHistory.mockResolvedValue({
      consentId: CONSENT_ID,
      statusHistory: [entry()],
      pagination: { limit: 5, offset: 0, totalCount: 1 },
    })
    consentHistoryApi.fetchMyConsentFullHistory.mockResolvedValue({
      consentId: CONSENT_ID,
      history: [],
      pagination: { limit: 5, offset: 0, totalCount: 0 },
    })

    renderSection('self', [
      REQUIRED_SCOPES.CONSENT_STATUS_HISTORY_READ_SELF,
      REQUIRED_SCOPES.CONSENT_FULL_HISTORY_READ_SELF,
    ])

    const openButton = await screen.findByRole('button', { name: 'View Full Snapshot History' })
    expect(consentHistoryApi.fetchMyConsentFullHistory).not.toHaveBeenCalled()

    fireEvent.click(openButton)

    expect(
      await screen.findByRole('heading', { name: 'View Full Snapshot History', level: 2 }),
    ).toBeInTheDocument()
    await waitFor(() => {
      expect(consentHistoryApi.fetchMyConsentFullHistory).toHaveBeenCalledWith(CONSENT_ID, {
        limit: 5,
        offset: 0,
      })
    })
  })
})
