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

import { test, expect, loginAsDpo } from '../../fixtures/auth.fixtures'
import { ComplaintQueuePage } from '../../pages/ComplaintQueuePage'
import { moveComplaintToStatusViaApi, seedComplaintViaApi } from '../../utils/complaintSetup'

/**
 * Narrowing the officer's org-wide queue - ComplaintQueueFilters.tsx offers a status filter, a
 * priority filter, and a free-text search box, unlike the Data Principal's list (status filter
 * only, see 08.03-data-principal-searching-complaints.spec.ts).
 *
 * Status, priority, and search all filter server-side (ComplaintQueuePage.tsx passes them to
 * useManagedComplaintListQuery), so a match is found whatever page it lives on. Every test below
 * still sets rowsPerPage to its max (25) first: the default sort is UPDATED_TIME DESC, so a test's
 * own freshly-created complaint is reliably on the first page of an unsearched queue.
 */
test.describe('Complaint Officer searching/filtering the queue (UI)', () => {
  test('08.06.01 - Filtering by status shows a matching complaint and hides a non-matching one', async ({
    browser,
    userComplaintApi,
    officerComplaintApi,
  }) => {
    const openComplaint = await seedComplaintViaApi(userComplaintApi, 'OTHER', 'queue-filter-open')
    const inProgressComplaint = await seedComplaintViaApi(userComplaintApi, 'OTHER', 'queue-filter-in-progress')
    await moveComplaintToStatusViaApi(officerComplaintApi, inProgressComplaint.id, 'IN_PROGRESS')

    const officerPage = await loginAsDpo(browser)
    const queuePage = new ComplaintQueuePage(officerPage)
    await queuePage.goto()
    await queuePage.setRowsPerPage(25)
    await queuePage.filterByStatus('Open')

    await expect(queuePage.rowByReferenceId(openComplaint.referenceId)).toBeVisible()
    await expect(queuePage.rowByReferenceId(inProgressComplaint.referenceId)).not.toBeVisible()
    await officerPage.context().close()
  })

  test('08.06.02 - Filtering by priority shows a matching complaint and hides a non-matching one', async ({
    browser,
    userComplaintApi,
  }) => {
    const criticalComplaint = await seedComplaintViaApi(userComplaintApi, 'DATA_BREACH', 'queue-filter-critical')
    const lowComplaint = await seedComplaintViaApi(userComplaintApi, 'OTHER', 'queue-filter-low')

    const officerPage = await loginAsDpo(browser)
    const queuePage = new ComplaintQueuePage(officerPage)
    await queuePage.goto()
    await queuePage.setRowsPerPage(25)
    await queuePage.filterByPriority('Critical')

    await expect(queuePage.rowByReferenceId(criticalComplaint.referenceId)).toBeVisible()
    await expect(queuePage.rowByReferenceId(lowComplaint.referenceId)).not.toBeVisible()
    await officerPage.context().close()
  })

  test('08.06.03 - A resolved complaint shows in the default queue view and when filtering by "Resolved"', async ({
    browser,
    userComplaintApi,
    officerComplaintApi,
  }) => {
    const seeded = await seedComplaintViaApi(userComplaintApi, 'OTHER', 'queue-filter-resolved')
    await moveComplaintToStatusViaApi(officerComplaintApi, seeded.id, 'IN_PROGRESS')
    await moveComplaintToStatusViaApi(officerComplaintApi, seeded.id, 'RESOLVED', 'Resolved for this test.')

    const officerPage = await loginAsDpo(browser)
    const queuePage = new ComplaintQueuePage(officerPage)
    await queuePage.goto()
    await queuePage.setRowsPerPage(25)

    // Regression cover for the queue having once dropped resolved complaints from the default
    // (status=All) view: "All" means every status, and selecting "Resolved" narrows to them.
    // The default sort is UPDATED_TIME DESC, so this just-resolved complaint heads page one.
    await expect(queuePage.table).toBeVisible()
    await expect(queuePage.rowByReferenceId(seeded.referenceId)).toBeVisible()
    await queuePage.filterByStatus('Resolved')
    await expect(queuePage.rowByReferenceId(seeded.referenceId)).toBeVisible()
    await officerPage.context().close()
  })

  test('08.06.04 - Searching by reference id narrows the queue to that complaint', async ({
    browser,
    userComplaintApi,
  }) => {
    const seeded = await seedComplaintViaApi(userComplaintApi, 'OTHER', 'queue-search-reference')
    const officerPage = await loginAsDpo(browser)
    const queuePage = new ComplaintQueuePage(officerPage)
    await queuePage.goto()
    await queuePage.setRowsPerPage(25)

    await queuePage.searchByReferenceOrName(seeded.referenceId)
    await expect(queuePage.rowByReferenceId(seeded.referenceId)).toBeVisible()
    // Every surviving row carries the searched reference id - asserted as "no row lacks it"
    // rather than "exactly one row", which would be a count assertion on a shared list.
    await expect(queuePage.rows.filter({ hasNotText: seeded.referenceId })).toHaveCount(0)
    await officerPage.context().close()
  })

  test("08.06.05 - Searching by the Data Principal's name narrows the queue to that principal's complaints", async ({
    browser,
    userComplaintApi,
  }) => {
    const seeded = await seedComplaintViaApi(userComplaintApi, 'OTHER', 'queue-search-name')
    const record = await (await userComplaintApi.getMyComplaint(seeded.id)).json()
    const dataPrincipalName = (record.userName as string | null) ?? (record.userId as string)

    const officerPage = await loginAsDpo(browser)
    const queuePage = new ComplaintQueuePage(officerPage)
    await queuePage.goto()
    await queuePage.setRowsPerPage(25)

    await queuePage.searchByReferenceOrName(dataPrincipalName)
    await expect(queuePage.rowByReferenceId(seeded.referenceId)).toBeVisible()
    // Every visible row belongs to this same Data Principal - other complaints of theirs from
    // other tests may also match, so this is "no row lacks the name" rather than an exact count.
    // One assertion over a filtered locator, never a loop over rows.nth(): the row count can
    // change between the count() and the assertion while the search's own refetch is still
    // narrowing the table (see 09.02.02's identical fix).
    await expect(queuePage.rows.filter({ hasNotText: dataPrincipalName })).toHaveCount(0)
    await officerPage.context().close()
  })

})
