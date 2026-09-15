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
import { ElementDeleteDialog } from '../../pages/ElementDeleteDialog'
import { ElementDetailPage } from '../../pages/ElementDetailPage'
import { ElementFormDialog } from '../../pages/ElementFormDialog'
import { ElementListPage } from '../../pages/ElementListPage'
import { PurposeFormDialog } from '../../pages/PurposeFormDialog'
import { PurposeListPage } from '../../pages/PurposeListPage'
import { uniqueElementName, uniquePurposeName } from '../../utils/testData'

/**
 * Deleting an Element from its detail page - only possible while nothing references it. A
 * Purpose that lists the Element blocks the delete (409, see ElementDetailsPage.tsx's
 * deleteErrorMessage), rather than deleting the Element and leaving the Purpose with a dangling
 * reference.
 */
test.describe('Admin deleting Elements (UI)', () => {
  test("02.04.01 - An admin deletes an element that isn't referenced by any purpose", async ({
    browser,
  }) => {
    const consentAdminPage = await loginAsConsentAdmin(browser)
    const elementName = uniqueElementName()

    const listPage = new ElementListPage(consentAdminPage)
    await listPage.goto()
    await listPage.openCreateDialog()
    const createDialog = new ElementFormDialog(consentAdminPage)
    await createDialog.fill({ name: elementName })
    await createDialog.submit()
    await expect(consentAdminPage).toHaveURL(/\/elements\/[^/]+$/)
    const elementId = /\/elements\/([^/]+)$/.exec(consentAdminPage.url())?.[1]
    if (!elementId) {
      throw new Error(`Could not read an element id out of the detail URL: ${consentAdminPage.url()}`)
    }
    // Not tracked with consentCleanupTracker: the delete below is the thing under test, so
    // there's nothing left to clean up once it succeeds.

    const detailPage = new ElementDetailPage(consentAdminPage)
    await detailPage.deleteButton.click()
    const deleteDialog = new ElementDeleteDialog(consentAdminPage)
    await deleteDialog.confirm()

    await expect(consentAdminPage).toHaveURL(/\/elements$/)

    // Confirms the server actually deleted it, not just that the UI navigated away - the same
    // load-failed signal 02.02.03 uses for a never-existed id.
    await detailPage.goto(elementId)
    await expect(detailPage.loadFailedMessage).toBeVisible()
    await consentAdminPage.context().close()
  })

  test('02.04.02 - An element still referenced by a purpose cannot be deleted', async ({
    browser,
    consentCleanupTracker,
  }) => {
    const consentAdminPage = await loginAsConsentAdmin(browser)
    const elementName = uniqueElementName()

    const elementListPage = new ElementListPage(consentAdminPage)
    await elementListPage.goto()
    await elementListPage.openCreateDialog()
    const createElementDialog = new ElementFormDialog(consentAdminPage)
    await createElementDialog.fill({ name: elementName })
    await createElementDialog.submit()
    await expect(consentAdminPage).toHaveURL(/\/elements\/[^/]+$/)
    const elementId = /\/elements\/([^/]+)$/.exec(consentAdminPage.url())?.[1]
    if (!elementId) {
      throw new Error(`Could not read an element id out of the detail URL: ${consentAdminPage.url()}`)
    }
    consentCleanupTracker.trackElement(elementId)

    const purposeListPage = new PurposeListPage(consentAdminPage)
    await purposeListPage.goto()
    await purposeListPage.openCreateDialog()
    const purposeDialog = new PurposeFormDialog(consentAdminPage)
    await purposeDialog.fill({ name: uniquePurposeName(), type: 'Policy', version: 'v1' })
    // addElementByName, not addElements: the picker searches server-side, so this reliably
    // selects the element this test just created regardless of how many others already exist.
    await purposeDialog.addElementByName(elementName, false)
    await purposeDialog.submit()
    await expect(consentAdminPage).toHaveURL(/\/purposes\/[^/]+$/)
    const purposeId = /\/purposes\/([^/]+)$/.exec(consentAdminPage.url())?.[1]
    if (!purposeId) {
      throw new Error(`Could not read a purpose id out of the detail URL: ${consentAdminPage.url()}`)
    }
    consentCleanupTracker.trackPurpose(purposeId)

    const detailPage = new ElementDetailPage(consentAdminPage)
    await detailPage.goto(elementId)
    await detailPage.deleteButton.click()
    const deleteDialog = new ElementDeleteDialog(consentAdminPage)
    await deleteDialog.confirm()

    await expect(deleteDialog.errorAlert).toContainText("still used by one or more purposes")
    // Rejected, not silently ignored - still on the confirmation dialog, and the element still
    // resolves (the delete never went through).
    await expect(deleteDialog.root).toBeVisible()
    await deleteDialog.cancel()
    await expect(detailPage.nameValue(elementName)).toBeVisible()
    await consentAdminPage.context().close()
  })
})
