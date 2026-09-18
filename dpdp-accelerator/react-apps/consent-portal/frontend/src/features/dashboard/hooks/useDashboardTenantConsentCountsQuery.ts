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
import { fetchAdminConsents } from '../../admin-consents/api/adminConsentsApi'
import type { ConsentState } from '../../../types/consent'
import { getNextCursor } from '../../../utils/cursorPagination'

const COUNT_PAGE_SIZE = 200
const COUNT_MAX_PAGES = 100

export interface StateCount {
  count: number
  /** True if COUNT_MAX_PAGES was hit before the state's consents were exhausted - `count` is
   *  then a floor, not the exact total, and should be displayed as e.g. "20,000+". */
  isAtLeast: boolean
}

export interface TenantConsentCounts {
  active: StateCount
  pending: StateCount
}

/**
 * `totalResults` on this endpoint is not a grand total - it always equals the number of
 * consents in the page just fetched (confirmed against a live server: `limit=1` returns
 * `totalResults: 1` even when far more consents match). The only way to get an exact count is
 * to page through every consent in the state and sum what each page returns, following `links`
 * until it stops offering a `next` cursor.
 */
async function countConsentsByState(
  state: ConsentState,
  cursor?: string,
  count = 0,
  remainingPages = COUNT_MAX_PAGES,
): Promise<StateCount> {
  const response = await fetchAdminConsents({ limit: COUNT_PAGE_SIZE, after: cursor, state })
  const total = count + response.Consents.length
  const nextCursor = getNextCursor(response.links)

  if (!nextCursor) {
    return { count: total, isAtLeast: false }
  }

  if (remainingPages <= 1) {
    return { count: total, isAtLeast: true }
  }

  return countConsentsByState(state, nextCursor, total, remainingPages - 1)
}

async function fetchTenantConsentCounts(): Promise<TenantConsentCounts> {
  const [active, pending] = await Promise.all([
    countConsentsByState('ACTIVE'),
    countConsentsByState('PENDING'),
  ])
  return { active, pending }
}

export default function useDashboardTenantConsentCountsQuery(
  enabled: boolean,
): UseQueryResult<TenantConsentCounts> {
  return useQuery({
    queryKey: ['consents', 'dashboard', 'tenant', 'counts'],
    queryFn: fetchTenantConsentCounts,
    enabled,
  })
}
