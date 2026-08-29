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

/**
 * Mirrors `ActionType` in consent-history.yaml. AUTHORIZE_APPROVE/REJECT/REVOKE are kept
 * distinct from a generic AUTHORIZE since one value covering all three reads ambiguously;
 * EXPIRE is written by the expiry job/reconciliation with actionBy "SYSTEM".
 */
export const CONSENT_HISTORY_ACTION_TYPES = [
  'CREATE',
  'UPDATE',
  'REVOKE',
  'AUTHORIZE_APPROVE',
  'AUTHORIZE_REJECT',
  'AUTHORIZE_REVOKE',
  'DELETE',
  'EXPIRE',
] as const

export type ConsentHistoryActionType = (typeof CONSENT_HISTORY_ACTION_TYPES)[number]

export function isConsentHistoryActionType(value: string): value is ConsentHistoryActionType {
  return (CONSENT_HISTORY_ACTION_TYPES as readonly string[]).includes(value)
}

/** The actor value the server writes for EXPIRE entries - never a real username. */
export const SYSTEM_ACTOR = 'SYSTEM'

/** Mirrors `PaginationDTO`. */
export interface ConsentHistoryPagination {
  limit: number
  offset: number
  totalCount: number
}

/** Mirrors `StatusAuditEntryDTO`. `previousStatus` is absent for the initial CREATE entry. */
export interface ConsentStatusAuditEntry {
  previousStatus?: string
  currentStatus: string
  actionType: ConsentHistoryActionType | string
  actionBy: string
  actionTime: number
}

/** Mirrors `StatusHistoryResponseDTO`. */
export interface ConsentStatusHistoryResponse {
  consentId: string
  statusHistory: ConsentStatusAuditEntry[]
  pagination: ConsentHistoryPagination
}

/**
 * The consent snapshot stored per history entry: a trimmed, hand-written view of the consent
 * mgt core `Receipt`, produced by `DPDPConsentSnapshotBuilder` on the server. Deliberately not
 * the same shape as `ConsentDetail` - this is the raw receipt snapshot, not the portal's REST
 * API DTO.
 */
export interface ConsentSnapshotElement {
  name: string
  displayName: string
  consented: boolean
}

export interface ConsentSnapshotPurpose {
  purpose: string
  uuid: string
  primaryPurpose: boolean
  elements: ConsentSnapshotElement[]
}

export interface ConsentSnapshotService {
  service: string
  spDisplayName: string
  purposes: ConsentSnapshotPurpose[]
}

export interface ConsentSnapshotAuthorization {
  userId: string
  type: string
  status: string
  updatedTime: number
}

export interface ConsentSnapshot {
  state: string
  piiPrincipalId: string
  language?: string
  expiryTime?: number
  properties?: Record<string, string>
  services?: ConsentSnapshotService[]
  authorizations?: ConsentSnapshotAuthorization[]
}

/** Mirrors `ConsentHistoryEntryDTO`. */
export interface ConsentHistoryEntry {
  actionType: ConsentHistoryActionType | string
  actionBy: string
  actionTime: number
  snapshot: ConsentSnapshot
}

/** Mirrors `ConsentHistoryResponseDTO`. */
export interface ConsentHistoryResponse {
  consentId: string
  history: ConsentHistoryEntry[]
  pagination: ConsentHistoryPagination
}

export interface ConsentHistoryPageParams {
  limit: number
  offset: number
}
