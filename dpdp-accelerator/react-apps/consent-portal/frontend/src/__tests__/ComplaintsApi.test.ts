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

import { afterEach, describe, expect, it, vi } from 'vitest'
import {
  uploadManagedComplaintAttachments,
  uploadMyComplaintAttachments,
} from '../features/complaints/api/complaintsApi'

const transport = vi.hoisted(() => ({
  httpRequest: vi.fn(),
  login: vi.fn(),
}))

vi.mock('../utils/authClient', () => ({
  httpRequest: transport.httpRequest,
  isAuthEnabled: () => true,
  login: transport.login,
}))

afterEach(() => {
  vi.clearAllMocks()
})

function respondWith(payload: unknown, status = 201): void {
  transport.httpRequest.mockResolvedValue({ status, data: payload })
}

/** The FormData the api handed to the auth SDK's httpRequest as `data`. */
function sentFormData(): FormData {
  const call = transport.httpRequest.mock.calls[0] as [{ data: FormData }] | undefined
  if (!call) {
    throw new Error('httpRequest was never called.')
  }
  return call[0].data
}

describe('complaintsApi attachment uploads', () => {
  it('uploadManagedComplaintAttachments appends every file under repeated "file" fields', async () => {
    respondWith([])
    const fileA = new File(['a-content'], 'a.png', { type: 'image/png' })
    const fileB = new File(['b-content'], 'b.pdf', { type: 'application/pdf' })

    await uploadManagedComplaintAttachments('complaint-1', [fileA, fileB], true)

    const formData = sentFormData()
    const filesSent = formData.getAll('file') as File[]
    expect(filesSent.map((file) => file.name)).toEqual(['a.png', 'b.pdf'])
    expect(formData.get('isPublic')).toBe('true')
  })

  it('uploadMyComplaintAttachments appends every file under repeated "file" fields', async () => {
    respondWith([])
    const fileA = new File(['a-content'], 'a.png', { type: 'image/png' })
    const fileB = new File(['b-content'], 'b.pdf', { type: 'application/pdf' })
    const fileC = new File(['c-content'], 'c.jpeg', { type: 'image/jpeg' })

    await uploadMyComplaintAttachments('complaint-1', [fileA, fileB, fileC])

    const formData = sentFormData()
    const filesSent = formData.getAll('file') as File[]
    expect(filesSent.map((file) => file.name)).toEqual(['a.png', 'b.pdf', 'c.jpeg'])
  })

  it('sends only one HTTP request for a multi-file upload, not one request per file', async () => {
    respondWith([])
    const fileA = new File(['a-content'], 'a.png', { type: 'image/png' })
    const fileB = new File(['b-content'], 'b.pdf', { type: 'application/pdf' })

    await uploadManagedComplaintAttachments('complaint-1', [fileA, fileB], true)

    expect(transport.httpRequest).toHaveBeenCalledTimes(1)
  })
})
