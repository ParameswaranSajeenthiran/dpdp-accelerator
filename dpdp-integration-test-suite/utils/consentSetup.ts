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

import { expect } from '@playwright/test'
import type { AuthorizationEntry, ConsentApiClient } from '../clients/ConsentApiClient'
import { randomElementProfile, randomPurposeProfile, randomServiceId } from './testData'

export interface SeededConsent {
  consentId: string
  purposeName: string
  elementDisplayName: string
  serviceId: string
}

/**
 * Element, Purpose, and Consent are all created via the admin API, not their UI forms - none of
 * this helper's callers are testing purpose/element creation itself (see
 * tests/02-elements/02.01-admin-creating-elements.spec.ts and
 * tests/03-purposes/03.01-admin-creating-purposes.spec.ts for that coverage), and Consent has no
 * create UI at all.
 *
 * The Purpose is created with no elements attached - the consent-mgt v2 API records whichever
 * elements a Consent's own `purposes[].elements[]` lists independently of what the Purpose
 * definition itself requires.
 *
 * `state: 'PENDING'` supplies `authorizations` instead of `state` - the consent-mgt v2 API sets
 * PENDING automatically when authorizations are present and rejects an explicit PENDING state.
 *
 * The Element, Purpose, and Consent created are never deleted - same as every other Consent in
 * this suite (there's no delete-by-id for Consents at all), they accumulate in the shared
 * environment for good.
 */
export async function seedConsentViaApi(
  adminApi: ConsentApiClient,
  subjectId: string,
  state: 'ACTIVE' | 'REJECTED' | 'PENDING',
  serviceId: string = randomServiceId(),
  /** Epoch millis. Omit for no expiry - only the consent-expiry reconciliation tests need this. */
  expiryTime?: number,
  /**
   * Only meaningful when `state === 'PENDING'`. Defaults to a single self-authorization (the
   * subject approving their own consent) - every caller before this parameter existed relied on
   * exactly that. Pass a different `userId`/`type` (e.g. `{ userId: parentId, type: 'PARENT' }`)
   * to seed a delegated consent instead, where the subject and the authorizer are different
   * people - see tests/04-consents/04.07-user-viewing-consent-history.spec.ts's delegated-consent
   * case.
   */
  authorizations: AuthorizationEntry[] = [{ userId: subjectId, type: 'USER' }],
): Promise<SeededConsent> {
  const element = randomElementProfile()
  const elementDisplayName = element.displayName

  const elementResponse = await adminApi.createElement({
    name: element.name,
    displayName: elementDisplayName,
    description: element.description,
  })
  expect(elementResponse.status()).toBe(201)
  const elementId = ((await elementResponse.json()) as { id: string }).id

  const purpose = randomPurposeProfile()
  const purposeName = purpose.name
  const purposeResponse = await adminApi.createPurpose({
    name: purposeName,
    type: purpose.type,
    version: 'v1',
    description: purpose.description,
  })
  expect(purposeResponse.status()).toBe(201)
  const purposeId = ((await purposeResponse.json()) as { id: string }).id

  const consentResponse = await adminApi.createConsent({
    subjectId,
    serviceId,
    // `language` is optional per consent-management-v2.yaml, but omitting it 500s: the
    // underlying CM_RECEIPT.LANGUAGE DB column is NOT NULL with no server-side default (as seen
    // live - IS returns a generic CM_00084 "Internal server error" wrapping an
    // H2 NULL-not-allowed constraint violation on that column). Tracked as a real product bug,
    // not a test bug - recorded in TEST-SCENARIOS.md.
    language: 'en',
    purposes: [{ id: purposeId, elements: [{ id: elementId }] }],
    ...(state === 'PENDING' ? { authorizations } : { state }),
    ...(expiryTime === undefined ? {} : { expiryTime }),
  })
  expect(consentResponse.status()).toBe(201)
  const consent = (await consentResponse.json()) as { id: string }

  return { consentId: consent.id, purposeName, elementDisplayName, serviceId }
}
