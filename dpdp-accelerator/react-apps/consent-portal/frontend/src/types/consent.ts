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

import type { CursorLink } from './catalog'

export const CONSENT_STATES = ['PENDING', 'ACTIVE', 'REJECTED', 'REVOKED', 'EXPIRED'] as const

export type ConsentState = (typeof CONSENT_STATES)[number]

export function isConsentState(state: string): state is ConsentState {
  return CONSENT_STATES.includes(state as ConsentState)
}

export const CONSENT_AUTHORIZATION_STATES = ['APPROVED', 'REJECTED', 'REVOKED', 'EXPIRED'] as const

export type ConsentAuthorizationState = (typeof CONSENT_AUTHORIZATION_STATES)[number]

/**
 * A caller's relationship to a consent: the data `SUBJECT` it's about, an
 * `AUTHORIZER` listed to approve/reject it on the subject's behalf (DPDP's
 * guardian/delegate model), or `ANY` for either.
 */
export const CONSENT_RELATIONS = ['SUBJECT', 'AUTHORIZER', 'ANY'] as const

export type ConsentRelation = (typeof CONSENT_RELATIONS)[number]

/**
 * An element referenced by a consented purpose.
 *
 * Consent level elements carry no approval or requirement flags in WSO2
 * Identity Server 7.3; those live on the purpose definition instead.
 */
export interface ConsentPurposeElement {
  id: string
  name: string
  displayName?: string
}

export interface ConsentPurpose {
  id: string
  name: string
  type: string
  versionId: string
  version: string
  elements: ConsentPurposeElement[]
  properties?: Record<string, string>
}

/** `userId` is a username (for example "admin"), not an identifier. */
export interface ConsentAuthorization {
  userId: string
  state: ConsentAuthorizationState | string
  updatedTime: number
}

export interface ConsentDetail {
  id: string
  /** Username of the data subject, not an identifier. */
  subjectId: string
  serviceId: string
  state: ConsentState | string
  language?: string
  timestamp: number
  expiryTime?: number
  purposes: ConsentPurpose[]
  authorizations?: ConsentAuthorization[]
  properties?: Record<string, string>
}

/**
 * Consents returned by the self-service and administrative list endpoints.
 *
 * `purposes` and `authorizations` are present whenever the list call asked
 * for them via `attributes=purposes,authorizations` - both list endpoints
 * request that, so in practice they're populated except on a row whose
 * expansion the server itself could not resolve.
 */
export interface ConsentSummary {
  id: string
  subjectId: string
  serviceId: string
  state: ConsentState | string
  timestamp: number
  purposes?: ConsentPurpose[]
  authorizations?: ConsentAuthorization[]
}

/**
 * Row model shared by the self-service and administrative consent tables.
 *
 * `purposes` is undefined when the source endpoint does not return them.
 * `authorizations` backs per-caller approve/reject gating - see
 * `features/my-consents/utils/consentAuthorization.ts`.
 */
export interface ConsentRecord {
  id: string
  subjectId: string
  serviceId: string
  state: ConsentState
  timestamp: number
  purposes?: string[]
  authorizations?: ConsentAuthorization[]
}

export interface ConsentRegistryFilters {
  state: 'All' | ConsentState
  serviceId: string
  relation: ConsentRelation
  createdAfter: string
  createdBefore: string
}

export interface AdminConsentRegistryFilters extends ConsentRegistryFilters {
  consentId: string
  userId: string
  purposeId: string
  propertyKey: string
  propertyValue: string
}

/**
 * Self-service search metadata.
 *
 * `total` only counts the records seen so far because the upstream API is
 * cursor based and reports no grand total. Never render it as an exact count.
 */
export interface ConsentSearchMetadata {
  total: number
  offset: number
  count: number
  limit: number
}

export interface ConsentSearchResponse {
  data: ConsentSummary[]
  metadata: ConsentSearchMetadata
}

export interface ConsentListQueryParams {
  limit: number
  offset: number
  /** One state, or undefined for all: the filter is a single-select. */
  state?: ConsentState
  serviceId?: string
  /** Always paired with the signed-in user's own ID; defaults to `ANY` server-side. */
  relation?: ConsentRelation
  /** A `timestamp ge/le <epoch-ms>` clause built by `buildTimestampFilter`. */
  filter?: string
}

export interface AdminConsentListQueryParams {
  limit: number
  after?: string
  before?: string
  userId?: string
  /** Only meaningful paired with `userId` - never sent alone. */
  relation?: ConsentRelation
  serviceId?: string
  state?: string
  purposeId?: string
  filter?: string
}

export interface AdminConsentListResponse {
  totalResults: number
  links: CursorLink[]
  Consents: ConsentSummary[]
}
