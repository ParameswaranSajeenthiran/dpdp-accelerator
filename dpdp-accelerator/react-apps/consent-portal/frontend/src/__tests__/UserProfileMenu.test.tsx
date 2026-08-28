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
import UserProfileMenu from '../components/layout/main-layout/UserProfileMenu'
import i18n from '../i18n/i18n'
import { APIError } from '../utils/apiClient'
import { REQUIRED_SCOPES, type ScopeRequirement } from '../utils/scopes'
import TestAuthorizationProvider from './TestAuthorizationProvider'

const authMocks = vi.hoisted(() => ({
  getUserProfile: vi.fn<() => Promise<Record<string, unknown> | undefined>>(),
  logout: vi.fn<() => Promise<void>>(),
  clearLocalSession: vi.fn<() => Promise<void>>(),
}))

const accountMocks = vi.hoisted(() => ({
  deleteMyAccount: vi.fn<() => Promise<'deleted' | 'pendingApproval'>>(),
}))

vi.mock('../utils/authClient', () => authMocks)
vi.mock('../features/account/api/accountApi', () => accountMocks)

afterEach(() => {
  cleanup()
  vi.clearAllMocks()
})

function renderComponent(scopes: ScopeRequirement[] = []): void {
  render(
    <OxygenUIThemeProvider theme={AcrylicOrangeTheme}>
      <I18nextProvider i18n={i18n}>
        <QueryClientProvider client={new QueryClient()}>
          <TestAuthorizationProvider scopes={scopes}>
            <UserProfileMenu />
          </TestAuthorizationProvider>
        </QueryClientProvider>
      </I18nextProvider>
    </OxygenUIThemeProvider>,
  )
}

/**
 * Renders the menu and opens it. The claims are fetched asynchronously, so
 * wait for the resolved name before opening the menu, otherwise the assertions
 * race the effect that loads them.
 */
async function renderMenu(
  profile?: Record<string, unknown>,
  scopes: ScopeRequirement[] = [],
): Promise<void> {
  authMocks.getUserProfile.mockResolvedValue(profile)
  authMocks.logout.mockResolvedValue()
  renderComponent(scopes)
  await waitFor(() => expect(authMocks.getUserProfile).toHaveBeenCalled())
  fireEvent.click(await screen.findByRole('button', { name: 'Account' }))
}

describe('UserProfileMenu', () => {
  it.each([
    [{ name: 'Name', displayName: 'Display Name' }, 'Name'],
    [{ displayName: 'Display Name' }, 'Display Name'],
    [{ given_name: 'Ada', family_name: 'Lovelace' }, 'Ada Lovelace'],
    [{ preferred_username: 'preferred', username: 'username' }, 'preferred'],
    [{ username: 'username' }, 'username'],
  ])('resolves the display name from claims in priority order', async (profile, expectedName) => {
    await renderMenu(profile)

    expect(await screen.findByText(expectedName)).toBeInTheDocument()
  })

  it('uses email and avatar claims', async () => {
    await renderMenu({
      name: 'Portal User',
      email: 'user@example.com',
      picture: 'https://example.com/u.png',
    })

    expect(await screen.findByText('user@example.com')).toBeInTheDocument()
    expect(screen.getByRole('img', { name: 'Portal User' })).toHaveAttribute(
      'src',
      'https://example.com/u.png',
    )
  })

  it('falls back to subject when email is unavailable', async () => {
    await renderMenu({ name: 'Portal User', sub: 'user-1' })

    expect(await screen.findByText('user-1')).toBeInTheDocument()
  })

  it('shows translated fallbacks when profile claims are unavailable', async () => {
    await renderMenu()

    expect(await screen.findByText('Unknown user')).toBeInTheDocument()
    expect(screen.getByText('No email available')).toBeInTheDocument()
  })

  it('renders untrusted profile claims as text rather than executable markup', async () => {
    const payload = '<img src=x onerror=alert(1)>'
    await renderMenu({ name: payload, email: '<script>alert(1)</script>' })

    expect(await screen.findByText(payload)).toBeInTheDocument()
    expect(screen.getByText('<script>alert(1)</script>')).toBeInTheDocument()
    expect(document.querySelector('script')).not.toBeInTheDocument()
    expect(document.querySelector('img[src="x"]')).not.toBeInTheDocument()
  })

  it('shows a translated logout error and allows retry', async () => {
    await renderMenu({ name: 'Portal User', email: 'user@example.com' })
    authMocks.logout.mockRejectedValueOnce(new Error('logout failed')).mockResolvedValueOnce()

    fireEvent.click(screen.getByRole('menuitem', { name: 'Sign out' }))

    const alert = await screen.findByRole('alert')
    expect(alert).toHaveTextContent('Unable to sign out. Please try again.')
    expect(authMocks.logout).toHaveBeenCalledOnce()

    fireEvent.click(screen.getByRole('button', { name: 'Try again' }))

    await waitFor(() => expect(authMocks.logout).toHaveBeenCalledTimes(2))
    await waitFor(() => expect(screen.queryByRole('alert')).not.toBeInTheDocument())
  })

  it('keeps the profile on screen while a sign-out is in flight', async () => {
    let completeLogout: (() => void) | undefined
    authMocks.getUserProfile.mockResolvedValue({
      name: 'Portal User',
      email: 'user@example.com',
    })
    authMocks.logout.mockReturnValue(
      new Promise<void>((resolve) => {
        completeLogout = resolve
      }),
    )
    renderComponent()

    fireEvent.click(await screen.findByRole('button', { name: 'Account' }))
    expect(await screen.findByText('Portal User')).toBeInTheDocument()
    fireEvent.click(screen.getByRole('menuitem', { name: 'Sign out' }))

    // The claims live in component state, so a pending sign-out must not blank
    // the menu while the browser is still on the page.
    fireEvent.click(screen.getByRole('button', { name: 'Account' }))
    expect(screen.getByText('Portal User')).toBeInTheDocument()
    expect(screen.queryByText('Unknown user')).not.toBeInTheDocument()

    completeLogout?.()
    await waitFor(() => expect(authMocks.logout).toHaveBeenCalledOnce())
  })

  it('hides account deletion from a session without the self-delete scope', async () => {
    await renderMenu({ name: 'Portal User', email: 'user@example.com' })

    expect(screen.queryByRole('menuitem', { name: 'Delete my account' })).not.toBeInTheDocument()
  })

  it('deletes the account after confirmation and tears the session down', async () => {
    accountMocks.deleteMyAccount.mockResolvedValue('deleted')
    authMocks.clearLocalSession.mockResolvedValue()
    // jsdom's location cannot be spied on in place; swap the whole object.
    const replace = vi.fn<(url: string) => void>()
    const { origin } = window.location
    vi.stubGlobal('location', { ...window.location, origin, replace })

    try {
      await renderMenu({ name: 'Portal User', email: 'user@example.com' }, [
        REQUIRED_SCOPES.ACCOUNT_SELF_DELETE,
      ])
      fireEvent.click(screen.getByRole('menuitem', { name: 'Delete my account' }))
      fireEvent.click(await screen.findByRole('button', { name: 'Delete my account' }))

      await waitFor(() => expect(accountMocks.deleteMyAccount).toHaveBeenCalledOnce())
      await waitFor(() => expect(authMocks.clearLocalSession).toHaveBeenCalledOnce())
      await waitFor(() =>
        expect(replace).toHaveBeenCalledWith(expect.stringContaining('/account-deleted')),
      )
    } finally {
      vi.unstubAllGlobals()
    }
  })

  it('shows a translated error and keeps the dialog open when deletion fails', async () => {
    accountMocks.deleteMyAccount.mockRejectedValue(new Error('deletion failed'))

    await renderMenu({ name: 'Portal User', email: 'user@example.com' }, [
      REQUIRED_SCOPES.ACCOUNT_SELF_DELETE,
    ])
    fireEvent.click(screen.getByRole('menuitem', { name: 'Delete my account' }))
    fireEvent.click(await screen.findByRole('button', { name: 'Delete my account' }))

    expect(
      await screen.findByText('Unable to delete your account. Please try again.'),
    ).toBeInTheDocument()
    expect(authMocks.clearLocalSession).not.toHaveBeenCalled()
  })

  /*
   * With an approval workflow on Delete User the account is not gone - the
   * Identity Server has only recorded a request - so the session must survive
   * and the user must not be told they have been deleted.
   */
  it('keeps the user signed in and says so when deletion needs approval', async () => {
    accountMocks.deleteMyAccount.mockResolvedValue('pendingApproval')
    authMocks.clearLocalSession.mockResolvedValue()
    const replace = vi.fn<(url: string) => void>()
    const { origin } = window.location
    vi.stubGlobal('location', { ...window.location, origin, replace })

    try {
      await renderMenu({ name: 'Portal User', email: 'user@example.com' }, [
        REQUIRED_SCOPES.ACCOUNT_SELF_DELETE,
      ])
      fireEvent.click(screen.getByRole('menuitem', { name: 'Delete my account' }))
      fireEvent.click(await screen.findByRole('button', { name: 'Delete my account' }))

      expect(
        await screen.findByText(
          'Your deletion request has been submitted for approval. Your account stays active until an administrator approves it.',
        ),
      ).toBeInTheDocument()
      expect(authMocks.clearLocalSession).not.toHaveBeenCalled()
      expect(replace).not.toHaveBeenCalled()
      expect(screen.queryByText('Your account has been deleted')).not.toBeInTheDocument()
    } finally {
      vi.unstubAllGlobals()
    }
  })

  it('explains that a deletion request is already awaiting approval on a 400', async () => {
    accountMocks.deleteMyAccount.mockRejectedValue(
      new APIError(
        400,
        'invalidValue',
        'There is a pending workflow already defined for the user.',
      ),
    )

    await renderMenu({ name: 'Portal User', email: 'user@example.com' }, [
      REQUIRED_SCOPES.ACCOUNT_SELF_DELETE,
    ])
    fireEvent.click(screen.getByRole('menuitem', { name: 'Delete my account' }))
    fireEvent.click(await screen.findByRole('button', { name: 'Delete my account' }))

    expect(
      await screen.findByText('Your account already has a deletion request awaiting approval.'),
    ).toBeInTheDocument()
    expect(authMocks.clearLocalSession).not.toHaveBeenCalled()
  })
})
