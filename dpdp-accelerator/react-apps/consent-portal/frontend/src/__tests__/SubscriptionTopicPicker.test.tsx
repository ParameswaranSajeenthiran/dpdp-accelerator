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
import { OxygenTheme, OxygenUIThemeProvider } from '@wso2/oxygen-ui'
import { useState } from 'react'
import { I18nextProvider } from 'react-i18next'
import { afterEach, expect, it, vi } from 'vitest'
import SubscriptionTopicPicker from '../features/events/components/SubscriptionTopicPicker'
import SubscriptionTopicChips from '../features/events/components/SubscriptionTopicChips'
import SubscriptionTopicsSection from '../features/events/components/SubscriptionTopicsSection'
import { collectMatchingTopics } from '../features/events/hooks/useSubscriptionTopicPicker'
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
it('preserves selection across server searches and supports clearing', async () => {
  api.fetchTopics.mockImplementation(({ search }: { search?: string }) =>
    Promise.resolve({
      items: [{ topicId: search || 'a', name: search || 'alpha' }],
      total: 1,
    }),
  )
  mount(<Picker />)
  fireEvent.click(await screen.findByRole('checkbox', { name: 'alpha' }))
  fireEvent.change(screen.getByLabelText('Search topics by name'), { target: { value: 'beta' } })
  fireEvent.click(await screen.findByRole('checkbox', { name: 'beta' }))
  expect(screen.getByRole('status')).toHaveTextContent('Selected topics')
  expect(screen.getByRole('status')).toHaveTextContent('2')
  fireEvent.change(screen.getByLabelText('Search topics by name'), { target: { value: '' } })
  await waitFor(() => expect(screen.getByRole('checkbox', { name: 'alpha' })).toBeChecked())
  fireEvent.click(screen.getByRole('button', { name: 'Clear' }))
  expect(screen.getByRole('status')).toHaveTextContent('0')
})
it('enforces the 100 topic cap without disabling removal', async () => {
  api.fetchTopics.mockResolvedValue({ items: [{ name: 'topic-0' }, { name: 'extra' }], total: 2 })
  mount(
    <SubscriptionTopicPicker
      selected={Array.from({ length: 100 }, (_, index) => `topic-${index}`)}
      onChange={vi.fn()}
    />,
  )
  expect(await screen.findByRole('checkbox', { name: 'extra' })).toBeDisabled()
  expect(screen.getByRole('checkbox', { name: 'topic-0' })).toBeEnabled()
})
it('shows a retryable error and distinguishes an empty catalog', async () => {
  api.fetchTopics
    .mockRejectedValueOnce(new Error('offline'))
    .mockResolvedValue({ items: [], total: 0 })
  mount(<Picker />)
  expect(await screen.findByRole('alert')).toHaveTextContent('Could not load topics')
  fireEvent.click(screen.getByRole('button', { name: 'Retry' }))
  expect(await screen.findByText('No active topics available.')).toBeInTheDocument()
})
it('expands compact chips and searches associated topics', () => {
  mount(
    <>
      <SubscriptionTopicChips topics={['alpha', 'beta', 'gamma']} />
      <SubscriptionTopicsSection topics={['delta', 'epsilon']} />
    </>,
  )
  expect(screen.queryByText('gamma')).not.toBeInTheDocument()
  fireEvent.click(screen.getByRole('button', { name: '+1 more' }))
  expect(screen.getByText('gamma')).toBeInTheDocument()
  fireEvent.click(screen.getByRole('button', { name: 'Subscribed Topics' }))
  fireEvent.change(screen.getByLabelText('Search associated topics'), {
    target: { value: 'epsilon' },
  })
  expect(screen.queryByText('delta')).not.toBeInTheDocument()
  expect(screen.getByText('epsilon')).toBeInTheDocument()
})

it('selects matching topics across all pages and keeps existing selections', async () => {
  api.fetchTopics
    .mockResolvedValueOnce({ items: [{ name: 'alpha' }], total: 2 })
    .mockResolvedValueOnce({ items: [{ name: 'beta' }], total: 2 })
  await expect(collectMatchingTopics('consent', ['existing', 'alpha'])).resolves.toEqual([
    'existing',
    'alpha',
    'beta',
  ])
  expect(api.fetchTopics).toHaveBeenLastCalledWith({
    status: 'ACTIVE',
    search: 'consent',
    limit: 100,
    offset: 1,
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
it('does not return partial results when a later page fails', async () => {
  api.fetchTopics
    .mockResolvedValueOnce({ items: [{ name: 'alpha' }], total: 2 })
    .mockRejectedValueOnce(new Error('offline'))
  const selected = ['existing']
  await expect(collectMatchingTopics('', selected)).rejects.toThrow('offline')
  expect(selected).toEqual(['existing'])
})
it('select all button selects matches and reports overflow without losing selection', async () => {
  api.fetchTopics.mockResolvedValue({ items: [{ name: 'alpha' }, { name: 'beta' }], total: 2 })
  mount(<Picker />)
  await screen.findByRole('checkbox', { name: 'alpha' })
  fireEvent.click(screen.getByRole('button', { name: 'Select all' }))
  await waitFor(() => {
    expect(screen.getByRole('status')).toHaveTextContent('Selected topics')
    expect(screen.getByRole('status')).toHaveTextContent('2')
  })
  api.fetchTopics.mockResolvedValue({ items: [{ name: 'extra' }], total: 101 })
  fireEvent.click(screen.getByRole('button', { name: 'Select all' }))
  expect(await screen.findByRole('alert')).toHaveTextContent('100-topic limit')
  expect(screen.getByRole('status')).toHaveTextContent('2')
})
