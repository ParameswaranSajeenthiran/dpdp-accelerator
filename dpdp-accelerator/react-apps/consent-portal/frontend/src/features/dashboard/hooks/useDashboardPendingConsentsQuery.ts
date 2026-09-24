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

import { type UseQueryResult, useQuery } from '@tanstack/react-query'
import { fetchMyConsents } from '../../my-consents/api/myConsentsApi'
import type { ConsentSummary } from '../../../types/consent'

/**
 * A batch big enough to reliably contain the signed-in user's most recent pending consents
 * (sorted and trimmed by the caller) without paging - the "Needs your attention" list only ever
 * shows a handful of them, unlike the state counts elsewhere on this page.
 */
const PENDING_BATCH_SIZE = 100

/** The signed-in user's pending consents, with purposes/authorizations inlined for display. */
async function fetchPendingConsents(): Promise<ConsentSummary[]> {
  // The server defaults relation to SUBJECT, which would hide exactly the consents awaiting this
  // user's decision as an authorizer.
  const response = await fetchMyConsents({
    limit: PENDING_BATCH_SIZE,
    offset: 0,
    state: 'PENDING',
    relation: 'ANY',
  })
  return response.data
}

export default function useDashboardPendingConsentsQuery(
  enabled: boolean,
): UseQueryResult<ConsentSummary[]> {
  return useQuery({
    queryKey: ['consents', 'dashboard', 'self', 'pending-list'],
    queryFn: fetchPendingConsents,
    enabled,
  })
}
