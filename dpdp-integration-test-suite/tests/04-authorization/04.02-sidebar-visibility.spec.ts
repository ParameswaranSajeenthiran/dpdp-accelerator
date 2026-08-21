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

import { test, expect, loginAsUser, loginAsConsentAdmin } from '../../fixtures/auth.fixtures'
import { AppSidebarPage } from '../../pages/AppSidebarPage'

/**
 * AppSidebar.tsx filters each category's items by the current persona's scopes and only renders
 * a category's <Sidebar.Category> block at all when at least one of its items survives that
 * filter - so an unauthorized category's heading text disappears from the DOM entirely, not
 * just its items. The Dashboard category renders with no heading of its own (just the item), so
 * it isn't asserted on directly here - the Consent/Definitions/Administration category headings
 * (translated as "Consent", "Definitions", "Administration") are what distinguish personas.
 *
 * The item labels come from the `sidebar.*` block of public/i18n/en/common.json, and the
 * self-service and admin registries are deliberately named apart there: a user's own lists are
 * "My Consents"/"My Pending Consents" (sidebar.allConsents/pendingConsents), while
 * "All Consents" (sidebar.adminConsents) is the ADMIN registry. Asserting "All Consents" for the
 * user persona therefore tests the opposite of what it reads like.
 */
test.describe('Sidebar navigation visibility (UI)', () => {
  test("01.02.01 - A user's sidebar shows only the Dashboard and Consent sections", async ({
    browser,
  }) => {
    const userPage = await loginAsUser(browser)
    await userPage.goto('dashboard')
    const sidebar = new AppSidebarPage(userPage)

    await expect(sidebar.label('Dashboard')).toBeVisible()
    await expect(sidebar.label('Consent')).toBeVisible()
    await expect(sidebar.label('My Consents')).toBeVisible()
    await expect(sidebar.label('My Pending Consents')).toBeVisible()

    // Neither category heading exists at all - not merely hidden - since this persona holds
    // none of PURPOSES_READ, ELEMENTS_READ, or CONSENTS_READ_ANY.
    await expect(sidebar.label('Definitions')).toHaveCount(0)
    await expect(sidebar.label('Purposes')).toHaveCount(0)
    await expect(sidebar.label('Elements')).toHaveCount(0)
    await expect(sidebar.label('Administration')).toHaveCount(0)
    // The admin registry's own entry, gated on CONSENTS_READ_ANY.
    await expect(sidebar.label('All Consents')).toHaveCount(0)
    await userPage.context().close()
  })

  test('01.02.02 - A Consent Admin\'s sidebar shows every section, including Definitions and Administration', async ({
    browser,
  }) => {
    const consentAdminPage = await loginAsConsentAdmin(browser)
    await consentAdminPage.goto('dashboard')
    const sidebar = new AppSidebarPage(consentAdminPage)

    await expect(sidebar.label('Dashboard')).toBeVisible()
    await expect(sidebar.label('Definitions')).toBeVisible()
    await expect(sidebar.label('Purposes')).toBeVisible()
    await expect(sidebar.label('Elements')).toBeVisible()
    await expect(sidebar.label('Administration')).toBeVisible()
    await expect(sidebar.label('All Consents')).toBeVisible()

    // deployment.config.json ships hideSelfConsentsForAdmins: true, so an account holding
    // CONSENTS_READ_ANY loses the self-service items - and with them the whole "Consent"
    // category, which only renders when at least one of its items survives the scope filter.
    // Matches the unit-level contract in frontend/src/__tests__/AppSidebar.test.tsx.
    await expect(sidebar.label('My Consents')).toHaveCount(0)
    await expect(sidebar.label('My Pending Consents')).toHaveCount(0)
    await expect(sidebar.label('Consent')).toHaveCount(0)
    await consentAdminPage.context().close()
  })
})
