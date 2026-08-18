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

import { test, expect } from '../../fixtures/auth.fixtures'
import { PurposeDetailPage } from '../../pages/PurposeDetailPage'
import { PurposeFormDialog } from '../../pages/PurposeFormDialog'
import { PurposeListPage } from '../../pages/PurposeListPage'
import { uniquePurposeName } from '../../utils/testData'

/**
 * Every Purpose (and every Element it needs) in this file is created through the real "Add
 * Purpose"/"Add Element" UI forms - Consent is the only thing in this suite created via the
 * admin API (it has no create UI at all). Every Purpose/Element this file creates is registered
 * with `consentCleanupTracker` so it's deleted again once the test finishes - see
 * fixtures/auth.fixtures.ts's ConsentCleanupTracker.
 */
test.describe('Purpose catalog (UI)', () => {
  test.describe('Happy paths', () => {
    test('01.02.01 - Creates a purpose through the Add Purpose form with a realistic mix of mandatory and optional elements', async ({
      consentAdminPage,
      consentCleanupTracker,
    }) => {
      const listPage = new PurposeListPage(consentAdminPage)
      await listPage.goto()
      await listPage.openCreateDialog()

      const purposeName = uniquePurposeName()
      const dialog = new PurposeFormDialog(consentAdminPage)
      await dialog.fill({
        name: purposeName,
        type: 'Loyalty',
        version: 'v1',
        description: 'Consent to enroll in the loyalty rewards program.',
      })
      // A real loyalty-program purpose needs more than one element - two required (e.g. name and
      // email) plus one optional (e.g. phone), not just a single element in isolation.
      const [firstLabel, secondLabel, thirdLabel] = await dialog.addElements([true, true, false])
      await dialog.addProperty('lawfulBasis', 'consent')
      await dialog.submit()

      // On success the dialog closes and navigates straight to the new purpose's detail page.
      await expect(consentAdminPage).toHaveURL(/\/purposes\/[^/]+$/)
      const purposeMatch = /\/purposes\/([^/]+)$/.exec(consentAdminPage.url())
      if (purposeMatch) {
        consentCleanupTracker.trackPurpose(purposeMatch[1])
      }
      const detailPage = new PurposeDetailPage(consentAdminPage)
      await expect(detailPage.heading(purposeName)).toBeVisible()
      await expect(consentAdminPage.getByText('Loyalty', { exact: true })).toBeVisible()
      await expect(detailPage.elementRow(firstLabel)).toContainText('Mandatory')
      await expect(detailPage.elementRow(secondLabel)).toContainText('Mandatory')
      await expect(detailPage.elementRow(thirdLabel)).toContainText('Optional')
      await expect(consentAdminPage.getByText('consent', { exact: true })).toBeVisible()

      // Also findable back on the list via the new name search, proving the create flow and the
      // search filter agree on what got created.
      await listPage.goto()
      await listPage.search({ name: purposeName })
      await expect(listPage.rowByName(purposeName)).toBeVisible()
    })

    test('01.02.02 - A purpose with no elements and no properties shows the catalog empty-state messages', async ({
      consentAdminPage,
      consentCleanupTracker,
    }) => {
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
      await expect(consentAdminPage.getByText('No custom properties.')).toBeVisible()
      await expect(
        consentAdminPage.getByText('No elements are configured for this purpose.'),
      ).toBeVisible()
    })

    test('01.02.03 - The rows-per-page control accepts a new page size without erroring', async ({
      consentAdminPage,
      consentCleanupTracker,
    }) => {
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
    })
  })

  test.describe('Validation violations', () => {
    test('01.02.04 - An unknown purpose id shows the load-failed message with a way back to the list', async ({
      consentAdminPage,
    }) => {
      const detailPage = new PurposeDetailPage(consentAdminPage)
      await detailPage.goto('00000000-0000-0000-0000-000000000000')
      await expect(detailPage.loadFailedMessage).toBeVisible()
      await detailPage.backButton.click()
      await expect(consentAdminPage).toHaveURL(/\/purposes$/)
    })

    test('01.02.05 - Leaving name, type, and version empty shows all three required-field errors and blocks submission', async ({
      consentAdminPage,
    }) => {
      const listPage = new PurposeListPage(consentAdminPage)
      await listPage.goto()
      await listPage.openCreateDialog()

      const dialog = new PurposeFormDialog(consentAdminPage)
      await dialog.blur('name')
      await dialog.blur('type')
      await dialog.blur('version')
      await dialog.submit()

      await expect(dialog.root.getByText('Name is required.')).toBeVisible()
      await expect(dialog.root.getByText('Type is required.')).toBeVisible()
      await expect(dialog.root.getByText('Version is required.')).toBeVisible()
      // Still on the dialog - nothing was submitted.
      await expect(dialog.root).toBeVisible()
    })

    test('01.02.06 - A property value with no key blocks submission until the key is filled in or the row is removed', async ({
      consentAdminPage,
    }) => {
      const listPage = new PurposeListPage(consentAdminPage)
      await listPage.goto()
      await listPage.openCreateDialog()

      const dialog = new PurposeFormDialog(consentAdminPage)
      await dialog.fill({ name: uniquePurposeName(), type: 'Policy', version: 'v1' })
      await dialog.addProperty('', 'orphaned-value')

      await expect(dialog.root.getByText('Add a key, or this value will not be saved.')).toBeVisible()
      await expect(dialog.createButton).toBeDisabled()
    })
  })
})
