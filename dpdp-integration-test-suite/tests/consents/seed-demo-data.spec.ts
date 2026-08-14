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

import type { Page } from '@playwright/test'
import { test, expect } from '../../fixtures/auth.fixtures'
import type { ConsentApiClient, CreateElementBody } from '../../clients/ConsentApiClient'
import {
  RICH_ELEMENTS,
  RICH_PURPOSES,
  type ConsentTargetState,
  type NamedRecord,
  type RichPurposeDefinition,
} from '../../utils/consentCleanup'
import { env } from '../../utils/env'
import { ElementFormDialog } from '../../pages/ElementFormDialog'
import { ElementListPage } from '../../pages/ElementListPage'
import { PurposeFormDialog } from '../../pages/PurposeFormDialog'
import { PurposeListPage } from '../../pages/PurposeListPage'

/** Reads the id out of a `.../<segment>/<id>` detail URL, e.g. after a create-form redirect. */
function idFromDetailUrl(url: string, segment: 'elements' | 'purposes'): string {
  const match = new RegExp(`/${segment}/([^/]+)$`).exec(url)
  if (!match) {
    throw new Error(`Could not read an id out of the ${segment} detail URL: ${url}`)
  }
  return match[1]
}

/**
 * Looks up an element by its (unique) name first and only creates it through the "Add Element"
 * UI form if missing - Consent is the only thing this suite creates via the admin API.
 */
async function getOrCreateElement(
  page: Page,
  api: ConsentApiClient,
  body: CreateElementBody,
): Promise<NamedRecord> {
  const existing = await api.findElementByName(body.name)
  if (existing.status() === 200) {
    const { Elements } = (await existing.json()) as { Elements: NamedRecord[] }
    if (Elements[0]) {
      return Elements[0]
    }
  }
  const listPage = new ElementListPage(page)
  await listPage.goto()
  await listPage.openCreateDialog()
  const dialog = new ElementFormDialog(page)
  await dialog.fill(body)
  await dialog.submit()
  await expect(page).toHaveURL(/\/elements\/[^/]+$/)
  return { id: idFromDetailUrl(page.url(), 'elements'), name: body.name }
}

/**
 * Consent is the one thing in this dataset created via the admin API - it has no create UI at
 * all. `REVOKED` isn't a state the create call accepts directly (only ACTIVE/REJECTED, or
 * PENDING via `authorizations`), so it's reached by creating ACTIVE and revoking right after.
 */
async function seedRichConsent(
  api: ConsentApiClient,
  subjectId: string,
  purposeId: string,
  elementIds: string[],
  serviceId: string,
  targetState: ConsentTargetState,
): Promise<void> {
  const response = await api.createConsent({
    subjectId,
    serviceId,
    language: 'en',
    purposes: [{ id: purposeId, elements: elementIds.map((id) => ({ id })) }],
    ...(targetState === 'PENDING'
      ? { authorizations: [{ userId: subjectId, type: 'USER' }] }
      : { state: targetState === 'REVOKED' ? 'ACTIVE' : targetState }),
  })
  expect(response.status()).toBe(201)
  if (targetState === 'REVOKED') {
    const { id } = (await response.json()) as { id: string }
    const revokeResponse = await api.revokeAdminConsent(id)
    expect(revokeResponse.status()).toBe(200)
  }
}

/**
 * Same idempotency reasoning as getOrCreateElement, but also attaches a mandatory and an
 * optional element through the picker for a more realistic-looking detail page - safe to do
 * here because the elements loop above always runs first, so the picker never has zero options
 * to pick from.
 */
async function getOrCreatePurposeWithElements(
  page: Page,
  api: ConsentApiClient,
  definition: RichPurposeDefinition,
): Promise<NamedRecord> {
  const existing = await api.findPurposeByName(definition.name)
  if (existing.status() === 200) {
    const { Purposes } = (await existing.json()) as { Purposes: NamedRecord[] }
    if (Purposes[0]) {
      return Purposes[0]
    }
  }
  const listPage = new PurposeListPage(page)
  await listPage.goto()
  await listPage.openCreateDialog()
  const dialog = new PurposeFormDialog(page)
  await dialog.fill({
    name: definition.name,
    type: definition.type,
    version: 'v1',
    description: definition.description,
  })
  await dialog.addElements([true, false])
  await dialog.submit()
  await expect(page).toHaveURL(/\/purposes\/[^/]+$/)
  return { id: idFromDetailUrl(page.url(), 'purposes'), name: definition.name }
}

/**
 * Not part of the functional test suite - a maintenance operation for this shared,
 * never-auto-reset environment, meant to be run deliberately and on demand to (re-)populate a
 * realistic-looking demo dataset:
 *
 *   npx playwright test seed-demo-data.spec.ts
 *
 * The functional tests in tests/consents/consents-ui/ clean up whatever they create themselves
 * (see fixtures/auth.fixtures.ts's ConsentCleanupTracker), so this dataset is the only thing that
 * persists in the environment across runs - idempotent by design (each Element/Purpose is looked
 * up by name first, created through the real admin UI forms only if missing), so rerunning it
 * never duplicates anything.
 */
test.describe('Seed Consent environment', () => {
  test('seed a rich demo dataset (20 elements, 20 purposes)', async ({
    consentAdminPage,
    consentAdminConsentApi,
  }) => {
    test.setTimeout(300_000)

    const elements: NamedRecord[] = []
    for (const body of RICH_ELEMENTS) {
      // eslint-disable-next-line no-await-in-loop -- each creation is its own UI round-trip; sequential keeps this simple
      elements.push(await getOrCreateElement(consentAdminPage, consentAdminConsentApi, body))
    }

    const purposes: NamedRecord[] = []
    for (const definition of RICH_PURPOSES) {
      // eslint-disable-next-line no-await-in-loop -- see above
      const purpose = await getOrCreatePurposeWithElements(consentAdminPage, consentAdminConsentApi, definition)
      purposes.push(purpose)
      // Two elements per consent, cycling through the pool so different purposes reference
      // different-looking elements rather than always the same first two.
      const index = purposes.length - 1
      const elementIds = [elements[index % elements.length], elements[(index + 1) % elements.length]].map(
        (element) => element.id,
      )
      // eslint-disable-next-line no-await-in-loop -- see above
      await seedRichConsent(
        consentAdminConsentApi,
        env.dataPrincipal.username,
        purpose.id,
        elementIds,
        definition.serviceId,
        definition.consentState,
      )
    }

    expect(elements).toHaveLength(RICH_ELEMENTS.length)
    expect(purposes).toHaveLength(RICH_PURPOSES.length)

    // eslint-disable-next-line no-console -- deliberately informative output for a maintenance script
    console.log(
      `Rich demo dataset ready: ${String(elements.length)} elements, ${String(purposes.length)} purposes, ` +
        `${String(RICH_PURPOSES.length)} consents for ${env.dataPrincipal.username}.`,
    )
  })
})
