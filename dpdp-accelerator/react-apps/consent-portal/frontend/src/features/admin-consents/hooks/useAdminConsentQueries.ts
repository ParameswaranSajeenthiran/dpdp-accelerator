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

import {
  keepPreviousData,
  type UseMutationResult,
  type UseQueryResult,
  useMutation,
  useQuery,
  useQueryClient,
} from '@tanstack/react-query'
import type {
  AdminConsentListQueryParams,
  AdminConsentRegistryFilters,
  ConsentDetail,
  ConsentRecord,
} from '../../../types/consent'
import { getNextCursor, getPreviousCursor } from '../../../utils/cursorPagination'
import { toConsentRow, toConsentRowFromSummary } from '../../../utils/consentRows'
import { buildTimestampFilter, combineFilters } from '../../../utils/filterGrammar'
import { endOfDayMillis, parseDateOnly, startOfDayMillis } from '../../../utils/dateTime'
import {
  buildConsentPropertyFilter,
  fetchAdminConsentByID,
  fetchAdminConsents,
  revokeAdminConsent,
} from '../api/adminConsentsApi'

export interface AdminConsentListResult {
  rows: ConsentRecord[]
  nextCursor?: string
  previousCursor?: string
}

function toListParams(
  filters: AdminConsentRegistryFilters,
  rowsPerPage: number,
  cursor: { after?: string; before?: string },
): AdminConsentListQueryParams {
  const afterDate = parseDateOnly(filters.createdAfter)
  const beforeDate = parseDateOnly(filters.createdBefore)

  return {
    limit: rowsPerPage,
    after: cursor.after,
    before: cursor.before,
    userId: filters.userId || undefined,
    relation: filters.relation,
    serviceId: filters.serviceId || undefined,
    state: filters.state === 'All' ? undefined : filters.state,
    purposeId: filters.purposeId || undefined,
    filter: combineFilters(
      buildConsentPropertyFilter(filters.propertyKey, filters.propertyValue),
      buildTimestampFilter(
        afterDate ? startOfDayMillis(afterDate) : undefined,
        beforeDate ? endOfDayMillis(beforeDate) : undefined,
      ),
    ),
  }
}

export function useAdminConsentListQuery(
  filters: AdminConsentRegistryFilters,
  rowsPerPage: number,
  cursor: { after?: string; before?: string },
): UseQueryResult<AdminConsentListResult> {
  const consentID = filters.consentId
  const params = toListParams(filters, rowsPerPage, cursor)

  return useQuery({
    queryKey: ['admin-consents', { consentID, params }],
    queryFn: async (): Promise<AdminConsentListResult> => {
      if (consentID) {
        const consent = await fetchAdminConsentByID(consentID)
        return { rows: [toConsentRow(consent)] }
      }

      const response = await fetchAdminConsents(params)

      return {
        rows: response.Consents.map(toConsentRowFromSummary),
        nextCursor: getNextCursor(response.links),
        previousCursor: getPreviousCursor(response.links),
      }
    },
    placeholderData: keepPreviousData,
  })
}

export function useAdminConsentDetailQuery(
  consentID: string | undefined,
): UseQueryResult<ConsentDetail> {
  return useQuery({
    queryKey: ['admin-consent', consentID],
    queryFn: () => fetchAdminConsentByID(String(consentID)),
    enabled: Boolean(consentID),
  })
}

export function useAdminRevokeConsentMutation(): UseMutationResult<unknown, Error, string> {
  const queryClient = useQueryClient()

  return useMutation({
    mutationFn: (consentID: string) => revokeAdminConsent(consentID),
    onSuccess: async (_data, consentID) => {
      await queryClient.invalidateQueries({ queryKey: ['admin-consents'] })
      await queryClient.invalidateQueries({ queryKey: ['admin-consent', consentID] })
      await queryClient.invalidateQueries({ queryKey: ['admin-consent-status-history', consentID] })
      await queryClient.invalidateQueries({ queryKey: ['admin-consent-full-history', consentID] })
    },
  })
}
