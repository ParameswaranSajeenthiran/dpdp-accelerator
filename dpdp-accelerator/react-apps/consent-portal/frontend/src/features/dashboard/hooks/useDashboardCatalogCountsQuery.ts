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
import { fetchElements, fetchPurposes } from '../../catalog/api/catalogApi'
import { type PageCount, pageCountFromCursor } from '../../../utils/cursorPagination'
import { CONSENT_COUNT_LIMIT } from './consentStateCounts'

/**
 * Purposes/Elements share the consent-mgt v2.0 API's `CursorPage` envelope, the same family
 * whose `totalResults` turned out to be just the page size rather than a real total (see
 * pageCountFromCursor) - so these counts use the same "limit=100, 100+ if a next link remains"
 * approach, independently gated so either can render without the other.
 */
export default function useDashboardPurposesCountQuery(
  enabled: boolean,
): UseQueryResult<PageCount> {
  return useQuery({
    queryKey: ['catalog', 'dashboard', 'purposes-count'],
    queryFn: async () => {
      const response = await fetchPurposes({ limit: CONSENT_COUNT_LIMIT })
      return pageCountFromCursor(response.Purposes.length, response.links, CONSENT_COUNT_LIMIT)
    },
    enabled,
  })
}

export function useDashboardElementsCountQuery(enabled: boolean): UseQueryResult<PageCount> {
  return useQuery({
    queryKey: ['catalog', 'dashboard', 'elements-count'],
    queryFn: async () => {
      const response = await fetchElements({ limit: CONSENT_COUNT_LIMIT })
      return pageCountFromCursor(response.Elements.length, response.links, CONSENT_COUNT_LIMIT)
    },
    enabled,
  })
}
