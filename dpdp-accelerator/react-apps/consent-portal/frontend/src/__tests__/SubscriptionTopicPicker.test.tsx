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
import { afterEach, expect, it, vi } from 'vitest'
import SubscriptionTopicPicker from '../features/events/components/SubscriptionTopicPicker'
import SubscriptionTopicChips from '../features/events/components/SubscriptionTopicChips'
import SubscriptionTopicsSection from '../features/events/components/SubscriptionTopicsSection'
import { collectMatchingTopics } from '../features/events/hooks/useSubscriptionTopicPicker'
import { getTopicCategory } from '../features/events/utils/topicCategory'
import i18n from '../i18n/i18n'

const api = vi.hoisted(() => ({ fetchTopics: vi.fn() }))
vi.mock('../features/events/api/topicsApi', () => api)

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

function Picker(): React.JSX.Element {
  const [selected, setSelected] = useState<string[]>([])
  return <SubscriptionTopicPicker selected={selected} onChange={setSelected} />
}

it('clears selection when category changes and allows chip deletion across categories', async () => {
  api.fetchTopics.mockResolvedValue({
    items: [
      { topicId: '1', name: 'consent.alpha', status: 'ACTIVE', initiatedBy: 'SYSTEM' },
      { topicId: '2', name: 'user.beta', status: 'ACTIVE', initiatedBy: 'SYSTEM' },
      { topicId: '3', name: 'custom.gamma', status: 'ACTIVE', initiatedBy: 'USER' },
    ],
    total: 3,
  })

  mount(<Picker />)

  // Autocomplete is disabled until a category is chosen
  expect(screen.getByPlaceholderText('Select a category first to choose topics')).toBeDisabled()

  // Select "Consent Topics" category
  fireEvent.mouseDown(screen.getByRole('combobox', { name: /topic category/i }))
  fireEvent.click(await screen.findByRole('option', { name: 'Consent Topics' }))

  const input = screen.getByPlaceholderText('Search topics by name')
  expect(input).toBeEnabled()

  // Open Autocomplete options and pick consent.alpha
  fireEvent.focus(input)
  fireEvent.keyDown(input, { key: 'ArrowDown' })
  const optionAlpha = await screen.findByRole('option', { name: 'consent.alpha' })
  fireEvent.click(optionAlpha)
  expect(screen.getByText('consent.alpha')).toBeInTheDocument()

  // Switch category to "User Topics" -> selection must be cleared because mixing is not allowed
  fireEvent.mouseDown(screen.getByRole('combobox', { name: /topic category/i }))
  fireEvent.click(await screen.findByRole('option', { name: 'User Topics' }))

  expect(screen.queryByText('consent.alpha')).not.toBeInTheDocument()

  // Open Autocomplete and pick user.beta
  fireEvent.focus(input)
  fireEvent.keyDown(input, { key: 'ArrowDown' })
  const optionBeta = await screen.findByRole('option', { name: 'user.beta' })
  fireEvent.click(optionBeta)
  expect(screen.getByText('user.beta')).toBeInTheDocument()

  // Switch category to "Custom Topics" -> selection must be cleared
  fireEvent.mouseDown(screen.getByRole('combobox', { name: /topic category/i }))
  fireEvent.click(await screen.findByRole('option', { name: 'Custom Topics' }))

  expect(screen.queryByText('user.beta')).not.toBeInTheDocument()

  // Open Autocomplete and pick custom.gamma
  fireEvent.focus(input)
  fireEvent.keyDown(input, { key: 'ArrowDown' })
  const optionGamma = await screen.findByRole('option', { name: 'custom.gamma' })
  fireEvent.click(optionGamma)
  expect(screen.getByText('custom.gamma')).toBeInTheDocument()

  // Delete custom.gamma via its chip delete icon
  const deleteIcons = document.querySelectorAll('.MuiChip-deleteIcon')
  expect(deleteIcons.length).toBe(1)
  fireEvent.click(deleteIcons[0])
  expect(screen.queryByText('custom.gamma')).not.toBeInTheDocument()
})

it('enforces the 100 topic cap and allows chip deletion', async () => {
  api.fetchTopics.mockResolvedValue({ items: [], total: 0 })
  const onChange = vi.fn()

  mount(
    <SubscriptionTopicPicker
      selected={Array.from({ length: 100 }, (_, index) => `consent.topic-${index}`)}
      onChange={onChange}
    />,
  )

  // Chip delete button exists and calls onChange without that item
  const deleteButtons = document.querySelectorAll('.MuiChip-deleteIcon')
  expect(deleteButtons.length).toBe(100)
  fireEvent.click(deleteButtons[0])
  expect(onChange).toHaveBeenCalled()
})

it('shows a retryable error on fetch failure', async () => {
  api.fetchTopics.mockRejectedValueOnce(new Error('offline'))
  mount(<Picker />)

  fireEvent.mouseDown(screen.getByRole('combobox', { name: /topic category/i }))
  fireEvent.click(await screen.findByRole('option', { name: 'Consent Topics' }))

  expect(await screen.findByRole('alert')).toHaveTextContent('Could not load topics')
})

it('renders compact chips and searches associated topics in SubscriptionTopicsSection', () => {
  mount(
    <>
      <SubscriptionTopicChips topics={['alpha', 'beta', 'gamma']} />
      <SubscriptionTopicsSection topics={['delta', 'epsilon']} />
    </>,
  )

  expect(screen.queryByText('gamma')).not.toBeInTheDocument()
  fireEvent.click(screen.getByRole('button', { name: '+1 more' }))
  expect(screen.getByText('gamma')).toBeInTheDocument()

  // SubscriptionTopicsSection is non-collapsible (always expanded)
  expect(screen.getByText('delta')).toBeInTheDocument()
  expect(screen.getByText('epsilon')).toBeInTheDocument()

  fireEvent.change(screen.getByLabelText('Search associated topics'), {
    target: { value: 'epsilon' },
  })
  expect(screen.queryByText('delta')).not.toBeInTheDocument()
  expect(screen.getByText('epsilon')).toBeInTheDocument()
})

it('selects matching topics and keeps existing selections', async () => {
  api.fetchTopics.mockResolvedValueOnce({
    items: [{ name: 'consent.alpha' }, { name: 'consent.beta' }],
    total: 2,
  })
  await expect(collectMatchingTopics('consent', ['existing', 'consent.alpha'])).resolves.toEqual([
    'existing',
    'consent.alpha',
    'consent.beta',
  ])
  expect(api.fetchTopics).toHaveBeenLastCalledWith({
    status: 'ACTIVE',
    search: 'consent',
    limit: 100,
    offset: 0,
  })
})

it('rejects select all above the cap instead of truncating', async () => {
  api.fetchTopics.mockResolvedValue({ items: [{ name: 'alpha' }], total: 101 })
  await expect(collectMatchingTopics('', [])).rejects.toThrow(RangeError)

  api.fetchTopics.mockResolvedValue({ items: [{ name: 'extra' }], total: 1 })
  await expect(
    collectMatchingTopics(
      '',
      Array.from({ length: 100 }, (_, index) => `topic-${index}`),
    ),
  ).rejects.toThrow(RangeError)
})

it('categorizes topics by checking SYSTEM status first before prefix', () => {
  // System topics check prefix
  expect(getTopicCategory({ name: 'consent.update', initiatedBy: 'SYSTEM' })).toBe('consent')
  expect(getTopicCategory({ name: 'consent.custom', initiatedBy: 'SYSTEM' })).toBe('consent')
  expect(getTopicCategory({ name: 'user.data.change', initiatedBy: 'SYSTEM' })).toBe('user')
  expect(getTopicCategory({ name: 'user.account.delete', initiatedBy: 'SYSTEM' })).toBe('user')
  expect(getTopicCategory({ name: 'other.system.topic', initiatedBy: 'SYSTEM' })).toBe('custom')

  // Non-system topics are ALWAYS custom, even if they have consent. or user. in name
  expect(getTopicCategory({ name: 'consent.custom', initiatedBy: 'USER' })).toBe('custom')
  expect(getTopicCategory({ name: 'user.custom', initiatedBy: 'USER' })).toBe('custom')
  expect(getTopicCategory({ name: 'orders.placed', initiatedBy: 'USER' })).toBe('custom')
  expect(getTopicCategory({ name: 'payment.completed' })).toBe('custom')
})

it('does not report query pending state as busy through onBusyChange', () => {
  api.fetchTopics.mockReturnValue(new Promise(() => {}))
  const onBusyChange = vi.fn()

  mount(
    <SubscriptionTopicPicker
      selected={[]}
      onChange={vi.fn()}
      onBusyChange={onBusyChange}
    />,
  )

  expect(onBusyChange).toHaveBeenCalledWith(false)
  expect(onBusyChange).not.toHaveBeenCalledWith(true)
})
