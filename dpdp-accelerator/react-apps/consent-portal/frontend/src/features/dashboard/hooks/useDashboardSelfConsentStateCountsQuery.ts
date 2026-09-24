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
import { fetchMyConsentsRaw } from '../../my-consents/api/myConsentsApi'
import type { ConsentState } from '../../../types/consent'
import { type PageCount, pageCountFromOverfetch } from '../../../utils/cursorPagination'
import { CONSENT_COUNT_LIMIT, type ConsentStateCounts } from './consentStateCounts'

/**
 * The self-service consent list is a bare array with no pagination metadata at all (see
 * fetchMyConsentsRaw) - asking for one more than CONSENT_COUNT_LIMIT and checking the array
 * length is the only way to know whether more than that many consents exist.
 */
async function countSelfConsents(state: ConsentState): Promise<PageCount> {
  // The server defaults relation to SUBJECT, which would leave out consents this user only
  // authorizes - ANY keeps these counts in line with the Consents page.
  const consents = await fetchMyConsentsRaw({
    limit: CONSENT_COUNT_LIMIT + 1,
    state,
    relation: 'ANY',
  })
  return pageCountFromOverfetch(consents.length, CONSENT_COUNT_LIMIT)
}

async function fetchSelfConsentStateCounts(): Promise<ConsentStateCounts> {
  const [pending, active, rejected, revoked, expired] = await Promise.all([
    countSelfConsents('PENDING'),
    countSelfConsents('ACTIVE'),
    countSelfConsents('REJECTED'),
    countSelfConsents('REVOKED'),
    countSelfConsents('EXPIRED'),
  ])
  return { pending, active, rejected, revoked, expired }
}

export default function useDashboardSelfConsentStateCountsQuery(
  enabled: boolean,
): UseQueryResult<ConsentStateCounts> {
  return useQuery({
    queryKey: ['consents', 'dashboard', 'self', 'state-counts'],
    queryFn: fetchSelfConsentStateCounts,
    enabled,
  })
}
