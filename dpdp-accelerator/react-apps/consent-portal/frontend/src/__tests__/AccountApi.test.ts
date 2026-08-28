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
import { deleteMyAccount } from '../features/account/api/accountApi'

const clientMocks = vi.hoisted(() => ({
  apiRequestForStatus: vi.fn<() => Promise<number>>(),
}))

vi.mock('../utils/apiClient', () => clientMocks)

afterEach(() => {
  vi.clearAllMocks()
})

describe('account API', () => {
  it('deletes the signed-in user through the SCIM2 me endpoint', async () => {
    clientMocks.apiRequestForStatus.mockResolvedValue(204)

    await expect(deleteMyAccount()).resolves.toBe('deleted')
    expect(clientMocks.apiRequestForStatus).toHaveBeenCalledWith('/scim2/Me', { method: 'DELETE' })
  })

  /*
   * An approval workflow on Delete User turns the call into a request: the
   * Identity Server answers 202 and the account is still there. Reporting that
   * as a deletion is what would sign the user out of an account they still have.
   */
  it('reports a 202 as awaiting approval rather than as a deletion', async () => {
    clientMocks.apiRequestForStatus.mockResolvedValue(202)

    await expect(deleteMyAccount()).resolves.toBe('pendingApproval')
  })

  it('treats any other success as a completed deletion', async () => {
    clientMocks.apiRequestForStatus.mockResolvedValue(200)

    await expect(deleteMyAccount()).resolves.toBe('deleted')
  })
})
