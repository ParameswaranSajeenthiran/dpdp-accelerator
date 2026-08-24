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
import { PurposeFormDialog } from '../../pages/PurposeFormDialog'
import { PurposeListPage } from '../../pages/PurposeListPage'
import { uniquePurposeName } from '../../utils/testData'

/**
 * The "Add Purpose" form's edge cases and validation rules. The happy-path creation flow itself
 * moved to tests/02-consents/02.07-admin-creating-a-consent-end-to-end.spec.ts's second step,
 * which chains straight from creating an Element into using it here and then into a Consent -
 * see that file's header comment for why. Purposes/Elements created here are registered with
 * `consentCleanupTracker` so they're deleted again once the test finishes - see
 * fixtures/auth.fixtures.ts's ConsentCleanupTracker.
 */
test.describe('Admin creating Purposes (UI)', () => {
  test('04.01.01 - A purpose with no elements and no properties shows the catalog empty-state messages', async ({
    browser,
    consentCleanupTracker,
  }) => {
    const consentAdminPage = await loginAsConsentAdmin(browser)
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
      consentAdminPage.getByText('No elements are configured for this version.'),
    ).toBeVisible()
    await consentAdminPage.context().close()
  })

  test('04.01.02 - Leaving name, type, and version empty shows all three required-field errors and blocks submission', async ({
    browser,
  }) => {
    const consentAdminPage = await loginAsConsentAdmin(browser)
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
    await consentAdminPage.context().close()
  })

  test('04.01.03 - A property value with no key blocks submission until the key is filled in or the row is removed', async ({
    browser,
  }) => {
    const consentAdminPage = await loginAsConsentAdmin(browser)
    const listPage = new PurposeListPage(consentAdminPage)
    await listPage.goto()
    await listPage.openCreateDialog()

    const dialog = new PurposeFormDialog(consentAdminPage)
    await dialog.fill({ name: uniquePurposeName(), type: 'Policy', version: 'v1' })
    await dialog.addProperty('', 'orphaned-value')

    await expect(dialog.root.getByText('Add a key, or this value will not be saved.')).toBeVisible()
    await expect(dialog.createButton).toBeDisabled()
    await consentAdminPage.context().close()
  })
})
