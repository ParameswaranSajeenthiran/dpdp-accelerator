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

/** The Identity Server's consent management (administrative) API. */
const CONSENT_MGT_V2 = '/api/identity/consent-mgt/v2.0'

export async function fetchAdminConsents(
  params: AdminConsentListQueryParams,
): Promise<AdminConsentListResponse> {
  return apiRequest<AdminConsentListResponse>(`${CONSENT_MGT_V2}/consents`, {
    method: 'GET',
    query: {
      limit: params.limit,
      after: params.after,
      before: params.before,
      subjectId: params.subjectId,
      serviceId: params.serviceId,
      state: params.state,
    },
  })
}

export async function fetchAdminConsentByID(consentID: string): Promise<ConsentDetail> {
  return apiRequest<ConsentDetail>(
    `${CONSENT_MGT_V2}/consents/${encodeURIComponent(consentID)}`,
    { method: 'GET' },
  )
}

export async function revokeAdminConsent(consentID: string): Promise<unknown> {
  // The Identity Server answers revoke with an empty body.
  await apiRequestOptionalContent(
    `${CONSENT_MGT_V2}/consents/${encodeURIComponent(consentID)}/revoke`,
    { method: 'POST' },
  )
  return { status: 'OK' }
}
