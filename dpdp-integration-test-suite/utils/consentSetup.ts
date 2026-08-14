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

import { expect, type Page } from '@playwright/test'
import type { ConsentApiClient } from '../clients/ConsentApiClient'
import type { ConsentCleanupTracker } from '../fixtures/auth.fixtures'
import { ElementFormDialog } from '../pages/ElementFormDialog'
import { ElementListPage } from '../pages/ElementListPage'
import { PurposeFormDialog } from '../pages/PurposeFormDialog'
import { PurposeListPage } from '../pages/PurposeListPage'
import { uniqueElementName, uniquePurposeName, uniqueServiceId } from './testData'

export interface SeededConsent {
  consentId: string
  purposeName: string
  elementDisplayName: string
  serviceId: string
}

/** Reads the id out of a `.../<segment>/<id>` detail URL, e.g. after a create-form redirect. */
function idFromDetailUrl(url: string, segment: 'elements' | 'purposes'): string {
  const match = new RegExp(`/${segment}/([^/]+)$`).exec(url)
  if (!match) {
    throw new Error(`Could not read an id out of the ${segment} detail URL: ${url}`)
  }
  return match[1]
}

/**
 * Only Consent creation has no create UI at all (see tests/consents/README.md), so it's the one
 * step here that goes through the admin API; the Element and Purpose it needs are created
 * through the real "Add Element" / "Add Purpose" admin UI forms, same as a real admin would use.
 *
 * The Purpose is created with no elements attached - the consent-mgt v2 API records whichever
 * elements a Consent's own `purposes[].elements[]` lists independently of what the Purpose
 * definition itself requires, so there's no need to fight the Purpose form's element picker (see
 * PurposeFormDialog.addElements's own comments on its quirks) just to satisfy this helper.
 *
 * `state: 'PENDING'` supplies `authorizations` instead of `state` - the consent-mgt v2 API sets
 * PENDING automatically when authorizations are present and rejects an explicit PENDING state.
 *
 * The Element and Purpose created are registered with `tracker` (see fixtures/auth.fixtures.ts)
 * so they're deleted again once the calling test finishes - this helper's whole point is
 * disposable, test-specific setup, never the persistent realistic demo dataset.
 */
export async function seedConsent(
  adminPage: Page,
  adminApi: ConsentApiClient,
  tracker: ConsentCleanupTracker,
  subjectId: string,
  state: 'ACTIVE' | 'REJECTED' | 'PENDING',
  serviceId: string = uniqueServiceId(),
): Promise<SeededConsent> {
  const elementName = uniqueElementName()
  const elementDisplayName = `Test Element ${elementName}`

  const elementListPage = new ElementListPage(adminPage)
  await elementListPage.goto()
  await elementListPage.openCreateDialog()
  const elementDialog = new ElementFormDialog(adminPage)
  await elementDialog.fill({ name: elementName, displayName: elementDisplayName })
  await elementDialog.submit()
  await expect(adminPage).toHaveURL(/\/elements\/[^/]+$/)
  const elementId = idFromDetailUrl(adminPage.url(), 'elements')
  tracker.trackElement(elementId)

  const purposeName = uniquePurposeName()
  const purposeListPage = new PurposeListPage(adminPage)
  await purposeListPage.goto()
  await purposeListPage.openCreateDialog()
  const purposeDialog = new PurposeFormDialog(adminPage)
  await purposeDialog.fill({ name: purposeName, type: 'Marketing', version: 'v1' })
  await purposeDialog.submit()
  await expect(adminPage).toHaveURL(/\/purposes\/[^/]+$/)
  const purposeId = idFromDetailUrl(adminPage.url(), 'purposes')
  tracker.trackPurpose(purposeId)

  const consentResponse = await adminApi.createConsent({
    subjectId,
    serviceId,
    // `language` is optional per consent-management-v2.yaml, but omitting it 500s: the
    // underlying CM_RECEIPT.LANGUAGE DB column is NOT NULL with no server-side default (verified
    // live - IS returns a generic CM_00084 "Internal server error" wrapping an
    // H2 NULL-not-allowed constraint violation on that column). Tracked as a real product bug,
    // not a test bug - see tests/consents/README.md.
    language: 'en',
    purposes: [{ id: purposeId, elements: [{ id: elementId }] }],
    ...(state === 'PENDING'
      ? { authorizations: [{ userId: subjectId, type: 'USER' }] }
      : { state }),
  })
  expect(consentResponse.status()).toBe(201)
  const consent = (await consentResponse.json()) as { id: string }

  return { consentId: consent.id, purposeName, elementDisplayName, serviceId }
}
