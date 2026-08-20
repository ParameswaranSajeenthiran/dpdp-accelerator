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

/** The Identity Server's self-service consent API. */
const SELF_CONSENTS = '/api/users/v1/me/consents'

const jsonHeaders = { 'Content-Type': 'application/json' }

/** Matches the page size the Identity Server list endpoint will serve at once. */
const MAX_FETCH = 200
const DEFAULT_PAGE_SIZE = 10

function consentPath(consentID: string, suffix = ''): string {
  return `${SELF_CONSENTS}/${encodeURIComponent(consentID)}${suffix}`
}

function summaryAsDetail(summary: ConsentSummary): ConsentDetail {
  return { ...summary, purposes: [] }
}

/**
 * Lists the signed in user's consents.
 *
 * The Identity Server returns a cursor based array of
 * {@code {id, serviceId, state, timestamp}} with no grand total and no offset
 * support, while the table wants an offset page of full records. Over-fetch by
 * one page, slice locally, then expand each row on the page with a detail
 * lookup.
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
    },
  })

  const page = summaries.slice(offset, offset + limit)
  const data = await Promise.all(
    page.map(async (summary) => {
      try {
        return await fetchMyConsentByID(summary.id)
      } catch {
        // One failed lookup must not blank the whole page.
        return summaryAsDetail(summary)
      }
    }),
  )

  return {
    data,
    metadata: {
      // Cursor based upstream: this counts what has been seen, not the total.
      total: summaries.length,
      offset,
      count: data.length,
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
 * The server accepts authorize on a consent in any state, which would move an
 * already revoked or expired consent back to active. A withdrawal has to stay
 * final, so only a pending consent is authorizable.
 */
async function authorizeMyConsent(consentID: string, state: 'APPROVED' | 'REJECTED') {
  const consent = await fetchMyConsentByID(consentID)
  if (consent.state !== 'PENDING') {
    throw new APIError(
      409,
      'INVALID_CONSENT_STATE',
      `Only a pending consent can be approved or rejected; this consent is ${consent.state}.`,
    )
  }

  await apiRequestOptionalContent(consentPath(consentID, '/authorize'), {
    method: 'POST',
    headers: jsonHeaders,
    body: JSON.stringify({ state }),
  })
  return { status: 'OK' }
}

export async function approveMyConsent(consentID: string): Promise<unknown> {
  return authorizeMyConsent(consentID, 'APPROVED')
}

export async function rejectMyConsent(consentID: string): Promise<unknown> {
  return authorizeMyConsent(consentID, 'REJECTED')
}

export async function revokeMyConsent(consentID: string): Promise<unknown> {
  // The Identity Server answers revoke with an empty body.
  await apiRequestOptionalContent(consentPath(consentID, '/revoke'), { method: 'POST' })
  return { status: 'OK' }
}
