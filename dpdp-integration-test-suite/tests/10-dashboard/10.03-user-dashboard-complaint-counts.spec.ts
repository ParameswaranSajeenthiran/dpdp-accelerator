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
import { moveComplaintToStatusViaApi, seedComplaintViaApi } from '../../utils/complaintSetup'
import { expectDashboardCounts } from '../../utils/dashboardCounts'

/**
 * The dashboard's per-status complaint counts. Complaints have no subject/authorizer split - the
 * self-service list is only ever the filer's own - so the cases are status bucketing and not
 * counting anyone else's. Exact for the same reason as 10.01: a throwaway account nothing else
 * files complaints as.
 */
test.describe('User dashboard complaint counts', () => {
  test("10.03.01 - Each complaint status card counts only the user's own complaints in that status", async ({
    throwawayAccounts,
    officerComplaintApi,
    userComplaintApi,
  }) => {
    const u = await throwawayAccounts.create('dpdp-e2e-dash-cmp')
    // One at a time: simultaneous creates in one org can exhaust the reference-ID retry - see
    // TEST-SCENARIOS.md, "Product bugs the tests work around".
    await seedComplaintViaApi(u.complaintApi, 'OTHER', 'dash-open')
    const inProgress = await seedComplaintViaApi(u.complaintApi, 'OTHER', 'dash-in-progress')
    const waitingOnClient = await seedComplaintViaApi(u.complaintApi, 'OTHER', 'dash-waiting-on-client')
    const waitingOnDpo = await seedComplaintViaApi(u.complaintApi, 'OTHER', 'dash-waiting-on-dpo')
    const resolved = await seedComplaintViaApi(u.complaintApi, 'OTHER', 'dash-resolved')
    // Someone else's open complaint - must not count as U's.
    await seedComplaintViaApi(userComplaintApi, 'OTHER', 'dash-other-user')
    await Promise.all([
      moveComplaintToStatusViaApi(officerComplaintApi, inProgress.id, 'IN_PROGRESS'),
      moveComplaintToStatusViaApi(officerComplaintApi, waitingOnClient.id, 'WAITING_ON_CLIENT'),
      moveComplaintToStatusViaApi(officerComplaintApi, waitingOnDpo.id, 'AWAITING_INTERNAL_REVIEW'),
      // OPEN -> RESOLVED isn't a direct transition; it goes through IN_PROGRESS.
      moveComplaintToStatusViaApi(officerComplaintApi, resolved.id, 'IN_PROGRESS').then(() =>
        moveComplaintToStatusViaApi(officerComplaintApi, resolved.id, 'RESOLVED'),
      ),
    ])

    const dashboard = new DashboardPage(u.session.page)
    await dashboard.goto()

    await expectDashboardCounts(dashboard, {
      'complaint-open': 1,
      'complaint-in-progress': 1,
      'complaint-waiting-on-client': 1,
      'complaint-waiting-on-dpo': 1,
      'complaint-resolved': 1,
    })
  })

  test('10.03.02 - "View all complaints" opens My Complaints', async ({ browser }) => {
    const page = await loginAsUser(browser)
    const dashboard = new DashboardPage(page)

    await dashboard.goto()
    await dashboard.viewComplaintsLink.click()

    await expect(page).toHaveURL(/\/complaints$/)
    await page.context().close()
  })
})
