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
import { CATALOG_STATS, COMPLAINT_STATS, CONSENT_STATS, DashboardPage } from '../../pages/DashboardPage'
import { expectDashboardCountsLoaded } from '../../utils/dashboardCounts'

/**
 * The admin dashboard counts the whole tenant, which every parallel test - and, on the super
 * tenant, every earlier run - writes to, so no exact value is predictable here. These tests check
 * each card loads a real value and the page's shape; the counting itself is proven on the user
 * dashboard (10.01-10.03), whose only difference is the list API it asks.
 */
test.describe('Admin dashboard', () => {
  test('10.04.01 - An admin sees tenant-wide consent status cards and Purposes/Elements counts', async ({
    browser,
  }) => {
    const page = await loginAsConsentAdmin(browser)
    const dashboard = new DashboardPage(page)

    await dashboard.goto()

    await expectDashboardCountsLoaded(dashboard, [...CONSENT_STATS, ...CATALOG_STATS])
    await expect(dashboard.adminSubtitle).toBeVisible()
    await expect(dashboard.totalConsentsCard).toHaveCount(0)
    // The admin's view replaces the self-service one - no complaints section of their own.
    await expect(dashboard.complaintsHeading).toHaveCount(0)
    for (const stat of COMPLAINT_STATS) {
      await expect(dashboard.card(stat)).toHaveCount(0)
    }
    await page.context().close()
  })

  test('10.04.02 - "View all consents" opens the admin consent registry', async ({ browser }) => {
    const page = await loginAsConsentAdmin(browser)
    const dashboard = new DashboardPage(page)

    await dashboard.goto()
    await dashboard.viewConsentsLink.click()

    await expect(page).toHaveURL(/\/administration\/consents$/)
    await page.context().close()
  })
})
