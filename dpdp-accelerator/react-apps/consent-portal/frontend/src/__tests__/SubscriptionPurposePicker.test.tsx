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
import { OxygenTheme, OxygenUIThemeProvider } from '@wso2/oxygen-ui'
import { useState } from 'react'
import { I18nextProvider } from 'react-i18next'
import { afterEach, describe, expect, it, vi } from 'vitest'
import SubscriptionPurposePicker from '../features/events/components/SubscriptionPurposePicker'
import i18n from '../i18n/i18n'

const catalogApi = vi.hoisted(() => ({
  fetchPurposes: vi.fn(),
  buildPurposeFilter: vi.fn((name: string, _type: string) =>
    name ? `name co "${name}"` : undefined,
  ),
}))

vi.mock('../features/catalog/api/catalogApi', () => catalogApi)

afterEach(() => {
  cleanup()
  vi.resetAllMocks()
})

function mount(child: React.ReactNode): void {
  render(
    <QueryClientProvider
      client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}
    >
      <I18nextProvider i18n={i18n}>
        <OxygenUIThemeProvider theme={OxygenTheme}>{child}</OxygenUIThemeProvider>
      </I18nextProvider>
    </QueryClientProvider>,
  )
}

function PurposePickerWrapper({
  initialSelected = [],
  error,
}: {
  initialSelected?: string[]
  error?: string
}): React.JSX.Element {
  const [selected, setSelected] = useState<string[]>(initialSelected)
  return <SubscriptionPurposePicker selected={selected} onChange={setSelected} error={error} />
}

describe('SubscriptionPurposePicker', () => {
  it('renders purposes with pagination controls when options exceed page size', async () => {
    catalogApi.fetchPurposes.mockResolvedValue({
      totalResults: 7,
      links: [],
      Purposes: [
        { id: 'p1', name: 'PURPOSE_1', type: 'DEFAULT' },
        { id: 'p2', name: 'PURPOSE_2', type: 'DEFAULT' },
        { id: 'p3', name: 'PURPOSE_3', type: 'DEFAULT' },
        { id: 'p4', name: 'PURPOSE_4', type: 'DEFAULT' },
        { id: 'p5', name: 'PURPOSE_5', type: 'DEFAULT' },
        { id: 'p6', name: 'PURPOSE_6', type: 'DEFAULT' },
        { id: 'p7', name: 'PURPOSE_7', type: 'DEFAULT' },
      ],
    })

    mount(<PurposePickerWrapper />)

    const input = await screen.findByLabelText(/Consent Purposes/)
    fireEvent.focus(input)
    fireEvent.keyDown(input, { key: 'ArrowDown' })

    expect(await screen.findByRole('option', { name: /PURPOSE_1/ })).toBeInTheDocument()
    expect(screen.getByRole('option', { name: /PURPOSE_5/ })).toBeInTheDocument()
    expect(screen.queryByRole('option', { name: /PURPOSE_6/ })).not.toBeInTheDocument()

    expect(screen.getByText('Showing 1–5 of 7')).toBeInTheDocument()

    // Click Next
    fireEvent.click(screen.getByRole('button', { name: 'Next' }))
    expect(await screen.findByRole('option', { name: /PURPOSE_6/ })).toBeInTheDocument()
    expect(screen.getByRole('option', { name: /PURPOSE_7/ })).toBeInTheDocument()
    expect(screen.getByText('Showing 6–7 of 7')).toBeInTheDocument()
  })

  it('selects all purposes and clears all purposes', async () => {
    catalogApi.fetchPurposes.mockResolvedValue({
      totalResults: 2,
      links: [],
      Purposes: [
        { id: 'p1', name: 'MARKETING', type: 'DEFAULT' },
        { id: 'p2', name: 'ANALYTICS', type: 'DEFAULT' },
      ],
    })

    mount(<PurposePickerWrapper />)

    await screen.findByLabelText(/Consent Purposes/)

    const selectAllBtn = screen.getByRole('button', { name: 'Select all' })
    await screen.findByRole('combobox')
    const { waitFor } = await import('@testing-library/react')
    await waitFor(() => expect(selectAllBtn).toBeEnabled())
    fireEvent.click(selectAllBtn)

    expect(await screen.findByText(/MARKETING/)).toBeInTheDocument()
    expect(screen.getByText(/ANALYTICS/)).toBeInTheDocument()

    const clearBtn = screen.getByRole('button', { name: 'Clear' })
    fireEvent.click(clearBtn)

    expect(screen.queryByText(/MARKETING/)).not.toBeInTheDocument()
    expect(screen.queryByText(/ANALYTICS/)).not.toBeInTheDocument()
  })

  it('does not display default helper text and only displays error when provided', async () => {
    catalogApi.fetchPurposes.mockResolvedValue({
      totalResults: 0,
      links: [],
      Purposes: [],
    })

    mount(<PurposePickerWrapper error="At least one purpose is required" />)

    expect(screen.getByText('At least one purpose is required')).toBeInTheDocument()
    expect(
      screen.queryByText('Select one or more purposes to filter events.'),
    ).not.toBeInTheDocument()
  })
})
