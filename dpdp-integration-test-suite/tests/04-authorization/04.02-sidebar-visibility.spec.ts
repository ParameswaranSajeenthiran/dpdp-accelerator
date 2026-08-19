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

import { test, expect, loginAsDataPrincipal, loginAsConsentAdmin } from '../../fixtures/auth.fixtures'
import { AppSidebarPage } from '../../pages/AppSidebarPage'

/**
 * AppSidebar.tsx filters each category's items by the current persona's scopes and only renders
 * a category's <Sidebar.Category> block at all when at least one of its items survives that
 * filter - so an unauthorized category's heading text disappears from the DOM entirely, not
 * just its items. The Dashboard category renders with no heading of its own (just the item), so
 * it isn't asserted on directly here - the Consent/Definitions/Administration category headings
 * (translated as "Consent", "Definitions", "Administration") are what distinguish personas.
 */
test.describe('Sidebar navigation visibility (UI)', () => {
  test("01.02.01 - A Data Principal's sidebar shows only the Dashboard and Consent sections", async ({
    browser,
  }) => {
    const dataPrincipalPage = await loginAsDataPrincipal(browser)
    await dataPrincipalPage.goto('dashboard')
    const sidebar = new AppSidebarPage(dataPrincipalPage)

    await expect(sidebar.label('Dashboard')).toBeVisible()
    await expect(sidebar.label('Consent')).toBeVisible()
    await expect(sidebar.label('All Consents')).toBeVisible()
    await expect(sidebar.label('Pending Consents')).toBeVisible()

    // Neither category heading exists at all - not merely hidden - since this persona holds
    // none of PURPOSES_READ, ELEMENTS_READ, or CONSENTS_READ_ANY.
    await expect(sidebar.label('Definitions')).toHaveCount(0)
    await expect(sidebar.label('Purposes')).toHaveCount(0)
    await expect(sidebar.label('Elements')).toHaveCount(0)
    await expect(sidebar.label('Administration')).toHaveCount(0)
    await dataPrincipalPage.context().close()
  })

  test('01.02.02 - A Consent Admin\'s sidebar shows every section, including Definitions and Administration', async ({
    browser,
  }) => {
    const consentAdminPage = await loginAsConsentAdmin(browser)
    await consentAdminPage.goto('dashboard')
    const sidebar = new AppSidebarPage(consentAdminPage)

    await expect(sidebar.label('Dashboard')).toBeVisible()
    await expect(sidebar.label('Consent')).toBeVisible()
    await expect(sidebar.label('All Consents')).toBeVisible()
    await expect(sidebar.label('Pending Consents')).toBeVisible()
    await expect(sidebar.label('Definitions')).toBeVisible()
    await expect(sidebar.label('Purposes')).toBeVisible()
    await expect(sidebar.label('Elements')).toBeVisible()
    await expect(sidebar.label('Administration')).toBeVisible()
    await consentAdminPage.context().close()
  })
})
