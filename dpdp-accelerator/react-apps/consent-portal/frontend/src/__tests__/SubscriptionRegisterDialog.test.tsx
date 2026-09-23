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
import { I18nextProvider } from 'react-i18next'
import { afterEach, describe, expect, it, vi } from 'vitest'
import SubscriptionRegisterDialog from '../features/events/components/SubscriptionRegisterDialog'
import i18n from '../i18n/i18n'

const topicsApi = vi.hoisted(() => ({
  fetchTopics: vi.fn(),
}))

vi.mock('../features/events/api/topicsApi', () => topicsApi)

afterEach(() => {
  cleanup()
  vi.clearAllMocks()
})

describe('SubscriptionRegisterDialog', () => {
  it('loads another topic page and submits the selected topics in one request', async () => {
    topicsApi.fetchTopics
      .mockResolvedValueOnce({ items: [{ name: 'consent.update' }], total: 2 })
      .mockResolvedValueOnce({ items: [{ name: 'consent.revoke' }], total: 2 })
      .mockResolvedValue({
        items: [{ name: 'consent.update' }, { name: 'consent.revoke' }],
        total: 2,
      })
    const onSubmit = vi.fn()
    render(
      <QueryClientProvider
        client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}
      >
        <I18nextProvider i18n={i18n}>
          <OxygenUIThemeProvider theme={OxygenTheme}>
            <SubscriptionRegisterDialog
              open
              loading={false}
              onClose={vi.fn()}
              onSubmit={onSubmit}
            />
          </OxygenUIThemeProvider>
        </I18nextProvider>
      </QueryClientProvider>,
    )
    await screen.findByText('consent.update')
    expect(screen.getByRole('button', { name: 'Next' })).toBeDisabled()
    expect(screen.getByRole('button', { name: 'Cancel' })).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Back' })).not.toBeInTheDocument()
    fireEvent.click(screen.getByRole('checkbox', { name: 'consent.update' }))
    fireEvent.click(screen.getByRole('button', { name: 'Load more topics' }))
    fireEvent.click(await screen.findByRole('checkbox', { name: 'consent.revoke' }))
    fireEvent.click(screen.getByRole('button', { name: 'Next' }))
    fireEvent.change(screen.getByRole('textbox', { name: /Webhook Callback URL/ }), {
      target: { value: 'https://receiver.example/callback' },
    })
    expect(screen.queryByRole('button', { name: 'Cancel' })).not.toBeInTheDocument()
    expect(screen.queryByRole('checkbox')).not.toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Close registration' })).toBeInTheDocument()
    expect(onSubmit).not.toHaveBeenCalled()
    fireEvent.click(screen.getByRole('button', { name: 'Back' }))
    expect(screen.getByRole('status')).toHaveTextContent('Selected topics')
    expect(screen.getByRole('status')).toHaveTextContent('2')
    fireEvent.click(screen.getByRole('button', { name: 'Next' }))
    expect(screen.getByRole('textbox', { name: /Webhook Callback URL/ })).toHaveValue(
      'https://receiver.example/callback',
    )
    fireEvent.click(screen.getByRole('button', { name: 'Register Subscription' }))
    await waitFor(() => expect(onSubmit).toHaveBeenCalledTimes(1))
    expect(onSubmit).toHaveBeenCalledWith(
      expect.objectContaining({
        topics: ['consent.update', 'consent.revoke'],
      }),
    )
  })
  it.each(['user.account.delete', 'user.data.change'])(
    'hides consent-purpose controls and submits the all filter for %s',
    async (topic) => {
      topicsApi.fetchTopics.mockResolvedValue({
        items: [{ topicId: 'topic-1', name: topic, status: 'ACTIVE' }],
        total: 1,
      })
      const onSubmit = vi.fn()

      render(
        <QueryClientProvider
          client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}
        >
          <I18nextProvider i18n={i18n}>
            <OxygenUIThemeProvider theme={OxygenTheme}>
              <SubscriptionRegisterDialog
                open
                loading={false}
                onClose={vi.fn()}
                onSubmit={onSubmit}
              />
            </OxygenUIThemeProvider>
          </I18nextProvider>
        </QueryClientProvider>,
      )

      fireEvent.click(await screen.findByRole('checkbox', { name: topic }))
      fireEvent.click(screen.getByRole('button', { name: 'Next' }))
      expect(screen.queryByLabelText('Consent Purpose Filter Mode')).not.toBeInTheDocument()
      expect(screen.queryByLabelText('Consent Purposes (comma-separated)')).not.toBeInTheDocument()

      fireEvent.change(screen.getByRole('textbox', { name: /Webhook Callback URL/ }), {
        target: { value: 'https://receiver.example/callback' },
      })
      fireEvent.click(screen.getByRole('button', { name: 'Register Subscription' }))

      await waitFor(() => expect(onSubmit).toHaveBeenCalledTimes(1))
      expect(onSubmit).toHaveBeenCalledWith(
        expect.objectContaining({
          topics: [topic],
          filter: { type: 'all', purposes: undefined },
        }),
      )
    },
  )

  it('shows the consent-purpose filter for consent topics', async () => {
    topicsApi.fetchTopics.mockResolvedValue({
      items: [{ topicId: 'topic-1', name: 'consent.status.update', status: 'ACTIVE' }],
      total: 1,
    })

    render(
      <QueryClientProvider
        client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}
      >
        <I18nextProvider i18n={i18n}>
          <OxygenUIThemeProvider theme={OxygenTheme}>
            <SubscriptionRegisterDialog open loading={false} onClose={vi.fn()} onSubmit={vi.fn()} />
          </OxygenUIThemeProvider>
        </I18nextProvider>
      </QueryClientProvider>,
    )

    fireEvent.click(await screen.findByRole('checkbox', { name: 'consent.status.update' }))
    fireEvent.click(screen.getByRole('button', { name: 'Next' }))
    expect(screen.getByLabelText('Consent Purpose Filter Mode')).toBeInTheDocument()
  })
})
