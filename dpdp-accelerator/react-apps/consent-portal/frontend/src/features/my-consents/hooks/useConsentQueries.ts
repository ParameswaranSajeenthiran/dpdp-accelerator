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
import {
  approveMyConsent,
  fetchMyConsentByID,
  fetchMyConsents,
  rejectMyConsent,
  revokeMyConsent,
} from '../api/myConsentsApi'
import type {
  ConsentDetail,
  ConsentListQueryParams,
  ConsentRecord,
  ConsentRegistryFilters,
} from '../../../types/consent'
import { toConsentRowFromSummary } from '../../../utils/consentRows'
import { buildTimestampFilter } from '../../../utils/filterGrammar'
import { endOfDayMillis, parseDateOnly, startOfDayMillis } from '../../../utils/dateTime'

export interface ConsentListResult {
  rows: ConsentRecord[]
  /** True when the page came back full, which is the only hint of more data. */
  hasNextPage: boolean
}

function toListParams(
  filters: ConsentRegistryFilters,
  page: number,
  rowsPerPage: number,
): ConsentListQueryParams {
  const afterDate = parseDateOnly(filters.createdAfter)
  const beforeDate = parseDateOnly(filters.createdBefore)

  return {
    state: filters.state === 'All' ? undefined : filters.state,
    serviceId: filters.serviceId.trim() || undefined,
    relation: filters.relation,
    filter: buildTimestampFilter(
      afterDate ? startOfDayMillis(afterDate) : undefined,
      beforeDate ? endOfDayMillis(beforeDate) : undefined,
    ),
    limit: rowsPerPage,
    offset: page * rowsPerPage,
  }
}

export function useConsentListQuery(
  filters: ConsentRegistryFilters,
  page: number,
  rowsPerPage: number,
): UseQueryResult<ConsentListResult> {
  const params = toListParams(filters, page, rowsPerPage)

  return useQuery({
    queryKey: ['consents', params],
    queryFn: async (): Promise<ConsentListResult> => {
      const response = await fetchMyConsents(params)

      return {
        rows: response.data.map(toConsentRowFromSummary),
        hasNextPage: response.data.length >= params.limit,
      }
    },
    placeholderData: keepPreviousData,
  })
}

export function useConsentDetailQuery(
  consentID: string | undefined,
): UseQueryResult<ConsentDetail> {
  return useQuery<ConsentDetail>({
    queryKey: ['consent', consentID],
    queryFn: async (): Promise<ConsentDetail> => fetchMyConsentByID(String(consentID)),
    enabled: Boolean(consentID),
  })
}

function useConsentLifecycleMutation(
  action: (consentID: string) => Promise<unknown>,
): UseMutationResult<unknown, Error, string> {
  const queryClient = useQueryClient()

  return useMutation({
    mutationFn: async (consentID: string): Promise<unknown> => action(consentID),
    onSuccess: async (_data, consentID): Promise<void> => {
      await queryClient.invalidateQueries({ queryKey: ['consents'] })
      await queryClient.invalidateQueries({ queryKey: ['consent', consentID] })
      await queryClient.invalidateQueries({ queryKey: ['consent-status-history', consentID] })
      await queryClient.invalidateQueries({ queryKey: ['consent-full-history', consentID] })
    },
  })
}

export function useApproveConsentMutation(
  currentUserId: string,
): UseMutationResult<unknown, Error, string> {
  return useConsentLifecycleMutation((consentID) => approveMyConsent(consentID, currentUserId))
}

export function useRejectConsentMutation(
  currentUserId: string,
): UseMutationResult<unknown, Error, string> {
  return useConsentLifecycleMutation((consentID) => rejectMyConsent(consentID, currentUserId))
}

export function useRevokeConsentMutation(): UseMutationResult<unknown, Error, string> {
  return useConsentLifecycleMutation(revokeMyConsent)
}
