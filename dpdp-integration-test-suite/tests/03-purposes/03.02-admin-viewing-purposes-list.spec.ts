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

import { test, expect, loginAsConsentAdmin } from '../../fixtures/auth.fixtures'
import { ElementFormDialog } from '../../pages/ElementFormDialog'
import { ElementListPage } from '../../pages/ElementListPage'
import { PurposeDetailPage } from '../../pages/PurposeDetailPage'
import { PurposeFormDialog } from '../../pages/PurposeFormDialog'
import { PurposeListPage } from '../../pages/PurposeListPage'
import { randomPurposeProfile, uniqueElementName, uniquePurposeName } from '../../utils/testData'

/**
 * The read-only Purposes list at /purposes: pagination, the load-failed path for a bad
 * detail-page id, and that a created purpose's fields actually round-trip through the detail
 * page. See tests/03-purposes/03.01-admin-creating-purposes.spec.ts for creation-form validation.
 */
test.describe('Admin viewing the Purposes list (UI)', () => {
  test('03.02.01 - The rows-per-page control accepts a new page size without erroring', async ({
    browser,
    consentCleanupTracker,
  }) => {
    const consentAdminPage = await loginAsConsentAdmin(browser)
    // Seeded so the list is guaranteed non-empty regardless of what earlier runs left behind.
    const listPage = new PurposeListPage(consentAdminPage)
    await listPage.goto()
    await listPage.openCreateDialog()
    const dialog = new PurposeFormDialog(consentAdminPage)
    await dialog.fill({ name: uniquePurposeName(), type: 'Policy', version: 'v1' })
    await dialog.submit()
    await expect(consentAdminPage).toHaveURL(/\/purposes\/[^/]+$/)
    const purposeMatch = /\/purposes\/([^/]+)$/.exec(consentAdminPage.url())
    if (purposeMatch) {
      consentCleanupTracker.trackPurpose(purposeMatch[1])
    }

    await listPage.goto()
    await expect(listPage.previousPageButton).toBeDisabled()
    await listPage.setRowsPerPage(25)
    await expect(listPage.table).toBeVisible()
    await consentAdminPage.context().close()
  })

  test('03.02.02 - An unknown purpose id shows the load-failed message with a way back to the list', async ({
    browser,
  }) => {
    const consentAdminPage = await loginAsConsentAdmin(browser)
    const detailPage = new PurposeDetailPage(consentAdminPage)
    await detailPage.goto('00000000-0000-0000-0000-000000000000')
    await expect(detailPage.loadFailedMessage).toBeVisible()
    await detailPage.backButton.click()
    await expect(consentAdminPage).toHaveURL(/\/purposes$/)
    await consentAdminPage.context().close()
  })

  test('03.02.03 - The rows-per-page control caps the number of rendered rows at the selected size', async ({
    browser,
    consentAdminConsentApi,
    consentCleanupTracker,
  }) => {
    const consentAdminPage = await loginAsConsentAdmin(browser)
    // One more than the smallest page size, so there's guaranteed to be a next page regardless
    // of how many purposes earlier runs already left in this shared environment. Seeded via the
    // admin API, not the create-Purpose UI form - this test isn't exercising that form (see
    // tests/03-purposes/03.01-admin-creating-purposes.spec.ts for that), only the pagination it
    // feeds into is real UI.
    const seedCount = 11
    for (let i = 0; i < seedCount; i += 1) {
      const response = await consentAdminConsentApi.createPurpose({
        name: uniquePurposeName(),
        type: 'Policy',
        version: 'v1',
      })
      expect(response.status()).toBe(201)
      consentCleanupTracker.trackPurpose(((await response.json()) as { id: string }).id)
    }

    const listPage = new PurposeListPage(consentAdminPage)
    await listPage.goto()
    await listPage.setRowsPerPage(10)

    await expect(listPage.rows).toHaveCount(10)
    await expect(listPage.nextPageButton).toBeEnabled()
    await consentAdminPage.context().close()
  })

  test("03.02.04 - A newly created purpose's detail page shows its type, latest version, description, elements, and properties correctly", async ({
    browser,
    consentCleanupTracker,
  }) => {
    const consentAdminPage = await loginAsConsentAdmin(browser)
    const elementName = uniqueElementName()
    const elementListPage = new ElementListPage(consentAdminPage)
    await elementListPage.goto()
    await elementListPage.openCreateDialog()
    const elementDialog = new ElementFormDialog(consentAdminPage)
    await elementDialog.fill({ name: elementName })
    await elementDialog.submit()
    await expect(consentAdminPage).toHaveURL(/\/elements\/[^/]+$/)
    const elementId = /\/elements\/([^/]+)$/.exec(consentAdminPage.url())?.[1]
    if (!elementId) {
      throw new Error(`Could not read an element id out of the detail URL: ${consentAdminPage.url()}`)
    }
    consentCleanupTracker.trackElement(elementId)

    const profile = randomPurposeProfile()
    const version = 'v1'
    const properties = { retention_days: '365', jurisdiction: 'EU' }

    const listPage = new PurposeListPage(consentAdminPage)
    await listPage.goto()
    await listPage.openCreateDialog()
    const createDialog = new PurposeFormDialog(consentAdminPage)
    await createDialog.fill({ name: profile.name, type: profile.type, version, description: profile.description })
    // addElementByName, not addElements: this test needs THIS SPECIFIC just-created element, which
    // addElements' "pick whichever the picker shows first" can't guarantee - see its docblock.
    await createDialog.addElementByName(elementName, true)
    for (const [key, value] of Object.entries(properties)) {
      await createDialog.addProperty(key, value)
    }
    await createDialog.submit()
    await expect(consentAdminPage).toHaveURL(/\/purposes\/[^/]+$/)
    const purposeId = /\/purposes\/([^/]+)$/.exec(consentAdminPage.url())?.[1]
    if (!purposeId) {
      throw new Error(`Could not read a purpose id out of the detail URL: ${consentAdminPage.url()}`)
    }
    consentCleanupTracker.trackPurpose(purposeId)

    // A fresh navigation, not just the post-submit redirect - proves the server actually
    // persisted every field, not just that the create form's own optimistic state looked right.
    const detailPage = new PurposeDetailPage(consentAdminPage)
    await detailPage.goto(purposeId)
    await expect(detailPage.nameValue(profile.name)).toBeVisible()
    await expect(detailPage.fieldValue('Type')).toHaveText(profile.type)
    await expect(detailPage.fieldValue('Latest version')).toHaveText(version)
    await expect(detailPage.fieldValue('Description')).toHaveText(profile.description)
    await expect(detailPage.elementRow(elementName)).toBeVisible()
    for (const [key, value] of Object.entries(properties)) {
      await expect(detailPage.propertyRow(key)).toContainText(value)
    }
    await consentAdminPage.context().close()
  })
})
