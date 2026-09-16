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
import { PurposeDeleteDialog } from '../../pages/PurposeDeleteDialog'
import { PurposeDetailPage } from '../../pages/PurposeDetailPage'
import { PurposeFormDialog } from '../../pages/PurposeFormDialog'
import { PurposeListPage } from '../../pages/PurposeListPage'
import { seedConsent } from '../../utils/consentSetup'
import { uniquePurposeName } from '../../utils/testData'

/**
 * Deleting a Purpose from its detail page - only possible while no Consent references it. A
 * Consent that lists the Purpose blocks the delete (409, see PurposeDetailsPage.tsx's
 * deleteErrorMessage) - unlike Elements, this is blocked by Consents, not by anything else in the
 * catalog.
 */
test.describe('Admin deleting Purposes (UI)', () => {
  test("03.04.01 - An admin deletes a purpose that isn't referenced by any consent", async ({
    browser,
  }) => {
    const consentAdminPage = await loginAsConsentAdmin(browser)
    const listPage = new PurposeListPage(consentAdminPage)
    await listPage.goto()
    await listPage.openCreateDialog()
    const createDialog = new PurposeFormDialog(consentAdminPage)
    await createDialog.fill({ name: uniquePurposeName(), type: 'Policy', version: 'v1' })
    await createDialog.submit()
    await expect(consentAdminPage).toHaveURL(/\/purposes\/[^/]+$/)
    const purposeId = /\/purposes\/([^/]+)$/.exec(consentAdminPage.url())?.[1]
    if (!purposeId) {
      throw new Error(`Could not read a purpose id out of the detail URL: ${consentAdminPage.url()}`)
    }
    // Not tracked with consentCleanupTracker: the delete below is the thing under test.

    const detailPage = new PurposeDetailPage(consentAdminPage)
    await detailPage.deleteButton.click()
    const deleteDialog = new PurposeDeleteDialog(consentAdminPage)
    await deleteDialog.confirm()

    await expect(consentAdminPage).toHaveURL(/\/purposes$/)

    // Confirms the server actually deleted it, not just that the UI navigated away - the same
    // load-failed signal 03.02.02 uses for a never-existed id.
    await detailPage.goto(purposeId)
    await expect(detailPage.loadFailedMessage).toBeVisible()
    await consentAdminPage.context().close()
  })

  test('03.04.02 - A purpose still referenced by a consent cannot be deleted', async ({
    browser,
    target,
    consentAdminConsentApi,
    consentCleanupTracker,
  }) => {
    const consentAdminPage = await loginAsConsentAdmin(browser)
    // seedConsent creates its own Purpose (and Element) - that Purpose is what this test needs
    // referenced by a real, permanent Consent (Consents can never be deleted, see AGENTS.md), so
    // it can't be seeded any other way.
    const { purposeName } = await seedConsent(
      consentAdminConsentApi,
      consentCleanupTracker,
      target.personas.user.username,
      'PENDING',
    )

    // Searched by name, not just goto() + openByName: the unfiltered list only shows its first
    // page, and this shared environment accumulates far more purposes than that.
    const listPage = new PurposeListPage(consentAdminPage)
    await listPage.goto()
    await listPage.search({ name: purposeName })
    await listPage.openByName(purposeName)
    await expect(consentAdminPage).toHaveURL(/\/purposes\/[^/]+$/)

    const detailPage = new PurposeDetailPage(consentAdminPage)
    await detailPage.deleteButton.click()
    const deleteDialog = new PurposeDeleteDialog(consentAdminPage)
    await deleteDialog.confirm()

    await expect(deleteDialog.errorAlert).toContainText('still referenced by one or more consents')
    // Rejected, not silently ignored - still on the confirmation dialog, and the purpose still
    // resolves (the delete never went through).
    await expect(deleteDialog.root).toBeVisible()
    await deleteDialog.cancel()
    await expect(detailPage.nameValue(purposeName)).toBeVisible()
    await consentAdminPage.context().close()
  })
})
