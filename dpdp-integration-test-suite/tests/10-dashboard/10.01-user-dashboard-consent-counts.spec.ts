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

import { test, expect, loginAsUser } from '../../fixtures/auth.fixtures'
import { DashboardPage } from '../../pages/DashboardPage'
import {
  SHORT_EXPIRY_MS,
  revokeConsentViaApi,
  seedCatalogViaApi,
  seedConsentForCatalogViaApi,
  waitUntilConsentExpiredViaApi,
} from '../../utils/consentSetup'
import { expectDashboardCounts } from '../../utils/dashboardCounts'

/**
 * The dashboard's per-state consent counts for consents the user is the subject of, with no
 * authorizations - which states land in which card. Who else a consent involves is
 * 10.02's job.
 *
 * Every count here is exact, which the rest of this suite never asserts on shared data: each
 * counting test runs as a throwaway account (the throwawayAccounts fixture) that exists only for
 * that test, so no parallel test or earlier run can have written a consent it would see.
 */
test.describe('User dashboard consent counts', () => {
  test('10.01.01 - A new user sees zero in every consent and complaint status card', async ({
    throwawayAccounts,
  }) => {
    const { session } = await throwawayAccounts.create('dpdp-e2e-dash-empty')
    const dashboard = new DashboardPage(session.page)

    await dashboard.goto()

    await expectDashboardCounts(dashboard, {
      'consent-pending': 0,
      'consent-active': 0,
      'consent-rejected': 0,
      'consent-revoked': 0,
      'consent-expired': 0,
      'complaint-open': 0,
      'complaint-in-progress': 0,
      'complaint-waiting-on-client': 0,
      'complaint-waiting-on-dpo': 0,
      'complaint-resolved': 0,
    })
    await expect(dashboard.card('complaint-waiting-on-dpo')).toContainText('Waiting on DPO')
    await expect(dashboard.totalConsentsCard).toHaveCount(0)
    await expect(dashboard.totalComplaintsCard).toHaveCount(0)
  })

  test("10.01.02 - Each consent status card counts the user's own consents in that state", async ({
    throwawayAccounts,
    consentAdminConsentApi,
  }) => {
    const u = await throwawayAccounts.create('dpdp-e2e-dash-states')
    const subjectId = u.user.username
    const catalog = await seedCatalogViaApi(consentAdminConsentApi)

    // First, so its expiry mostly elapses while the rest is seeded.
    const expiring = await seedConsentForCatalogViaApi(consentAdminConsentApi, catalog, {
      subjectId,
      state: 'ACTIVE',
      expiryTime: Date.now() + SHORT_EXPIRY_MS,
    })
    const [, , , toRevoke] = await Promise.all([
      seedConsentForCatalogViaApi(consentAdminConsentApi, catalog, { subjectId, state: 'ACTIVE' }),
      seedConsentForCatalogViaApi(consentAdminConsentApi, catalog, { subjectId, state: 'ACTIVE' }),
      seedConsentForCatalogViaApi(consentAdminConsentApi, catalog, { subjectId, state: 'REJECTED' }),
      seedConsentForCatalogViaApi(consentAdminConsentApi, catalog, { subjectId, state: 'ACTIVE' }),
    ])
    await revokeConsentViaApi(u.consentApi, toRevoke.consentId)
    await waitUntilConsentExpiredViaApi(u.consentApi, expiring.consentId)

    const dashboard = new DashboardPage(u.session.page)
    await dashboard.goto()

    await expectDashboardCounts(dashboard, {
      'consent-pending': 0,
      'consent-active': 2,
      'consent-rejected': 1,
      'consent-revoked': 1,
      'consent-expired': 1,
    })
  })

  test('10.01.03 - "View all consents" opens My Consents', async ({ browser }) => {
    const page = await loginAsUser(browser)
    const dashboard = new DashboardPage(page)

    await dashboard.goto()
    await dashboard.viewConsentsLink.click()

    await expect(page).toHaveURL(/\/consents$/)
    await page.context().close()
  })
})
