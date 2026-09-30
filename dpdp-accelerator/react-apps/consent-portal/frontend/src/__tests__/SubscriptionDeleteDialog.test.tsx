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

import { cleanup, fireEvent, render, screen } from '@testing-library/react'
import { I18nextProvider } from 'react-i18next'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { OxygenTheme, OxygenUIThemeProvider } from '@wso2/oxygen-ui'
import i18n from '../i18n/i18n'
import SubscriptionDeleteDialog from '../features/events/components/SubscriptionDeleteDialog'
import type { SubscriptionRecord } from '../types/subscription'

function renderWithProviders(component: React.JSX.Element): void {
  render(
    <I18nextProvider i18n={i18n}>
      <OxygenUIThemeProvider theme={OxygenTheme}>{component}</OxygenUIThemeProvider>
    </I18nextProvider>,
  )
}

describe('SubscriptionDeleteDialog', () => {
  const mockSubscription: SubscriptionRecord = {
    subscriptionId: '4f0fd359-e420-44d5-92b6-5cfedbc402a3',
    name: 'Test Subscription',
    status: 'ACTIVE',
  }

  beforeEach(() => {
    vi.useFakeTimers()
  })

  afterEach(() => {
    cleanup()
    vi.runOnlyPendingTimers()
    vi.useRealTimers()
  })

  it('renders modal without consent ID caption and highlights subscription UUID in bold', () => {
    const onConfirm = vi.fn()
    const onClose = vi.fn()

    renderWithProviders(
      <SubscriptionDeleteDialog
        open
        subscription={mockSubscription}
        loading={false}
        onClose={onClose}
        onConfirm={onConfirm}
      />,
    )

    // Accessible dialog name is strictly the title (does not conflate description)
    const dialog = screen.getByRole('dialog', { name: 'Confirm Subscription Deletion' })
    expect(dialog).toBeInTheDocument()
    expect(
      screen.getByRole('heading', { level: 2, name: 'Confirm Subscription Deletion' }),
    ).toBeInTheDocument()
    expect(screen.queryByRole('heading', { level: 6 })).not.toBeInTheDocument()

    // Confirmation question is associated with the dialog via aria-describedby
    const describedById = dialog.getAttribute('aria-describedby')
    expect(describedById).toBeTruthy()
    expect(document.getElementById(describedById ?? '')).toHaveTextContent(
      /Are you sure you want to delete subscription/i,
    )

    // Confirm Subscription Deletion title is present
    expect(screen.getByText('Confirm Subscription Deletion')).toBeInTheDocument()

    // "Consent ID:" must NOT be rendered
    expect(screen.queryByText(/Consent ID/i)).not.toBeInTheDocument()

    // Subscription UUID is rendered and bolded
    const uuidElement = screen.getByText('4f0fd359-e420-44d5-92b6-5cfedbc402a3')
    expect(uuidElement).toBeInTheDocument()
    expect(uuidElement).toHaveStyle({ fontWeight: 700 })

    // Note says "to this subscriber." without "endpoint"
    expect(
      screen.getByText(
        'Deleting this subscription prevents new events from being dispatched to this subscriber.',
      ),
    ).toBeInTheDocument()

    // Confirm button triggers onConfirm
    fireEvent.click(screen.getByRole('button', { name: /delete subscription/i }))
    expect(onConfirm).toHaveBeenCalledTimes(1)

    // Cancel button triggers onClose
    fireEvent.click(screen.getByRole('button', { name: /cancel/i }))
    expect(onClose).toHaveBeenCalledTimes(1)
  })
})
