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
import { test, expect, loginAsConsentAdmin, type ConsentCleanupTracker } from '../../fixtures/auth.fixtures'
import { ElementDetailPage } from '../../pages/ElementDetailPage'
import { ElementFormDialog } from '../../pages/ElementFormDialog'
import { ElementListPage } from '../../pages/ElementListPage'
import { randomElementProfile, uniqueElementName } from '../../utils/testData'

/**
 * The read-only Elements list at /elements: rendering, pagination, the load-failed path for a bad
 * detail-page id, and that a created element's fields actually round-trip through the detail
 * page. Elements seeded here only exist to give the list rows to page through - see
 * tests/02-elements/02.01-admin-creating-elements.spec.ts for creation-form validation.
 */

/** Creates an element through the UI, tracks it for cleanup, and returns its id. */
async function createElementViaUi(page: Page, tracker: ConsentCleanupTracker): Promise<string> {
  const listPage = new ElementListPage(page)
  await listPage.goto()
  await listPage.openCreateDialog()
  const dialog = new ElementFormDialog(page)
  await dialog.fill({ name: uniqueElementName() })
  await dialog.submit()
  await expect(page).toHaveURL(/\/elements\/[^/]+$/)
  const match = /\/elements\/([^/]+)$/.exec(page.url())
  if (!match) {
    throw new Error(`Could not read an element id out of the detail URL: ${page.url()}`)
  }
  tracker.trackElement(match[1])
  return match[1]
}

test.describe('Admin viewing the Elements list (UI)', () => {
  test('02.02.01 - The list renders and its rows-per-page control accepts a new page size without erroring', async ({
    browser,
    consentCleanupTracker,
  }) => {
    const consentAdminPage = await loginAsConsentAdmin(browser)
    // Seeded so the list is guaranteed non-empty regardless of what earlier runs left behind.
    await createElementViaUi(consentAdminPage, consentCleanupTracker)

    const listPage = new ElementListPage(consentAdminPage)
    await listPage.goto()
    await expect(listPage.heading).toBeVisible()
    await expect(listPage.table.getByRole('row')).not.toHaveCount(0)
    await expect(listPage.previousPageButton).toBeDisabled()
    await listPage.setRowsPerPage(25)
    await expect(listPage.table).toBeVisible()
    await consentAdminPage.context().close()
  })

  test('02.02.02 - The rows-per-page control caps the number of rendered rows at the selected size', async ({
    browser,
    consentCleanupTracker,
  }) => {
    const consentAdminPage = await loginAsConsentAdmin(browser)
    // One more than the smallest page size, so there's guaranteed to be a next page regardless
    // of how many elements earlier runs already left in this shared environment.
    const seedCount = 11
    // Each creation is its own UI round-trip - sequential by design, not perf-sensitive.
    for (let i = 0; i < seedCount; i += 1) {
      await createElementViaUi(consentAdminPage, consentCleanupTracker)
    }

    const listPage = new ElementListPage(consentAdminPage)
    await listPage.goto()
    await listPage.setRowsPerPage(10)

    await expect(listPage.rows).toHaveCount(10)
    await expect(listPage.nextPageButton).toBeEnabled()
    await consentAdminPage.context().close()
  })

  test('02.02.03 - An unknown element id shows the load-failed message with a way back to the list', async ({
    browser,
  }) => {
    const consentAdminPage = await loginAsConsentAdmin(browser)
    const detailPage = new ElementDetailPage(consentAdminPage)
    await detailPage.goto('00000000-0000-0000-0000-000000000000')
    await expect(detailPage.loadFailedMessage).toBeVisible()
    await detailPage.backButton.click()
    await expect(consentAdminPage).toHaveURL(/\/elements$/)
    await consentAdminPage.context().close()
  })

  test("02.02.04 - A newly created element's detail page shows its display name, description, and properties correctly", async ({
    browser,
    consentCleanupTracker,
  }) => {
    const consentAdminPage = await loginAsConsentAdmin(browser)
    const profile = randomElementProfile()
    const properties = { retention_days: '365', encryption: 'AES-256' }

    const listPage = new ElementListPage(consentAdminPage)
    await listPage.goto()
    await listPage.openCreateDialog()
    const createDialog = new ElementFormDialog(consentAdminPage)
    await createDialog.fill({ name: profile.name, displayName: profile.displayName, description: profile.description })
    for (const [key, value] of Object.entries(properties)) {
      await createDialog.addProperty(key, value)
    }
    await createDialog.submit()
    await expect(consentAdminPage).toHaveURL(/\/elements\/[^/]+$/)
    const elementId = /\/elements\/([^/]+)$/.exec(consentAdminPage.url())?.[1]
    if (!elementId) {
      throw new Error(`Could not read an element id out of the detail URL: ${consentAdminPage.url()}`)
    }
    consentCleanupTracker.trackElement(elementId)

    // A fresh navigation, not just the post-submit redirect - proves the server actually
    // persisted every field, not just that the create form's own optimistic state looked right.
    const detailPage = new ElementDetailPage(consentAdminPage)
    await detailPage.goto(elementId)
    await expect(detailPage.nameValue(profile.name)).toBeVisible()
    await expect(detailPage.fieldValue('Display name')).toHaveText(profile.displayName)
    await expect(detailPage.fieldValue('Description')).toHaveText(profile.description)
    for (const [key, value] of Object.entries(properties)) {
      await expect(detailPage.propertyRow(key)).toContainText(value)
    }
    await consentAdminPage.context().close()
  })
})
