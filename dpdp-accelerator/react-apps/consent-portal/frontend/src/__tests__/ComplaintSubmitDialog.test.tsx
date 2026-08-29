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
import { I18nextProvider } from 'react-i18next'
import { afterEach, describe, expect, it, vi } from 'vitest'
import ComplaintSubmitDialog from '../features/complaints/components/ComplaintSubmitDialog'
import i18n from '../i18n/i18n'

const complaintsApi = vi.hoisted(() => ({
  listComplaintCategories: vi.fn(),
  createMyComplaint: vi.fn(),
  uploadMyComplaintAttachments: vi.fn(),
}))

vi.mock('../features/complaints/api/complaintsApi', () => complaintsApi)

complaintsApi.listComplaintCategories.mockResolvedValue({
  data: [{ category: 'DATA_ACCESS_REQUEST' }],
})

function renderDialog(): void {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } })

  render(
    <QueryClientProvider client={queryClient}>
      <I18nextProvider i18n={i18n}>
        <OxygenUIThemeProvider theme={OxygenTheme}>
          <ComplaintSubmitDialog open onClose={vi.fn()} onSubmitted={vi.fn()} />
        </OxygenUIThemeProvider>
      </I18nextProvider>
    </QueryClientProvider>,
  )
}

afterEach(() => {
  cleanup()
})

/** The file input is `hidden` - not reachable via role queries, same as a real user's click on "Upload files". */
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
 * Only one attachment is allowed at a time when filing a complaint: the @asgardeo/auth-spa web
 * worker http-client mangles multiple FormData entries under the same field name (see the
 * reverted patch and https://github.com/wso2/dpdp-accelerator/issues/TODO), so
 * ComplaintSubmitDialog.tsx reads only the first file of any selection and disables
 * "Upload files" while a file is staged.
 */
describe('ComplaintSubmitDialog single attachment limit', () => {
  it('keeps only the first file when multiple files are selected in one multi-select interaction', () => {
    renderDialog()

    const fileInput = getFileInput()
    const fileA = new File(['a-content'], 'a.png', { type: 'image/png' })
    const fileB = new File(['b-content'], 'b.pdf', { type: 'application/pdf' })

    selectFiles(fileInput, [fileA, fileB])

    expect(screen.getByText('a.png')).toBeInTheDocument()
    expect(screen.queryByText('b.pdf')).not.toBeInTheDocument()
  })

  it('replaces the staged file when a new one is attached in a separate interaction', () => {
    renderDialog()

    const fileInput = getFileInput()
    const fileA = new File(['a-content'], 'a.png', { type: 'image/png' })
    const fileB = new File(['b-content'], 'b.pdf', { type: 'application/pdf' })

    selectFiles(fileInput, [fileA])
    expect(screen.getByText('a.png')).toBeInTheDocument()

    selectFiles(fileInput, [fileB])
    expect(screen.getByText('b.pdf')).toBeInTheDocument()
    expect(screen.queryByText('a.png')).not.toBeInTheDocument()
  })

  it('disables "Upload files" while a file is staged and re-enables it once removed', () => {
    renderDialog()

    const fileInput = getFileInput()
    const fileA = new File(['a-content'], 'a.png', { type: 'image/png' })

    selectFiles(fileInput, [fileA])

    const uploadButton = screen.getByRole('button', { name: 'Upload files' })
    expect(uploadButton).toBeDisabled()

    fireEvent.click(screen.getByRole('button', { name: 'Remove attachment' }))

    expect(uploadButton).toBeEnabled()
  })
})
