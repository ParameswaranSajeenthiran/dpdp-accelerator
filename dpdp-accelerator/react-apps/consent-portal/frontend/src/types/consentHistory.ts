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

export interface ConsentHistoryPageParams {
  limit: number
  offset: number
}

/**
 * The lifecycle table shows every event for a consent at once rather than paginating - a
 * consent's status-change history is small (a handful of entries in practice), so a single
 * generous page avoids "Load more" UI for no real benefit.
 */
export const CONSENT_LIFECYCLE_FETCH_LIMIT = 100

/**
 * The snapshot stored per history entry is a trimmed view of the consent mgt core `Receipt`
 * (`DPDPConsentSnapshotBuilder` on the server) and carries far more than these three fields
 * (state, piiPrincipalId, language, services/purposes/elements). Only `expiryTime`,
 * `properties` and `authorizations` are typed here because those are the only fields
 * `ReceiptUpdateInput` (the update model IS's own consent-mgt core accepts a PATCH against)
 * can actually change - the full-snapshot view only ever needs to diff what update can affect.
 */
export interface ConsentSnapshotAuthorization {
  userId: string
  type: string
  status: string
  updatedTime: number
}

export interface ConsentSnapshot {
  expiryTime?: number
  properties?: Record<string, string>
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
