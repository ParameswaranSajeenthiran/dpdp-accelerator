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

import { loginAsConsentAdmin, test, expect } from '../../fixtures/auth.fixtures'
import { TopicsPage } from '../../pages/TopicsPage'
import { seedActiveTopic } from '../../utils/eventNotificationSetup'

/**
 * Listing, searching, and filtering Event Notification topics (TopicsPage.tsx/TopicTable.tsx/
 * TopicFilters.tsx). See tests/08-event-notifications/README.md for the rows-per-page drift
 * (real options are [10, 20, 50], not the spreadsheet's "25").
 */
test.describe('Admin viewing and searching Topics', () => {
  test('05.02.01 - The Topics list renders active and deregistered rows with pagination controls', async ({
    browser,
    consentAdminEventApi,
  }) => {
    await seedActiveTopic(consentAdminEventApi, 'list-visible')
    const page = await loginAsConsentAdmin(browser)
    try {
      const topicsPage = new TopicsPage(page)
      await topicsPage.goto()

      await expect(topicsPage.table).toBeVisible()
      await expect(topicsPage.rows.first()).toBeVisible()
      await expect(topicsPage.previousPageButton).toBeDisabled()

      // Real rows-per-page option (20), not the spreadsheet's "25" - see this directory's README.
      await topicsPage.setRowsPerPage(20)
      await expect(topicsPage.table).toBeVisible()
      await expect(topicsPage.rows.first()).toBeVisible()
    } finally {
      await page.context().close()
    }
  })

  test('05.02.02 - Searching by a partial topic name finds the matching row', async ({
    browser,
    consentAdminEventApi,
  }) => {
    const topic = await seedActiveTopic(consentAdminEventApi, 'consent-status-changed')
    const uniqueSuffix = topic.name.split('-').slice(-2).join('-')

    const page = await loginAsConsentAdmin(browser)
    try {
      const topicsPage = new TopicsPage(page)
      await topicsPage.goto()
      await topicsPage.search(uniqueSuffix)

      await expect(topicsPage.rowByName(topic.name)).toBeVisible()
      for (const row of await topicsPage.rows.all()) {
        await expect(row).toContainText(uniqueSuffix)
      }
    } finally {
      await page.context().close()
    }
  })

})
