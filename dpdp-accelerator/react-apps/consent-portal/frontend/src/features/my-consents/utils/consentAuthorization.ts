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
import { isConsentApprovableState, isConsentRejectableState } from './statusChip'

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
 * The state to gate the signed-in user's own approve/reject buttons on.
 *
 * A consent's aggregate `state` can stay `PENDING` while it's waiting on
 * other authorizers after the current user has already recorded their own
 * decision - gating purely on the aggregate state would keep showing them an
 * action button for a decision they already made. When the caller has their
 * own authorization entry, that entry - not the aggregate state - is
 * authoritative for whether they can still act.
 */
export function effectiveActionState(
  consentState: ConsentState | string,
  authorizations: ConsentAuthorization[] | undefined,
  currentUserId: string,
): string {
  return myAuthorization(authorizations, currentUserId)?.state ?? consentState
}

export function isApprovableByCurrentUser(
  consentState: ConsentState | string,
  authorizations: ConsentAuthorization[] | undefined,
  currentUserId: string,
): boolean {
  return isConsentApprovableState(effectiveActionState(consentState, authorizations, currentUserId))
}

export function isRejectableByCurrentUser(
  consentState: ConsentState | string,
  authorizations: ConsentAuthorization[] | undefined,
  currentUserId: string,
): boolean {
  return isConsentRejectableState(effectiveActionState(consentState, authorizations, currentUserId))
}
