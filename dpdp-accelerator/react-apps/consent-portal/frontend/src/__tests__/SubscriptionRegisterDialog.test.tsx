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
  it.each(['user.account.delete', 'user.data.change'])(
    'hides consent-purpose controls and submits the all filter for %s',
    async (topic) => {
      topicsApi.fetchTopics.mockResolvedValue({
        items: [{ topicId: 'topic-1', name: topic, status: 'ACTIVE' }],
        total: 1,
      })
      const onSubmit = vi.fn()

      render(
        <I18nextProvider i18n={i18n}>
          <OxygenUIThemeProvider theme={OxygenTheme}>
            <SubscriptionRegisterDialog
              open
              loading={false}
              onClose={vi.fn()}
              onSubmit={onSubmit}
            />
          </OxygenUIThemeProvider>
        </I18nextProvider>,
      )

      await screen.findByText(topic)
      expect(screen.queryByLabelText('Consent Purpose Filter Mode')).not.toBeInTheDocument()
      expect(screen.queryByLabelText('Consent Purposes (comma-separated)')).not.toBeInTheDocument()

      fireEvent.change(screen.getByRole('textbox', { name: /Webhook Callback URL/ }), {
        target: { value: 'https://receiver.example/callback' },
      })
      fireEvent.click(screen.getByRole('button', { name: 'Register Subscription' }))

      await waitFor(() => expect(onSubmit).toHaveBeenCalledTimes(1))
      expect(onSubmit).toHaveBeenCalledWith(
        expect.objectContaining({
          topic,
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
      <I18nextProvider i18n={i18n}>
        <OxygenUIThemeProvider theme={OxygenTheme}>
          <SubscriptionRegisterDialog open loading={false} onClose={vi.fn()} onSubmit={vi.fn()} />
        </OxygenUIThemeProvider>
      </I18nextProvider>,
    )

    await screen.findByText('consent.status.update')
    expect(screen.getByLabelText('Consent Purpose Filter Mode')).toBeInTheDocument()
  })
})
