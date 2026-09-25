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
  const catalog = await seedCatalogViaApi(adminApi)
  return seedConsentForCatalogViaApi(adminApi, catalog, {
    subjectId,
    state,
    serviceId,
    expiryTime,
    authorizations,
  })
}

/** The Element and Purpose a consent is recorded against - see seedCatalogViaApi. */
export interface SeededCatalog {
  purposeId: string
  purposeName: string
  elementId: string
  elementDisplayName: string
}

/**
 * One fresh Element and Purpose, for a test that seeds several consents and has no need for each
 * to have its own - every further consent is then one API call instead of three
 * (seedConsentForCatalogViaApi). Consents never interfere through a shared Purpose: the v2 API
 * keeps them independent, the same property seedConsentViaApi already relies on by attaching no
 * elements to the Purpose itself.
 */
export async function seedCatalogViaApi(adminApi: ConsentApiClient): Promise<SeededCatalog> {
  const element = randomElementProfile()
  const elementResponse = await adminApi.createElement({
    name: element.name,
    displayName: element.displayName,
    description: element.description,
  })
  expect(elementResponse.status()).toBe(201)
  const elementId = ((await elementResponse.json()) as { id: string }).id

  const purpose = randomPurposeProfile()
  const purposeResponse = await adminApi.createPurpose({
    name: purpose.name,
    type: purpose.type,
    version: 'v1',
    description: purpose.description,
  })
  expect(purposeResponse.status()).toBe(201)
  const purposeId = ((await purposeResponse.json()) as { id: string }).id

  return { purposeId, purposeName: purpose.name, elementId, elementDisplayName: element.displayName }
}

export interface ConsentSeed {
  subjectId: string
  state: 'ACTIVE' | 'REJECTED' | 'PENDING'
  serviceId?: string
  /** Epoch millis. Omit for no expiry. */
  expiryTime?: number
  /** Only meaningful for PENDING - see seedConsentViaApi's parameter of the same name. */
  authorizations?: AuthorizationEntry[]
}

/** A consent against an existing catalog (seedCatalogViaApi) - see seedConsentViaApi for the rest. */
export async function seedConsentForCatalogViaApi(
  adminApi: ConsentApiClient,
  catalog: SeededCatalog,
  seed: ConsentSeed,
): Promise<SeededConsent> {
  const serviceId = seed.serviceId ?? randomServiceId()
  const authorizations = seed.authorizations ?? [{ userId: seed.subjectId, type: 'USER' }]
  const consentResponse = await adminApi.createConsent({
    subjectId: seed.subjectId,
    serviceId,
    // `language` is optional per consent-management-v2.yaml, but omitting it 500s: the
    // underlying CM_RECEIPT.LANGUAGE DB column is NOT NULL with no server-side default (IS
    // returns a generic CM_00084 "Internal server error" wrapping an H2 NULL-not-allowed
    // constraint violation on that column). Tracked as a real product bug, not a test bug -
    // recorded in TEST-SCENARIOS.md.
    language: 'en',
    purposes: [{ id: catalog.purposeId, elements: [{ id: catalog.elementId }] }],
    ...(seed.state === 'PENDING' ? { authorizations } : { state: seed.state }),
    ...(seed.expiryTime === undefined ? {} : { expiryTime: seed.expiryTime }),
  })
  expect(consentResponse.status()).toBe(201)
  const consent = (await consentResponse.json()) as { id: string }

  return {
    consentId: consent.id,
    purposeName: catalog.purposeName,
    elementDisplayName: catalog.elementDisplayName,
    serviceId,
  }
}

type ConsentState = 'PENDING' | 'ACTIVE' | 'REJECTED' | 'REVOKED' | 'EXPIRED'

/**
 * The consent's state as `api`'s owner sees it - a precondition check, so a setup step that
 * didn't take effect fails as that, not later as a misleading assertion on whatever reads it.
 */
export async function expectConsentStateViaApi(
  api: ConsentApiClient,
  consentId: string,
  state: ConsentState,
): Promise<void> {
  const response = await api.getMyConsent(consentId)
  expect(response.status()).toBe(200)
  expect(((await response.json()) as { state: string }).state).toBe(state)
}

/** Records `api`'s owner's decision on a consent they are an authorizer of. */
export async function authorizeConsentViaApi(
  api: ConsentApiClient,
  consentId: string,
  decision: 'APPROVED' | 'REJECTED',
): Promise<void> {
  const response = await api.authorizeMyConsent(consentId, decision)
  expect(response.ok(), `authorize ${decision}: ${await response.text()}`).toBe(true)
}

/**
 * Revokes a consent as `api`'s owner and checks it took effect. The self-service revoke is IS's
 * authorize-with-REVOKED on the caller's own authorization, so the caller must be either the
 * subject of a consent with no authorizations, or one of its authorizers - a subject revoking a
 * consent that has authorizers is a silent no-op (see TEST-SCENARIOS.md, "Product bugs the tests
 * work around"), which the check here would catch.
 */
export async function revokeConsentViaApi(api: ConsentApiClient, consentId: string): Promise<void> {
  const response = await api.revokeMyConsent(consentId)
  expect(response.ok(), `revoke: ${await response.text()}`).toBe(true)
  await expectConsentStateViaApi(api, consentId, 'REVOKED')
}

/**
 * How far ahead to set an expiry that a test then waits out. Short, because the wait is real time;
 * long enough that the consent is still unexpired when created, whatever the server's latency.
 * IS resolves EXPIRED when a consent is read (an ACTIVE/PENDING receipt past its EXPIRY_TIME - see
 * TEST-SCENARIOS.md), so no expiry job has to run first.
 */
export const SHORT_EXPIRY_MS = 5_000

/**
 * Waits until IS reports the consent as EXPIRED to `api`'s owner. Seed the expiring consent first
 * and call this after the rest of a test's setup, so the wait mostly overlaps that setup.
 */
export async function waitUntilConsentExpiredViaApi(api: ConsentApiClient, consentId: string): Promise<void> {
  await expect
    .poll(
      async () => {
        const response = await api.getMyConsent(consentId)
        return ((await response.json()) as { state?: string }).state
      },
      { timeout: SHORT_EXPIRY_MS + 15_000, intervals: [1_000] },
    )
    .toBe('EXPIRED')
}
