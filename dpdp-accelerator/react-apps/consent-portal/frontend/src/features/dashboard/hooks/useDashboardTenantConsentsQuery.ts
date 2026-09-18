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
import type { ConsentSummary } from '../../../types/consent'
import { getNextCursor } from '../../../utils/cursorPagination'

const DASHBOARD_PAGE_SIZE = 100
const DASHBOARD_MAX_PAGES = 20

/**
 * Walks the admin listing's cursor pages until `links` carries no `next`.
 *
 * Mirrors useDashboardConsentsQuery's self-service paging, capped the same
 * way, since the v2.0 admin API has no aggregate-stats endpoint of its own.
 */
async function fetchConsentPage(
  cursor: string | undefined,
  collected: ConsentSummary[],
  remainingPages: number,
): Promise<ConsentSummary[]> {
  const response = await fetchAdminConsents({ limit: DASHBOARD_PAGE_SIZE, after: cursor })
  const consents = [...collected, ...response.Consents]
  const nextCursor = getNextCursor(response.links)

  if (!nextCursor || remainingPages <= 1) {
    return consents
  }

  return fetchConsentPage(nextCursor, consents, remainingPages - 1)
}

async function fetchAllTenantConsents(): Promise<ConsentSummary[]> {
  return fetchConsentPage(undefined, [], DASHBOARD_MAX_PAGES)
}

export default function useDashboardTenantConsentsQuery(
  enabled: boolean,
): UseQueryResult<ConsentSummary[]> {
  return useQuery({
    queryKey: ['consents', 'dashboard', 'tenant'],
    queryFn: fetchAllTenantConsents,
    enabled,
  })
}
