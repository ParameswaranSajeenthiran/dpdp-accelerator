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
import { test, expect, type ConsentCleanupTracker } from '../../fixtures/auth.fixtures'
import { ElementDetailPage } from '../../pages/ElementDetailPage'
import { ElementFormDialog } from '../../pages/ElementFormDialog'
import { ElementListPage } from '../../pages/ElementListPage'
import { uniqueElementName } from '../../utils/testData'

/**
 * Every Element in this file is created through the real "Add Element" UI form - Consent is the
 * only thing in this suite created via the admin API (it has no create UI at all). Even tests
 * that only need an Element to already exist (as setup for a Purpose dependency, or to check
 * pagination) go through the create dialog rather than the API. Every Element this file creates
 * is registered with `tracker` so it's deleted again once the test finishes - see
 * fixtures/auth.fixtures.ts's ConsentCleanupTracker.
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

test.describe('Element catalog (UI)', () => {
  test.describe('Happy paths', () => {
    test('01.01.01 - Creates an element through the Add Element form with a display name, description, and property', async ({
      consentAdminPage,
      consentCleanupTracker,
    }) => {
      const listPage = new ElementListPage(consentAdminPage)
      await listPage.goto()
      await listPage.openCreateDialog()

      const elementName = uniqueElementName()
      const dialog = new ElementFormDialog(consentAdminPage)
      await dialog.fill({
        name: elementName,
        displayName: 'Shipping Address',
        description: 'Address used to ship physical orders.',
      })
      await dialog.addProperty('dataCategory', 'personal')
      await dialog.submit()

      // On success the dialog closes and navigates straight to the new element's detail page.
      await expect(consentAdminPage).toHaveURL(/\/elements\/[^/]+$/)
      const match = /\/elements\/([^/]+)$/.exec(consentAdminPage.url())
      if (match) {
        consentCleanupTracker.trackElement(match[1])
      }
      const detailPage = new ElementDetailPage(consentAdminPage)
      await expect(detailPage.heading('Shipping Address')).toBeVisible()
      await expect(
        consentAdminPage.getByText('Address used to ship physical orders.'),
      ).toBeVisible()
      await expect(consentAdminPage.getByText('personal', { exact: true })).toBeVisible()

      // Also findable back on the list via the new name search.
      await listPage.goto()
      await listPage.searchByName(elementName)
      await expect(listPage.rowByName(elementName)).toBeVisible()
    })

  

    test('01.01.02 - The list renders and its rows-per-page control accepts a new page size without erroring', async ({
      consentAdminPage,
      consentCleanupTracker,
    }) => {
      // Seeded so the list is guaranteed non-empty regardless of what earlier runs left behind.
      await createElementViaUi(consentAdminPage, consentCleanupTracker)

      const listPage = new ElementListPage(consentAdminPage)
      await listPage.goto()
      await expect(listPage.heading).toBeVisible()
      await expect(listPage.table.getByRole('row')).not.toHaveCount(0)
      await expect(listPage.previousPageButton).toBeDisabled()
      await listPage.setRowsPerPage(25)
      await expect(listPage.table).toBeVisible()
    })

    test('01.01.03 - The rows-per-page control caps the number of rendered rows at the selected size', async ({
      consentAdminPage,
      consentCleanupTracker,
    }) => {
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
    })
  })

  test.describe('Validation violations', () => {
    test('01.01.04 - An unknown element id shows the load-failed message with a way back to the list', async ({
      consentAdminPage,
    }) => {
      const detailPage = new ElementDetailPage(consentAdminPage)
      await detailPage.goto('00000000-0000-0000-0000-000000000000')
      await expect(detailPage.loadFailedMessage).toBeVisible()
      await detailPage.backButton.click()
      await expect(consentAdminPage).toHaveURL(/\/elements$/)
    })

    test('01.01.05 - Leaving name empty shows the required-field error and blocks submission', async ({
      consentAdminPage,
    }) => {
      const listPage = new ElementListPage(consentAdminPage)
      await listPage.goto()
      await listPage.openCreateDialog()

      const dialog = new ElementFormDialog(consentAdminPage)
      await dialog.blurName()
      await dialog.submit()

      await expect(dialog.root.getByText('Name is required.')).toBeVisible()
      await expect(dialog.root).toBeVisible()
    })

    test('01.01.06 - Creating an element with a name that already exists shows the duplicate-name message', async ({
      consentAdminPage,
      consentCleanupTracker,
    }) => {
      const elementName = uniqueElementName()

      const listPage = new ElementListPage(consentAdminPage)
      await listPage.goto()
      await listPage.openCreateDialog()
      const firstDialog = new ElementFormDialog(consentAdminPage)
      await firstDialog.fill({ name: elementName })
      await firstDialog.submit()
      await expect(consentAdminPage).toHaveURL(/\/elements\/[^/]+$/)
      const match = /\/elements\/([^/]+)$/.exec(consentAdminPage.url())
      if (match) {
        consentCleanupTracker.trackElement(match[1])
      }

      await listPage.goto()
      await listPage.openCreateDialog()
      const dialog = new ElementFormDialog(consentAdminPage)
      await dialog.fill({ name: elementName })
      await dialog.submit()

      await expect(
        dialog.root.getByText(`An element named "${elementName}" already exists. Choose a different name.`),
      ).toBeVisible()
      // Still on the dialog - the duplicate was rejected, not silently created twice.
      await expect(dialog.root).toBeVisible()
    })

    test('01.01.07 - A property value with no key blocks submission until the key is filled in or the row is removed', async ({
      consentAdminPage,
    }) => {
      const listPage = new ElementListPage(consentAdminPage)
      await listPage.goto()
      await listPage.openCreateDialog()

      const dialog = new ElementFormDialog(consentAdminPage)
      await dialog.fill({ name: uniqueElementName() })
      await dialog.addProperty('', 'orphaned-value')

      await expect(dialog.root.getByText('Add a key, or this value will not be saved.')).toBeVisible()
      await expect(dialog.createButton).toBeDisabled()
    })
  })
})
