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

import type {
  AdminConsentListQueryParams,
  AdminConsentListResponse,
  ConsentDetail,
} from '../../../types/consent'
import { apiRequest, apiRequestOptionalContent } from '../../../utils/apiClient'
import { escapeFilterValue } from '../../../utils/filterGrammar'

/** The Identity Server's consent management (administrative) API. */
const CONSENT_MGT_V2 = '/api/identity/consent-mgt/v2.0'

/**
 * Builds a `properties.<key> eq "<value>"` filter, the Identity Server's
 * dot-notation search over a consent's custom properties. Both key and
 * value are required -- a lone key or value can't be searched.
 */
export function buildConsentPropertyFilter(key: string, value: string): string | undefined {
  const trimmedKey = key.trim()
  const trimmedValue = value.trim()
  return trimmedKey && trimmedValue
    ? `properties.${trimmedKey} eq "${escapeFilterValue(trimmedValue)}"`
    : undefined
}

export async function fetchAdminConsentByID(consentID: string): Promise<ConsentDetail> {
  return apiRequest<ConsentDetail>(`${CONSENT_MGT_V2}/consents/${encodeURIComponent(consentID)}`, {
    method: 'GET',
  })
}

/**
 * Lists consents across users.
 *
 * `attributes=purposes,authorizations` asks the list endpoint to inline what
 * the table needs directly, so no per-row detail lookup is required here (see
 * https://github.com/wso2/dpdp-accelerator/issues/23). `relation` is only
 * meaningful paired with `userId` - the server rejects one without the other
 * - so it's omitted whenever no user is being searched for.
 */
export async function fetchAdminConsents(
  params: AdminConsentListQueryParams,
): Promise<AdminConsentListResponse> {
  return apiRequest<AdminConsentListResponse>(`${CONSENT_MGT_V2}/consents`, {
    method: 'GET',
    query: {
      limit: params.limit,
      after: params.after,
      before: params.before,
      userId: params.userId,
      relation: params.userId ? params.relation : undefined,
      serviceId: params.serviceId,
      state: params.state,
      purposeId: params.purposeId,
      filter: params.filter,
      attributes: 'purposes,authorizations',
    },
  })
}

export async function revokeAdminConsent(consentID: string): Promise<unknown> {
  // The Identity Server answers revoke with an empty body.
  await apiRequestOptionalContent(
    `${CONSENT_MGT_V2}/consents/${encodeURIComponent(consentID)}/revoke`,
    { method: 'POST' },
  )
  return { status: 'OK' }
}
