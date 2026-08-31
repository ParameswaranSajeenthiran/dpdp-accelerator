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

import { test, expect } from '../../fixtures/auth.fixtures'
import type { ComplaintPriority, ComplaintStatus } from '../../clients/ComplaintApiClient'
import { uniqueMarker } from '../../utils/testData'

/**
 * The Complaint Officer / admin surface - ComplaintEndpoint.java, ComplaintCommentEndpoint.java -
 * officer-assisted intake, org-wide search, internal notes, and resolving with a note (contrast
 * with 06.02.09's citizen-side bug: the officer path has always required - and always supplied -
 * a note for RESOLVED, so it isn't affected).
 *
 * Search/filter/pagination tests use a synthetic, never-before-used userId (via officer-assisted
 * intake, which - unlike /me - accepts any non-blank userId with no check that it's a real
 * registered account) rather than a real persona's userId. This makes `userId`-filtered result
 * sets exactly and deterministically sized, immune to however much history this shared,
 * never-reset environment (see the suite README) has accumulated for a real test account.
 */
test.describe('Complaint Officer managing complaints (API)', () => {
  test('06.04.01 - Officer-assisted intake creates a complaint attributed to the named Data Principal', async ({
    officerComplaintApi,
  }) => {
    const userId = uniqueMarker('intake-user')
    const description = `Complainant called in to report a data breach: ${uniqueMarker('intake')}`

    const response = await officerComplaintApi.createComplaintForUser({
      userId,
      subjectCategory: 'DATA_BREACH',
      description,
    })
    expect(response.status()).toBe(201)
    const body = await response.json()
    expect(body.userId).toBe(userId)
    expect(body.description).toBe(description)
    expect(body.priority).toBe('CRITICAL')

    const fetched = await (await officerComplaintApi.getComplaint(body.id)).json()
    expect(fetched.userId).toBe(userId)
  })

  test('06.04.02 - A citizen can see a complaint an officer lodged on their behalf', async ({
    userComplaintApi,
    officerComplaintApi,
  }) => {
    // The citizen creates one complaint themselves first, purely to learn their own IS user id
    // (userId) from the response - there's no other way to look this up from the officer side
    // without an IS admin API, which is out of scope for this suite.
    const own = await userComplaintApi.createMyComplaint({
      subjectCategory: 'OTHER',
      description: `Automated regression test: ${uniqueMarker('learn-my-user-id')}`,
    })
    const { userId } = await own.json()

    const intakeDescription = `Phoned in on this citizen's behalf: ${uniqueMarker('officer-intake-visible')}`
    const intake = await officerComplaintApi.createComplaintForUser({
      userId,
      subjectCategory: 'OTHER',
      description: intakeDescription,
    })
    const { id } = await intake.json()

    const seenByCitizen = await userComplaintApi.getMyComplaint(id)
    expect(seenByCitizen.ok()).toBe(true)
    expect((await seenByCitizen.json()).description).toBe(intakeDescription)
  })

  test.describe('Search and filtering', () => {
    test('06.04.03 - Filtering by userId returns exactly that Data Principal\'s complaints', async ({
      officerComplaintApi,
    }) => {
      const userId = uniqueMarker('search-user')
      await officerComplaintApi.createComplaintForUser({
        userId,
        subjectCategory: 'OTHER',
        description: `Automated regression test: ${uniqueMarker('search-1')}`,
      })
      await officerComplaintApi.createComplaintForUser({
        userId,
        subjectCategory: 'OTHER',
        description: `Automated regression test: ${uniqueMarker('search-2')}`,
      })

      const response = await officerComplaintApi.listComplaints({ userId })
      expect(response.ok()).toBe(true)
      const { data, metadata } = await response.json()
      expect(metadata.total).toBe(2)
      expect(data).toHaveLength(2)
      expect(data.every((complaint: { userId: string }) => complaint.userId === userId)).toBe(true)
    })

    test('06.04.04 - Filtering by status only returns complaints in that status', async ({ officerComplaintApi }) => {
      const userId = uniqueMarker('search-status-user')
      const opened = await officerComplaintApi.createComplaintForUser({
        userId,
        subjectCategory: 'OTHER',
        description: `Automated regression test: ${uniqueMarker('status-open')}`,
      })
      const inProgress = await officerComplaintApi.createComplaintForUser({
        userId,
        subjectCategory: 'OTHER',
        description: `Automated regression test: ${uniqueMarker('status-in-progress')}`,
      })
      const { id: inProgressId } = await inProgress.json()
      await officerComplaintApi.updateStatus(inProgressId, { toStatus: 'IN_PROGRESS' })

      const response = await officerComplaintApi.listComplaints({ userId, status: 'OPEN' })
      const { data } = await response.json()
      const ids = data.map((complaint: { id: string }) => complaint.id)
      expect(ids).toContain((await opened.json()).id)
      expect(ids).not.toContain(inProgressId)
    })

    test('06.04.05 - Filtering by priority only returns complaints at that priority', async ({
      officerComplaintApi,
    }) => {
      const userId = uniqueMarker('search-priority-user')
      const critical = await officerComplaintApi.createComplaintForUser({
        userId,
        subjectCategory: 'DATA_BREACH', // CRITICAL
        description: `Automated regression test: ${uniqueMarker('priority-critical')}`,
      })
      const low = await officerComplaintApi.createComplaintForUser({
        userId,
        subjectCategory: 'OTHER', // LOW
        description: `Automated regression test: ${uniqueMarker('priority-low')}`,
      })

      const response = await officerComplaintApi.listComplaints({ userId, priority: 'CRITICAL' })
      const { data } = await response.json()
      const ids = data.map((complaint: { id: string }) => complaint.id)
      expect(ids).toContain((await critical.json()).id)
      expect(ids).not.toContain((await low.json()).id)
    })

    test('06.04.06 - Pagination: limit and offset page through a known set deterministically', async ({
      officerComplaintApi,
    }) => {
      const userId = uniqueMarker('search-paging-user')
      for (let index = 0; index < 3; index += 1) {
        await officerComplaintApi.createComplaintForUser({
          userId,
          subjectCategory: 'OTHER',
          description: `Automated regression test: ${uniqueMarker(`paging-${index}`)}`,
        })
      }

      const firstPage = await (await officerComplaintApi.listComplaints({ userId, limit: 2, offset: 0 })).json()
      const secondPage = await (await officerComplaintApi.listComplaints({ userId, limit: 2, offset: 2 })).json()
      expect(firstPage.metadata.total).toBe(3)
      expect(firstPage.data).toHaveLength(2)
      expect(secondPage.data).toHaveLength(1)

      const firstPageIds = new Set(firstPage.data.map((c: { id: string }) => c.id))
      const secondPageIds = new Set(secondPage.data.map((c: { id: string }) => c.id))
      expect([...firstPageIds].some((id) => secondPageIds.has(id))).toBe(false)
    })

    test('06.04.07 - An unrecognized status filter value returns 422, not an empty page', async ({
      officerComplaintApi,
    }) => {
      const response = await officerComplaintApi.listComplaints({
        status: 'NOT_A_REAL_STATUS' as unknown as ComplaintStatus,
      })
      expect(response.status()).toBe(422)
      expect((await response.json()).code).toBe('CO-4002')
    })

    test('06.04.08 - An unrecognized priority filter value returns 422, not an empty page', async ({
      officerComplaintApi,
    }) => {
      const response = await officerComplaintApi.listComplaints({
        priority: 'ULTRA' as unknown as ComplaintPriority,
      })
      expect(response.status()).toBe(422)
      expect((await response.json()).code).toBe('CO-4002')
    })
  })

  test.describe('Comments, internal notes, and status', () => {
    test('06.04.09 - A public officer reply is visible to the citizen; an internal note never is', async ({
      userComplaintApi,
      officerComplaintApi,
    }) => {
      const own = await userComplaintApi.createMyComplaint({
        subjectCategory: 'OTHER',
        description: `Automated regression test: ${uniqueMarker('officer-notes-visibility')}`,
      })
      const { id } = await own.json()

      const publicMessage = `Acknowledged, looking into this: ${uniqueMarker('public-reply')}`
      const internalMessage = `Escalating to legal: ${uniqueMarker('internal-note')}`
      await officerComplaintApi.addComment(id, { message: publicMessage, isPublic: true })
      await officerComplaintApi.addComment(id, { message: internalMessage, isPublic: false })

      const officerTimeline = await (await officerComplaintApi.getTimeline(id)).json()
      const officerMessages = officerTimeline.data.map((entry: { message: string }) => entry.message)
      expect(officerMessages).toContain(publicMessage)
      expect(officerMessages).toContain(internalMessage)

      const citizenTimeline = await (await userComplaintApi.getMyTimeline(id)).json()
      const citizenMessages = citizenTimeline.data.map((entry: { message: string }) => entry.message)
      expect(citizenMessages).toContain(publicMessage)
      expect(citizenMessages).not.toContain(internalMessage)
    })

    test('06.04.10 - An officer resolving with a comment + note succeeds and is never blocked by the note requirement', async ({
      officerComplaintApi,
    }) => {
      const created = await officerComplaintApi.createComplaintForUser({
        userId: uniqueMarker('officer-resolve-user'),
        subjectCategory: 'OTHER',
        description: `Automated regression test: ${uniqueMarker('officer-resolve')}`,
      })
      const { id } = await created.json()
      await officerComplaintApi.updateStatus(id, { toStatus: 'IN_PROGRESS' })

      // The status-only endpoint's note requirement (NOTE_REQUIRED_FOR_RESOLVED_ERROR) was always
      // intended for this path - unlike 06.02.09, the officer request bean does expose `note`.
      const resolved = await officerComplaintApi.updateStatus(id, {
        toStatus: 'RESOLVED',
        note: 'Resolved after internal review.',
      })
      expect(resolved.status()).toBe(200)
      expect((await resolved.json()).toStatus).toBe('RESOLVED')
    })

    test('06.04.11 - An officer resolving via status-only with no note is rejected with 422', async ({
      officerComplaintApi,
    }) => {
      const created = await officerComplaintApi.createComplaintForUser({
        userId: uniqueMarker('officer-resolve-no-note-user'),
        subjectCategory: 'OTHER',
        description: `Automated regression test: ${uniqueMarker('officer-resolve-no-note')}`,
      })
      const { id } = await created.json()
      await officerComplaintApi.updateStatus(id, { toStatus: 'IN_PROGRESS' })

      const response = await officerComplaintApi.updateStatus(id, { toStatus: 'RESOLVED' })
      expect(response.status()).toBe(422)
      expect((await response.json()).code).toBe('CO-4002')
    })
  })
})
