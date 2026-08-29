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
  type ConsentHistoryPage,
  useConsentHistoryPaging,
} from '../../../hooks/useConsentHistoryPaging'
import type { ConsentHistoryEntry, ConsentStatusAuditEntry } from '../../../types/consentHistory'
import {
  fetchAdminConsentFullHistory,
  fetchAdminConsentStatusHistory,
} from '../api/consentHistoryApi'

export function useAdminConsentStatusHistoryQuery(
  consentId: string,
  enabled: boolean,
): ConsentHistoryPage<ConsentStatusAuditEntry> {
  return useConsentHistoryPaging(
    ['admin-consent-status-history', consentId],
    async ({ limit, offset }) => {
      const response = await fetchAdminConsentStatusHistory(consentId, { limit, offset })
      return { entries: response.statusHistory, totalCount: response.pagination.totalCount }
    },
    enabled,
  )
}

export function useAdminConsentFullHistoryQuery(
  consentId: string,
  enabled: boolean,
): ConsentHistoryPage<ConsentHistoryEntry> {
  return useConsentHistoryPaging(
    ['admin-consent-full-history', consentId],
    async ({ limit, offset }) => {
      const response = await fetchAdminConsentFullHistory(consentId, { limit, offset })
      return { entries: response.history, totalCount: response.pagination.totalCount }
    },
    enabled,
  )
}
