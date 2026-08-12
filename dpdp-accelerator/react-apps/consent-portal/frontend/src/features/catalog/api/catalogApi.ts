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
  CatalogElement,
  CursorPageParams,
  ElementInput,
  ElementListQueryParams,
  ElementListResponse,
  PurposeDetail,
  PurposeListResponse,
  PurposeVersionListResponse,
} from '../../../types/catalog'
import { apiRequest, apiRequestNoContent } from '../../../utils/apiClient'

function toCursorQuery(params: CursorPageParams): Record<string, string | number | undefined> {
  return {
    limit: params.limit,
    after: params.after,
    before: params.before,
  }
}

/**
 * Builds a `name co "<term>"` filter for the Elements API. Values are quoted
 * per the filter grammar so a search term containing spaces or quotes stays
 * a single value rather than being parsed as separate filter tokens.
 */
export function buildElementNameFilter(term: string): string | undefined {
  const trimmed = term.trim()
  if (!trimmed) {
    return undefined
  }
  const escaped = trimmed.replace(/\\/g, '\\\\').replace(/"/g, '\\"')
  return `name co "${escaped}"`
}

export function fetchElements(params: ElementListQueryParams): Promise<ElementListResponse> {
  return apiRequest<ElementListResponse>('/api/consent-elements', {
    method: 'GET',
    query: { ...toCursorQuery(params), filter: params.filter },
  })
}

export function fetchElement(elementId: string): Promise<CatalogElement> {
  return apiRequest<CatalogElement>(`/api/consent-elements/${encodeURIComponent(elementId)}`, {
    method: 'GET',
  })
}

export function createElement(payload: ElementInput): Promise<CatalogElement> {
  return apiRequest<CatalogElement>('/api/consent-elements', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(payload),
  })
}

export function deleteElement(elementId: string): Promise<void> {
  return apiRequestNoContent(`/api/consent-elements/${encodeURIComponent(elementId)}`, {
    method: 'DELETE',
  })
}

export function fetchPurposes(params: CursorPageParams): Promise<PurposeListResponse> {
  return apiRequest<PurposeListResponse>('/api/consent-purposes', {
    method: 'GET',
    query: toCursorQuery(params),
  })
}

export function fetchPurpose(purposeId: string): Promise<PurposeDetail> {
  return apiRequest<PurposeDetail>(`/api/consent-purposes/${encodeURIComponent(purposeId)}`, {
    method: 'GET',
  })
}

export function fetchPurposeVersions(
  purposeId: string,
  params: CursorPageParams,
): Promise<PurposeVersionListResponse> {
  return apiRequest<PurposeVersionListResponse>(
    `/api/consent-purposes/${encodeURIComponent(purposeId)}/versions`,
    { method: 'GET', query: toCursorQuery(params) },
  )
}
