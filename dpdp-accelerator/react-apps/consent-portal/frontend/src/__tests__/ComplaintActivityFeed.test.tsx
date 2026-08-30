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
import { cleanup, render, screen } from '@testing-library/react'
import { OxygenTheme, OxygenUIThemeProvider } from '@wso2/oxygen-ui'
import { I18nextProvider } from 'react-i18next'
import { afterEach, describe, expect, it } from 'vitest'
import ComplaintActivityFeed from '../features/complaints/components/ComplaintActivityFeed'
import i18n from '../i18n/i18n'
import type { ComplaintAttachment, ComplaintTimelineEntry } from '../types/complaint'

afterEach(() => {
  cleanup()
})

function renderFeed(entries: ComplaintTimelineEntry[]): void {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } })

  render(
    <QueryClientProvider client={queryClient}>
      <I18nextProvider i18n={i18n}>
        <OxygenUIThemeProvider theme={OxygenTheme}>
          <ComplaintActivityFeed
            complaintId="complaint-1"
            entries={entries}
            viewerRole="DataPrincipal"
          />
        </OxygenUIThemeProvider>
      </I18nextProvider>
    </QueryClientProvider>,
  )
}

function buildAttachment(overrides: Partial<ComplaintAttachment> = {}): ComplaintAttachment {
  return { id: 'attachment-1', fileName: 'evidence.pdf', fileSizeLabel: '12 KB', ...overrides }
}

function buildEntry(
  attachments: ComplaintAttachment[],
  overrides: Partial<ComplaintTimelineEntry> = {},
): ComplaintTimelineEntry {
  return {
    id: 'entry-1',
    type: 'communication',
    actorName: 'Jane Doe',
    actorRole: 'DataPrincipal',
    message: 'Here is my evidence.',
    timestamp: 1785657600000,
    visibility: 'shared',
    attachments,
    ...overrides,
  }
}

describe('ComplaintActivityFeed attachment count', () => {
  it('shows the singular sentence for a single attachment', () => {
    renderFeed([buildEntry([buildAttachment()])])

    expect(screen.getByText('Attached a new file')).toBeInTheDocument()
  })

  it('shows the plural sentence with count for multiple attachments', () => {
    renderFeed([
      buildEntry([
        buildAttachment({ id: 'attachment-1', fileName: 'evidence.pdf' }),
        buildAttachment({ id: 'attachment-2', fileName: 'evidence.png' }),
      ]),
    ])

    expect(screen.getByText('Attached 2 new files')).toBeInTheDocument()
  })

  it('shows no attachment sentence when the entry has no attachments', () => {
    renderFeed([buildEntry([])])

    expect(screen.queryByText(/attached/i)).not.toBeInTheDocument()
  })
})
