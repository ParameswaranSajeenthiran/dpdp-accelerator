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
import { afterEach, describe, expect, it, vi } from 'vitest'
import { OxygenTheme, OxygenUIThemeProvider } from '@wso2/oxygen-ui'
import i18n from '../i18n/i18n'
import ComplaintReplyComposer from '../features/complaints/components/ComplaintReplyComposer'

function renderWithProviders(component: React.JSX.Element): void {
  render(
    <I18nextProvider i18n={i18n}>
      <OxygenUIThemeProvider theme={OxygenTheme}>{component}</OxygenUIThemeProvider>
    </I18nextProvider>,
  )
}

afterEach(() => {
  cleanup()
})

/** The file input is `hidden` - not reachable via role queries, same as a real user's click on "Attach". */
function getFileInput(): HTMLInputElement {
  const input = document.querySelector('input[type="file"]')
  if (!input) {
    throw new Error('Expected a hidden file input to be rendered.')
  }
  return input as HTMLInputElement
}

function selectFiles(input: HTMLInputElement, files: File[]): void {
  Object.defineProperty(input, 'files', { value: files, configurable: true })
  fireEvent.change(input)
}

/**
 * Only one attachment is allowed at a time: the @asgardeo/auth-spa web worker http-client
 * mangles multiple FormData entries under the same field name (see the reverted patch and
 * https://github.com/wso2/dpdp-accelerator/issues/TODO), so ComplaintReplyComposer.tsx reads
 * only the first file of any selection and disables "Attach" while a file is staged. These
 * tests exercise the real rendered component to pin that behaviour down as a regression guard.
 */
describe('ComplaintReplyComposer single attachment limit', () => {
  it('replaces the staged file when a new one is attached in a separate interaction', () => {
    const onSend = vi.fn()
    renderWithProviders(
      <ComplaintReplyComposer
        canPostInternalNote={false}
        statusOptions={[]}
        getStatusLabel={() => ''}
        onSend={onSend}
      />,
    )

    const fileInput = getFileInput()
    const fileA = new File(['a-content'], 'a.png', { type: 'image/png' })
    const fileB = new File(['b-content'], 'b.pdf', { type: 'application/pdf' })

    selectFiles(fileInput, [fileA])
    expect(screen.getByText('a.png')).toBeInTheDocument()

    selectFiles(fileInput, [fileB])
    expect(screen.getByText('b.pdf')).toBeInTheDocument()
    expect(screen.queryByText('a.png')).not.toBeInTheDocument()

    fireEvent.change(screen.getByRole('textbox'), { target: { value: 'here is my evidence' } })
    fireEvent.click(screen.getByRole('button', { name: 'Send' }))

    expect(onSend).toHaveBeenCalledTimes(1)
    const [, sentFiles] = onSend.mock.calls[0] as [string, File[]]
    expect(sentFiles.map((file) => file.name)).toEqual(['b.pdf'])
  })

  it('keeps only the first file when multiple files are selected in one multi-select interaction', () => {
    const onSend = vi.fn()
    renderWithProviders(
      <ComplaintReplyComposer
        canPostInternalNote={false}
        statusOptions={[]}
        getStatusLabel={() => ''}
        onSend={onSend}
      />,
    )

    const fileInput = getFileInput()
    const fileA = new File(['a-content'], 'a.png', { type: 'image/png' })
    const fileB = new File(['b-content'], 'b.pdf', { type: 'application/pdf' })

    selectFiles(fileInput, [fileA, fileB])

    fireEvent.change(screen.getByRole('textbox'), { target: { value: 'here is my evidence' } })
    fireEvent.click(screen.getByRole('button', { name: 'Send' }))

    expect(onSend).toHaveBeenCalledTimes(1)
    const [, sentFiles] = onSend.mock.calls[0] as [string, File[]]
    expect(sentFiles.map((file) => file.name)).toEqual(['a.png'])
  })

  it('disables "Send" while isSending is true, even with a non-empty draft', () => {
    renderWithProviders(
      <ComplaintReplyComposer
        canPostInternalNote={false}
        statusOptions={[]}
        getStatusLabel={() => ''}
        onSend={vi.fn()}
        isSending
      />,
    )

    fireEvent.change(screen.getByRole('textbox'), { target: { value: 'here is my evidence' } })

    expect(screen.getByRole('button', { name: 'Send' })).toBeDisabled()
  })

  it('disables "Attach" while a file is staged and re-enables it once removed', () => {
    renderWithProviders(
      <ComplaintReplyComposer
        canPostInternalNote={false}
        statusOptions={[]}
        getStatusLabel={() => ''}
        onSend={vi.fn()}
      />,
    )

    const fileInput = getFileInput()
    const fileA = new File(['a-content'], 'a.png', { type: 'image/png' })

    selectFiles(fileInput, [fileA])

    const attachButton = screen.getByRole('button', { name: 'Attach' })
    expect(attachButton).toBeDisabled()

    fireEvent.click(screen.getByRole('button', { name: 'Remove attachment' }))

    expect(attachButton).toBeEnabled()
  })
})
