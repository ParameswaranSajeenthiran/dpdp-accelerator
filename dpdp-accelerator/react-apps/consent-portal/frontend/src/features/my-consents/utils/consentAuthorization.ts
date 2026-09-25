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

import type { ConsentAuthorization, ConsentState } from '../../../types/consent'
import { normalizeConsentState } from './statusChip'

/**
 * `authorizations` never carries an entry for the consent's own subject -
 * only for the other people listed to approve or reject it on the subject's
 * behalf. So a signed-in user acting as the subject falls through to the
 * consent's own state below, same as before this consent could have more
 * than one decision-maker.
 */
function myAuthorization(
  authorizations: ConsentAuthorization[] | undefined,
  currentUserId: string,
): ConsentAuthorization | undefined {
  return authorizations?.find((authorization) => authorization.userId === currentUserId)
}

/**
 * The status message to show instead of a button, when none of
 * `canApprove`/`canReject`/`canRevoke` applies. See "Approve / Reject /
 * Revoke rules" in CLAUDE.md for the full rule table this implements.
 */
export type ConsentStatusMessageKey =
  | 'awaitingApproval'
  | 'approved'
  | 'rejected'
  | 'decisionRecordedWaiting'
  | 'youRejected'
  | 'revoked'
  | 'expired'

export interface ConsentActionView {
  canApprove: boolean
  canReject: boolean
  canRevoke: boolean
  statusMessageKey: ConsentStatusMessageKey | null
}

const NO_ACTIONS: ConsentActionView = {
  canApprove: false,
  canReject: false,
  canRevoke: false,
  statusMessageKey: null,
}

/**
 * What the signed-in caller can do with a consent on the self-service
 * surface, gated on their own `authorizations` entry rather than the
 * consent's aggregate state - the aggregate can stay `PENDING` while this
 * caller has already decided, or waiting on someone else entirely.
 *
 * `subjectId` only matters for a Direct Consent (`authorizations` empty):
 * there, the caller must actually be the subject to revoke it. Everywhere
 * else, having (or not having) an `authorizations` entry already answers the
 * question regardless of who the subject is - which is why `isApprovableByCurrentUser`
 * and `isRejectableByCurrentUser` below don't need a `subjectId` at all.
 */
export function getSelfConsentActionView(
  subjectId: string,
  consentState: ConsentState | string,
  authorizations: ConsentAuthorization[] | undefined,
  currentUserId: string,
): ConsentActionView {
  const state = normalizeConsentState(consentState)

  // Terminal aggregate states win over anything a stale per-authorizer entry
  // says - a consent revoked or expired after the fact stays that way for
  // everyone, no matter what their own last recorded decision was.
  if (state === 'REVOKED') {
    return { ...NO_ACTIONS, statusMessageKey: 'revoked' }
  }
  if (state === 'EXPIRED') {
    return { ...NO_ACTIONS, statusMessageKey: 'expired' }
  }

  const authorizers = authorizations ?? []

  // Direct Consent - no one else named. The subject owns it outright. PENDING/REJECTED aren't
  // reachable through the normal creation flow here (a Direct consent is created ACTIVE), but a
  // consent can still exist in that shape - e.g. created directly with an explicit state - so
  // this stays defensive rather than silently returning no message at all.
  if (authorizers.length === 0) {
    if (state === 'ACTIVE') {
      return { ...NO_ACTIONS, canRevoke: subjectId === currentUserId }
    }
    if (state === 'REJECTED') {
      return {
        ...NO_ACTIONS,
        statusMessageKey: subjectId === currentUserId ? 'youRejected' : 'rejected',
      }
    }
    return NO_ACTIONS
  }

  const own = myAuthorization(authorizers, currentUserId)

  // Delegated Consent, pure observer - the caller has no entry of their own,
  // so they're being notified of someone else's decision, not asked for one.
  if (!own) {
    if (state === 'PENDING') return { ...NO_ACTIONS, statusMessageKey: 'awaitingApproval' }
    if (state === 'ACTIVE') return { ...NO_ACTIONS, statusMessageKey: 'approved' }
    if (state === 'REJECTED') return { ...NO_ACTIONS, statusMessageKey: 'rejected' }
    return NO_ACTIONS
  }

  // Delegated authoriser, or Co-Authorized subject/authoriser - decides for
  // themself exactly the same way regardless of which named scenario this is.
  const ownState = normalizeConsentState(own.state)

  if (state === 'PENDING') {
    if (ownState === 'PENDING') {
      return { canApprove: true, canReject: true, canRevoke: false, statusMessageKey: null }
    }
    // Already decided, but the consent is still waiting on someone else -
    // a decision can't be reopened by acting again.
    return { ...NO_ACTIONS, statusMessageKey: 'decisionRecordedWaiting' }
  }
  if (state === 'ACTIVE') {
    // Anyone with a stake in it can revoke once active, not only whoever approved it.
    return { ...NO_ACTIONS, canRevoke: true }
  }
  if (state === 'REJECTED') {
    return { ...NO_ACTIONS, statusMessageKey: ownState === 'REJECTED' ? 'youRejected' : 'rejected' }
  }
  return NO_ACTIONS
}

/**
 * Self-service callers (the authorize pre-flight check, the consent list
 * table) only ever see a consent they're already involved in, so an empty
 * `authorizations` list always means the caller is its subject - passing
 * `currentUserId` as the subject is safe here and keeps these independent of
 * the admin surface's own `getSelfConsentActionView(subjectId, ...)` call,
 * which can't assume that.
 */
export function isApprovableByCurrentUser(
  consentState: ConsentState | string,
  authorizations: ConsentAuthorization[] | undefined,
  currentUserId: string,
): boolean {
  return getSelfConsentActionView(currentUserId, consentState, authorizations, currentUserId)
    .canApprove
}

/** Mirrors `isApprovableByCurrentUser` for the reject action. */
export function isRejectableByCurrentUser(
  consentState: ConsentState | string,
  authorizations: ConsentAuthorization[] | undefined,
  currentUserId: string,
): boolean {
  return getSelfConsentActionView(currentUserId, consentState, authorizations, currentUserId)
    .canReject
}

/**
 * Administrative oversight gating - looks only at the consent's own state,
 * never at involvement or scenario. An admin can revoke a still-`PENDING`
 * request outright, not only wind down an `ACTIVE` one, but never approves or
 * rejects on someone else's behalf; that stays with the named authorisers.
 */
export function isRevokableByAdmin(consentState: ConsentState | string): boolean {
  const state = normalizeConsentState(consentState)
  return state === 'PENDING' || state === 'ACTIVE'
}

/** The status message an admin sees for a state that offers no admin action - see `isRevokableByAdmin`. */
export function getAdminConsentStatusMessageKey(
  consentState: ConsentState | string,
): ConsentStatusMessageKey | null {
  const state = normalizeConsentState(consentState)
  if (state === 'REJECTED') return 'rejected'
  if (state === 'REVOKED') return 'revoked'
  if (state === 'EXPIRED') return 'expired'
  return null
}

/**
 * Whether the signed-in user is deciding on this consent for someone else - one
 * of its authorizers rather than its subject. Only meaningful on the
 * self-service surfaces, where every consent is one or the other. False when
 * either ID is missing, since guessing would mislabel the caller's own consent.
 */
export function isManagedByCurrentUser(
  subjectId: string | undefined,
  currentUserId: string,
): boolean {
  return Boolean(subjectId) && Boolean(currentUserId) && subjectId !== currentUserId
}
