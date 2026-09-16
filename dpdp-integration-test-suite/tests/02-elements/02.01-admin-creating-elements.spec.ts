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
 * The "Add Element" form: the happy path plus its validation rules.
 */
test.describe('Admin creating Elements (UI)', () => {
  test('02.01.01 - Creating an element with a name, display name, description, and properties succeeds', async ({
    browser,
  }) => {
    const consentAdminPage = await loginAsConsentAdmin(browser)
    const elementName = uniqueElementName()
    const displayName = `Display ${elementName}`
    const description = 'Used to send account and service notifications.'

    const listPage = new ElementListPage(consentAdminPage)
    await listPage.goto()
    await listPage.openCreateDialog()
    const dialog = new ElementFormDialog(consentAdminPage)
    await dialog.fill({ name: elementName, displayName, description })
    await dialog.addProperty('retention_days', '365')
    await dialog.submit()

    await expect(consentAdminPage).toHaveURL(/\/elements\/[^/]+$/)

    const detailPage = new ElementDetailPage(consentAdminPage)
    await expect(detailPage.nameValue(elementName)).toBeVisible()
    await expect(detailPage.fieldValue('Display name')).toHaveText(displayName)
    await expect(detailPage.fieldValue('Description')).toHaveText(description)
    await expect(detailPage.propertyRow('retention_days')).toContainText('365')
    await consentAdminPage.context().close()
  })

  test('02.01.02 - Leaving name empty shows the required-field error and blocks submission', async ({
    browser,
  }) => {
    const consentAdminPage = await loginAsConsentAdmin(browser)
    const listPage = new ElementListPage(consentAdminPage)
    await listPage.goto()
    await listPage.openCreateDialog()

    const dialog = new ElementFormDialog(consentAdminPage)
    await dialog.blurName()
    await dialog.submit()

    await expect(dialog.root.getByText('Name is required.')).toBeVisible()
    await expect(dialog.root).toBeVisible()
    await consentAdminPage.context().close()
  })

  test('02.01.03 - Creating an element with a name that already exists shows the duplicate-name message', async ({
    browser,
  }) => {
    const consentAdminPage = await loginAsConsentAdmin(browser)
    const elementName = uniqueElementName()

    const listPage = new ElementListPage(consentAdminPage)
    await listPage.goto()
    await listPage.openCreateDialog()
    const firstDialog = new ElementFormDialog(consentAdminPage)
    await firstDialog.fill({ name: elementName })
    await firstDialog.submit()
    await expect(consentAdminPage).toHaveURL(/\/elements\/[^/]+$/)

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
    await consentAdminPage.context().close()
  })

  test('02.01.04 - A property value with no key blocks submission until the key is filled in or the row is removed', async ({
    browser,
  }) => {
    const consentAdminPage = await loginAsConsentAdmin(browser)
    const listPage = new ElementListPage(consentAdminPage)
    await listPage.goto()
    await listPage.openCreateDialog()

    const dialog = new ElementFormDialog(consentAdminPage)
    await dialog.fill({ name: uniqueElementName() })
    await dialog.addProperty('', 'orphaned-value')

    await expect(dialog.root.getByText('Add a key, or this value will not be saved.')).toBeVisible()
    await expect(dialog.createButton).toBeDisabled()
    await consentAdminPage.context().close()
  })
})
