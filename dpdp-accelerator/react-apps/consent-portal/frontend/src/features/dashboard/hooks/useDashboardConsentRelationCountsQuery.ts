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
import type { ConsentRelation } from '../../../types/consent'
import { type PageCount, pageCountFromOverfetch } from '../../../utils/cursorPagination'
import { CONSENT_COUNT_LIMIT } from './consentStateCounts'

export interface ConsentRelationCounts {
  /** Consents the signed-in user is the data SUBJECT of - their own. */
  subject: PageCount
  /** Consents the signed-in user is the AUTHORIZER for - managed on someone else's behalf. */
  authorizer: PageCount
}

async function countSelfConsentsByRelation(relation: ConsentRelation): Promise<PageCount> {
  const consents = await fetchMyConsentsRaw({ limit: CONSENT_COUNT_LIMIT + 1, relation })
  return pageCountFromOverfetch(consents.length, CONSENT_COUNT_LIMIT)
}

async function fetchConsentRelationCounts(): Promise<ConsentRelationCounts> {
  const [subject, authorizer] = await Promise.all([
    countSelfConsentsByRelation('SUBJECT'),
    countSelfConsentsByRelation('AUTHORIZER'),
  ])
  return { subject, authorizer }
}

export default function useDashboardConsentRelationCountsQuery(
  enabled: boolean,
): UseQueryResult<ConsentRelationCounts> {
  return useQuery({
    queryKey: ['consents', 'dashboard', 'self', 'relation-counts'],
    queryFn: fetchConsentRelationCounts,
    enabled,
  })
}
