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
import { type PageCount, pageCountFromCursor } from '../../../utils/cursorPagination'
import { CONSENT_COUNT_LIMIT, type ConsentStateCounts } from './consentStateCounts'

/**
 * `totalResults` on this endpoint is just the page size, not a real total (see pageCountFromCursor).
 * A single `limit=100` request per state is enough to
 * tell "exactly N" from "more than 100" via the presence of a `next` link, without paging
 * through a tenant's entire consent history.
 */
async function countTenantConsents(state: ConsentState): Promise<PageCount> {
  const response = await fetchAdminConsents({ limit: CONSENT_COUNT_LIMIT, state })
  return pageCountFromCursor(response.Consents.length, response.links, CONSENT_COUNT_LIMIT)
}

async function fetchTenantConsentStateCounts(): Promise<ConsentStateCounts> {
  const [pending, active, rejected, revoked, expired] = await Promise.all([
    countTenantConsents('PENDING'),
    countTenantConsents('ACTIVE'),
    countTenantConsents('REJECTED'),
    countTenantConsents('REVOKED'),
    countTenantConsents('EXPIRED'),
  ])
  return { pending, active, rejected, revoked, expired }
}

export default function useDashboardTenantConsentStateCountsQuery(
  enabled: boolean,
): UseQueryResult<ConsentStateCounts> {
  return useQuery({
    queryKey: ['consents', 'dashboard', 'tenant', 'state-counts'],
    queryFn: fetchTenantConsentStateCounts,
    enabled,
  })
}
