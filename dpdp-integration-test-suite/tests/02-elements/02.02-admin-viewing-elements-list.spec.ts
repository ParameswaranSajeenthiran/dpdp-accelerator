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
import { ElementDetailPage } from '../../pages/ElementDetailPage'
import { ElementFormDialog } from '../../pages/ElementFormDialog'
import { ElementListPage } from '../../pages/ElementListPage'
import { uniqueElementName } from '../../utils/testData'

/**
 * The read-only Elements list at /elements: rendering, pagination, and the load-failed path for a
 * bad detail-page id. Elements seeded here only exist to give the list rows to page through - see
 * tests/02-elements/02.01-admin-creating-elements.spec.ts for creation-form validation and the
 * detail page's own field round-trip.
 */
test.describe('Admin viewing the Elements list (UI)', () => {
  test('02.02.01 - The list renders and its rows-per-page control accepts a new page size without erroring', async ({
    browser,
  }) => {
    const consentAdminPage = await loginAsConsentAdmin(browser)
    // Seeded so the list is guaranteed non-empty regardless of what earlier runs left behind.
    const listPageSetup = new ElementListPage(consentAdminPage)
    await listPageSetup.goto()
    await listPageSetup.openCreateDialog()
    const setupDialog = new ElementFormDialog(consentAdminPage)
    await setupDialog.fill({ name: uniqueElementName() })
    await setupDialog.submit()
    await expect(consentAdminPage).toHaveURL(/\/elements\/[^/]+$/)

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
    consentAdminConsentApi,
  }) => {
    const consentAdminPage = await loginAsConsentAdmin(browser)
    // One more than the smallest page size, so there's guaranteed to be a next page regardless
    // of how many elements earlier runs already left in this shared environment. Seeded via the
    // admin API - see 02.01-admin-creating-elements.spec.ts for the create-form coverage itself.
    const seedCount = 11
    for (let i = 0; i < seedCount; i += 1) {
      const response = await consentAdminConsentApi.createElement({ name: uniqueElementName() })
      expect(response.status()).toBe(201)
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
})
