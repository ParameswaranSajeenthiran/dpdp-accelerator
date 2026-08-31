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

import { apiRequestForStatus } from '../../../utils/apiClient'

/**
 * What actually happened to the account. Deleting it is not always immediate:
 * when an approval workflow is associated with the Delete User operation the
 * Identity Server records a request and answers 202, leaving the account
 * usable until an administrator approves it.
 */
export type AccountDeletionOutcome = 'deleted' | 'pendingApproval'

/** The 202 the Identity Server returns when a workflow intercepts the delete. */
const ACCEPTED = 202

/**
 * Deletes the signed-in user's own account, or raises a request to. Guarded by
 * the account:self:delete scope, which only the dpdp-consent-user role grants.
 */
export async function deleteMyAccount(): Promise<AccountDeletionOutcome> {
  const status = await apiRequestForStatus('/scim2/Me', { method: 'DELETE' })
  return status === ACCEPTED ? 'pendingApproval' : 'deleted'
}
