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

import { type UseMutationResult, useMutation, useQueryClient } from '@tanstack/react-query'
import { clearLocalSession } from '../../../utils/authClient'
import { runtimeBasePath } from '../../../utils/basePath'
import { deleteMyAccount, type AccountDeletionOutcome } from '../api/accountApi'

/**
 * Deletes the account - or asks for it to be deleted.
 *
 * Only a completed deletion tears the session down. When an approval workflow
 * intercepts, the account is still there and still works, so signing the user
 * out and showing them a goodbye page would be a lie; the caller reports that
 * the request is awaiting approval and leaves them where they are.
 *
 * On a real deletion the order matters: the query cache is cleared first (a
 * background refetch afterwards would 401 and bounce to a sign-in for a user
 * who no longer exists), the worker-held session is dropped, and the browser
 * hard-navigates so the whole SPA is gone rather than unmounted.
 */
export default function useDeleteAccountMutation(): UseMutationResult<
  AccountDeletionOutcome,
  Error,
  void
> {
  const queryClient = useQueryClient()

  return useMutation({
    mutationFn: () => deleteMyAccount(),
    onSuccess: async (outcome): Promise<void> => {
      if (outcome !== 'deleted') {
        return
      }
      queryClient.clear()
      await clearLocalSession()
      window.location.replace(`${window.location.origin}${runtimeBasePath()}/account-deleted`)
    },
  })
}
