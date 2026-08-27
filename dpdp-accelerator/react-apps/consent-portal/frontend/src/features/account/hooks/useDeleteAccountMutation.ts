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
import { deleteMyAccount } from '../api/accountApi'

/**
 * Deletes the account, then tears the client session down in an order that
 * keeps any request from firing with the dead user's token: the query cache
 * is cleared first (a background refetch after the 204 would 401 and bounce
 * to a sign-in for a user that no longer exists), the worker-held session is
 * dropped, and the browser hard-navigates so the whole SPA - router state,
 * providers, everything - is gone rather than unmounted.
 */
export default function useDeleteAccountMutation(): UseMutationResult<void, Error, void> {
  const queryClient = useQueryClient()

  return useMutation({
    mutationFn: () => deleteMyAccount(),
    onSuccess: async (): Promise<void> => {
      queryClient.clear()
      await clearLocalSession()
      window.location.replace(`${window.location.origin}${runtimeBasePath()}/account-deleted`)
    },
  })
}
