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
  ConsentHistoryPageParams,
  ConsentHistoryResponse,
  ConsentStatusHistoryResponse,
} from '../types/consentHistory'
import { apiRequest } from './apiClient'

/**
 * The accelerator's own consent-history webapp - a separate internal webapp from the
 * Identity Server's own `/api/identity/consent-mgt/v2.0`, see consent-history.yaml.
 */
const CONSENT_HISTORY_API = '/api/dpdp/consent-mgt/v1'

/** "/consents" for the admin routes, "/me/consents" for the self routes. */
export type ConsentHistoryBasePath = '/consents' | '/me/consents'

function consentHistoryPath(basePath: ConsentHistoryBasePath, consentID: string, suffix: string) {
  return `${CONSENT_HISTORY_API}${basePath}/${encodeURIComponent(consentID)}${suffix}`
}

export async function fetchConsentStatusHistory(
  basePath: ConsentHistoryBasePath,
  consentID: string,
  params: ConsentHistoryPageParams,
): Promise<ConsentStatusHistoryResponse> {
  return apiRequest<ConsentStatusHistoryResponse>(
    consentHistoryPath(basePath, consentID, '/status-history'),
    { method: 'GET', query: { limit: params.limit, offset: params.offset } },
  )
}

export async function fetchConsentFullHistory(
  basePath: ConsentHistoryBasePath,
  consentID: string,
  params: ConsentHistoryPageParams,
): Promise<ConsentHistoryResponse> {
  return apiRequest<ConsentHistoryResponse>(consentHistoryPath(basePath, consentID, '/history'), {
    method: 'GET',
    query: { limit: params.limit, offset: params.offset },
  })
}
