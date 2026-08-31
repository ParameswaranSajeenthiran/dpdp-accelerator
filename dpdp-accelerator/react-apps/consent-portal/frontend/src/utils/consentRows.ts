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

import type { ConsentDetail, ConsentRecord, ConsentSummary } from '../types/consent'
import { isConsentState } from '../types/consent'
import { normalizeConsentState } from '../features/my-consents/utils/statusChip'

function toRecord(
  id: string,
  subjectId: string,
  serviceId: string,
  state: string,
  timestamp: number,
  purposeNames: string[] | undefined,
  authorizations: ConsentRecord['authorizations'],
): ConsentRecord {
  const normalizedState = normalizeConsentState(state)

  if (!isConsentState(normalizedState)) {
    throw new Error(`Unsupported consent state received from API: ${state}`)
  }

  return {
    id,
    subjectId,
    serviceId,
    state: normalizedState,
    timestamp,
    purposes: purposeNames,
    authorizations,
  }
}

/** Maps a full consent record (single-consent GET) to a table row. */
export function toConsentRow(consent: ConsentDetail): ConsentRecord {
  return toRecord(
    consent.id,
    consent.subjectId,
    consent.serviceId,
    consent.state,
    consent.timestamp,
    consent.purposes.map((purpose) => purpose.name),
    consent.authorizations,
  )
}

/** Maps a list-endpoint summary (requested with `attributes=purposes,authorizations`) to a table row. */
export function toConsentRowFromSummary(consent: ConsentSummary): ConsentRecord {
  return toRecord(
    consent.id,
    consent.subjectId,
    consent.serviceId,
    consent.state,
    consent.timestamp,
    consent.purposes?.map((purpose) => purpose.name),
    consent.authorizations,
  )
}
