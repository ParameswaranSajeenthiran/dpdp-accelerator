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
  AdminConsentRegistryFilters,
  ConsentRelation,
  ConsentState,
} from '../../../types/consent'
import { CONSENT_RELATIONS, isConsentState } from '../../../types/consent'

function isConsentRelation(value: string): value is ConsentRelation {
  return (CONSENT_RELATIONS as readonly string[]).includes(value)
}

export const EMPTY_ADMIN_CONSENT_FILTERS: AdminConsentRegistryFilters = {
  state: 'All',
  consentId: '',
  userId: '',
  relation: 'ANY',
  serviceId: '',
  purposeId: '',
  propertyKey: '',
  propertyValue: '',
  createdAfter: '',
  createdBefore: '',
}

export function normalizeAdminConsentFilters(
  filters: AdminConsentRegistryFilters,
): AdminConsentRegistryFilters {
  return {
    state: filters.state,
    consentId: filters.consentId.trim(),
    userId: filters.userId.trim(),
    relation: filters.relation,
    serviceId: filters.serviceId.trim(),
    purposeId: filters.purposeId.trim(),
    propertyKey: filters.propertyKey.trim(),
    propertyValue: filters.propertyValue.trim(),
    createdAfter: filters.createdAfter.trim(),
    createdBefore: filters.createdBefore.trim(),
  }
}

export function getAdminConsentFilters(searchParams: URLSearchParams): AdminConsentRegistryFilters {
  const state = searchParams.get('state') ?? ''
  const relation = searchParams.get('relation') ?? ''

  return normalizeAdminConsentFilters({
    state: isConsentState(state) ? (state as ConsentState) : 'All',
    consentId: searchParams.get('consentId') ?? '',
    userId: searchParams.get('userId') ?? '',
    relation: isConsentRelation(relation) ? relation : 'ANY',
    serviceId: searchParams.get('serviceId') ?? '',
    purposeId: searchParams.get('purposeId') ?? '',
    propertyKey: searchParams.get('propertyKey') ?? '',
    propertyValue: searchParams.get('propertyValue') ?? '',
    createdAfter: searchParams.get('createdAfter') ?? '',
    createdBefore: searchParams.get('createdBefore') ?? '',
  })
}
