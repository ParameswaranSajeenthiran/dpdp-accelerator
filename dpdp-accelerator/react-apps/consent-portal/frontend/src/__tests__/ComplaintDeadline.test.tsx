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

import { cleanup, render, screen } from '@testing-library/react'
import { OxygenTheme, OxygenUIThemeProvider } from '@wso2/oxygen-ui'
import { I18nextProvider } from 'react-i18next'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import ComplaintDeadline from '../features/complaints/components/ComplaintDeadline'
import i18n from '../i18n/i18n'
import type { ComplaintStatus } from '../types/complaint'

const NOW = new Date(2026, 2, 9, 12, 0).getTime()
const DAY_IN_MS = 24 * 60 * 60 * 1000
const SUBMITTED_AT = NOW - 25 * DAY_IN_MS

function renderDeadline(
  audience: 'dataPrincipal' | 'officer',
  status: ComplaintStatus,
  statutoryDueDate: number,
): void {
  render(
    <I18nextProvider i18n={i18n}>
      <OxygenUIThemeProvider theme={OxygenTheme}>
        <ComplaintDeadline
          submittedAt={SUBMITTED_AT}
          statutoryDueDate={statutoryDueDate}
          status={status}
          audience={audience}
        />
      </OxygenUIThemeProvider>
    </I18nextProvider>,
  )
}

describe('ComplaintDeadline', () => {
  beforeEach(() => {
    vi.useFakeTimers({ toFake: ['Date'] })
    vi.setSystemTime(NOW)
  })

  afterEach(() => {
    cleanup()
    vi.useRealTimers()
  })

  it('explains the statutory period to the data principal in plain text', () => {
    renderDeadline('dataPrincipal', 'OPEN', SUBMITTED_AT + 30 * DAY_IN_MS)

    expect(screen.getByText('Due date')).toBeInTheDocument()
    expect(screen.getByText('5 days left')).toBeInTheDocument()
    expect(
      screen.getByText(
        'The organisation must resolve your complaint within 30 days of submitting it.',
      ),
    ).toBeInTheDocument()
  })

  it('points the data principal to escalation once the deadline has passed', () => {
    renderDeadline('dataPrincipal', 'IN_PROGRESS', NOW - 2 * DAY_IN_MS)

    expect(screen.getByText('2 days overdue')).toBeInTheDocument()
    expect(
      screen.getByText(/escalate your complaint to the Data Protection Board/),
    ).toBeInTheDocument()
  })

  it('keeps the officer view to the label and badge until the case is overdue', () => {
    renderDeadline('officer', 'OPEN', SUBMITTED_AT + 30 * DAY_IN_MS)

    expect(screen.getByText('Statutory deadline')).toBeInTheDocument()
    expect(screen.queryByText(/SLA breach/)).not.toBeInTheDocument()

    cleanup()
    renderDeadline('officer', 'OPEN', NOW - DAY_IN_MS)

    expect(screen.getByText('Overdue: this case counts as an SLA breach.')).toBeInTheDocument()
  })

  it('shows neither a badge nor helper text once resolved', () => {
    renderDeadline('dataPrincipal', 'RESOLVED', NOW - 2 * DAY_IN_MS)

    expect(screen.queryByText(/overdue/)).not.toBeInTheDocument()
    expect(screen.queryByText(/must resolve/)).not.toBeInTheDocument()
  })
})
