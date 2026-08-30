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

import { useQuery } from '@tanstack/react-query'
import {
  CONSENT_LIFECYCLE_FETCH_LIMIT,
  type ConsentHistoryEntry,
  type ConsentStatusAuditEntry,
} from '../../../types/consentHistory'
import type { ConsentHistoryQueryResult } from '../../my-consents/hooks/useConsentHistoryQueries'
import {
  fetchAdminConsentFullHistory,
  fetchAdminConsentStatusHistory,
} from '../api/consentHistoryApi'

export function useAdminConsentStatusHistoryQuery(
  consentId: string,
  enabled: boolean,
): ConsentHistoryQueryResult<ConsentStatusAuditEntry> {
  const query = useQuery({
    queryKey: ['admin-consent-status-history', consentId],
    queryFn: async (): Promise<ConsentStatusAuditEntry[]> => {
      const response = await fetchAdminConsentStatusHistory(consentId, {
        limit: CONSENT_LIFECYCLE_FETCH_LIMIT,
        offset: 0,
      })
      return response.statusHistory
    },
    enabled,
  })

  return {
    entries: query.data ?? [],
    isLoading: query.isLoading,
    isError: query.isError,
  }
}

export function useAdminConsentFullHistoryQuery(
  consentId: string,
  enabled: boolean,
): ConsentHistoryQueryResult<ConsentHistoryEntry> {
  const query = useQuery({
    queryKey: ['admin-consent-full-history', consentId],
    queryFn: async (): Promise<ConsentHistoryEntry[]> => {
      const response = await fetchAdminConsentFullHistory(consentId, {
        limit: CONSENT_LIFECYCLE_FETCH_LIMIT,
        offset: 0,
      })
      return response.history
    },
    enabled,
  })

  return {
    entries: query.data ?? [],
    isLoading: query.isLoading,
    isError: query.isError,
  }
}
