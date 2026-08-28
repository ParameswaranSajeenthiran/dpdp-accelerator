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
  ConsentDetail,
  ConsentListQueryParams,
  ConsentSearchResponse,
  ConsentSummary,
} from '../../../types/consent'
import { APIError, apiRequest, apiRequestOptionalContent } from '../../../utils/apiClient'
import { isApprovableByCurrentUser, isRejectableByCurrentUser } from '../utils/consentAuthorization'

/** The Identity Server's self-service consent API. */
const SELF_CONSENTS = '/api/users/v1/me/consents'

const jsonHeaders = { 'Content-Type': 'application/json' }

/** Matches the page size the Identity Server list endpoint will serve at once. */
const MAX_FETCH = 200
const DEFAULT_PAGE_SIZE = 10

function consentPath(consentID: string, suffix = ''): string {
  return `${SELF_CONSENTS}/${encodeURIComponent(consentID)}${suffix}`
}

/**
 * Lists the signed in user's consents.
 *
 * The Identity Server returns a cursor based array with no grand total and no
 * offset support, while the table wants an offset page. Over-fetch by one
 * page and slice locally. `attributes=purposes,authorizations` asks the list
 * endpoint to inline what the table needs directly, so no per-row detail
 * lookup is required here.
 */
export async function fetchMyConsents(
  params: ConsentListQueryParams,
): Promise<ConsentSearchResponse> {
  const limit = params.limit > 0 ? params.limit : DEFAULT_PAGE_SIZE
  const offset = Math.max(0, params.offset)

  const summaries = await apiRequest<ConsentSummary[]>(SELF_CONSENTS, {
    method: 'GET',
    query: {
      limit: Math.min(offset + limit + 1, MAX_FETCH),
      serviceId: params.serviceId ? params.serviceId : undefined,
      state: params.state,
      relation: params.relation,
      filter: params.filter,
      attributes: 'purposes,authorizations',
    },
  })

  const page = summaries.slice(offset, offset + limit)

  return {
    data: page,
    metadata: {
      // Cursor based upstream: this counts what has been seen, not the total.
      total: summaries.length,
      offset,
      count: page.length,
      limit,
    },
  }
}

export async function fetchMyConsentByID(consentID: string): Promise<ConsentDetail> {
  return apiRequest<ConsentDetail>(consentPath(consentID), { method: 'GET' })
}

/**
 * Approves or rejects a consent as a whole - the Identity Server has no per
 * element authorization.
 *
 * A caller can reconsider their own prior decision (approve after rejecting,
 * or vice versa) as long as it's still theirs to decide. The server accepts
 * authorize on a consent in any state, which would otherwise let an already
 * revoked or expired consent be pulled back to active - a withdrawal has to
 * stay final, so those two states block both actions regardless of who asks.
 * `currentUserId` defaults to empty for callers with no signed-in identity to
 * check against, which falls back to gating on the consent's own aggregate
 * state - the same behavior this function had before per-authorizer decisions
 * existed.
 */
async function authorizeMyConsent(
  consentID: string,
  state: 'APPROVED' | 'REJECTED',
  currentUserId = '',
): Promise<unknown> {
  const consent = await fetchMyConsentByID(consentID)
  const canAct =
    state === 'APPROVED'
      ? isApprovableByCurrentUser(consent.state, consent.authorizations, currentUserId)
      : isRejectableByCurrentUser(consent.state, consent.authorizations, currentUserId)

  if (!canAct) {
    throw new APIError(
      409,
      'INVALID_CONSENT_STATE',
      `This consent cannot be ${state === 'APPROVED' ? 'approved' : 'rejected'} right now; it is ${consent.state}.`,
    )
  }

  await apiRequestOptionalContent(consentPath(consentID, '/authorize'), {
    method: 'POST',
    headers: jsonHeaders,
    body: JSON.stringify({ state }),
  })
  return { status: 'OK' }
}

export async function approveMyConsent(consentID: string, currentUserId = ''): Promise<unknown> {
  return authorizeMyConsent(consentID, 'APPROVED', currentUserId)
}

export async function rejectMyConsent(consentID: string, currentUserId = ''): Promise<unknown> {
  return authorizeMyConsent(consentID, 'REJECTED', currentUserId)
}

export async function revokeMyConsent(consentID: string): Promise<unknown> {
  // The Identity Server answers revoke with an empty body.
  await apiRequestOptionalContent(consentPath(consentID, '/revoke'), { method: 'POST' })
  return { status: 'OK' }
}
