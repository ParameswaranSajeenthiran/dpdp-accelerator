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
import type { ComplaintCategory } from '../../clients/ComplaintApiClient'
import { uniqueMarker } from '../../utils/testData'

/**
 * End-to-end scenarios stitching together create/comment/status/attachments the way a real
 * grievance-redressal case actually plays out, rather than one call at a time - see
 * tests/06-complaints-api/README.md, "Scenarios", for the brainstorm behind each of these.
 */
test.describe('Real-world complaint scenarios (API)', () => {
  test('06.06.01 - A DATA_BREACH complaint\'s full lifecycle: intake, info request, citizen reply, officer resolution', async ({
    userComplaintApi,
    officerComplaintApi,
  }) => {
    const description = `Automated regression test: ${uniqueMarker('lifecycle-data-breach')}`
    const created = await userComplaintApi.createMyComplaint({ subjectCategory: 'DATA_BREACH', description })
    const complaint = await created.json()
    expect(complaint.status).toBe('OPEN')
    expect(complaint.priority).toBe('CRITICAL')

    // Officer picks it up.
    const pickedUp = await officerComplaintApi.updateStatus(complaint.id, { toStatus: 'IN_PROGRESS' })
    expect(pickedUp.ok()).toBe(true)

    // Officer needs more information from the citizen before proceeding.
    const infoRequest = await officerComplaintApi.addComment(complaint.id, {
      message: 'Can you confirm which service this data breach relates to?',
      isPublic: true,
      toStatus: 'WAITING_ON_CLIENT',
    })
    expect(infoRequest.ok()).toBe(true)

    // Citizen sees the request on their own timeline...
    const citizenTimeline = await (await userComplaintApi.getMyTimeline(complaint.id)).json()
    expect(
      citizenTimeline.data.some((entry: { message: string }) =>
        entry.message.includes('which service this data breach')),
    ).toBe(true)

    // ...and replying (message + toStatus together, one call) auto-routes it back for internal
    // review - StatusTransitionValidator only allows WAITING_ON_CLIENT -> AWAITING_INTERNAL_REVIEW.
    const reply = await userComplaintApi.addMyComment(complaint.id, {
      message: 'It relates to the mobile banking app login flow.',
      toStatus: 'AWAITING_INTERNAL_REVIEW',
    })
    expect(reply.ok()).toBe(true)
    expect((await reply.json()).toStatus).toBe('AWAITING_INTERNAL_REVIEW')

    // Officer resolves with a note (required by the status-only endpoint for RESOLVED).
    const resolved = await officerComplaintApi.updateStatus(complaint.id, {
      toStatus: 'RESOLVED',
      note: 'Confirmed and patched the mobile banking login flow; no further exposure.',
    })
    expect(resolved.ok()).toBe(true)

    const finalRecord = await (await userComplaintApi.getMyComplaint(complaint.id)).json()
    expect(finalRecord.status).toBe('RESOLVED')
    expect(finalRecord.priority).toBe('CRITICAL') // never changes after creation
  })

  test('06.06.02 - A citizen reopens a RESOLVED complaint by replying again, and the officer closes it a second time', async ({
    userComplaintApi,
    officerComplaintApi,
  }) => {
    const created = await userComplaintApi.createMyComplaint({
      subjectCategory: 'OTHER',
      description: `Automated regression test: ${uniqueMarker('reopen-flow')}`,
    })
    const { id } = await created.json()
    await officerComplaintApi.updateStatus(id, { toStatus: 'IN_PROGRESS' })
    await officerComplaintApi.updateStatus(id, { toStatus: 'RESOLVED', note: 'Believed resolved.' })

    // There's no "reopen" action for either role - only a reply can move a complaint out of
    // RESOLVED, and the only place it can go is AWAITING_INTERNAL_REVIEW.
    const reopened = await userComplaintApi.addMyComment(id, {
      message: 'This is actually still happening, please look again.',
      toStatus: 'AWAITING_INTERNAL_REVIEW',
    })
    expect(reopened.ok()).toBe(true)

    const afterReopen = await (await officerComplaintApi.getComplaint(id)).json()
    expect(afterReopen.status).toBe('AWAITING_INTERNAL_REVIEW')

    const reResolved = await officerComplaintApi.updateStatus(id, {
      toStatus: 'RESOLVED',
      note: 'Confirmed fixed on the second pass.',
    })
    expect(reResolved.ok()).toBe(true)
  })

  test('06.06.03 - Officer-assisted phone intake with mixed public/internal evidence, then resolution', async ({
    userComplaintApi,
    officerComplaintApi,
  }) => {
    const own = await userComplaintApi.createMyComplaint({
      subjectCategory: 'OTHER',
      description: `Automated regression test: ${uniqueMarker('learn-user-id-for-phone-intake')}`,
    })
    const { userId } = await own.json()

    const intake = await officerComplaintApi.createComplaintForUser({
      userId,
      subjectCategory: 'UNAUTHORIZED_DATA_SHARING',
      description: 'Complainant called in to report their data was shared with a third party without consent.',
    })
    const complaint = await intake.json()
    expect(complaint.priority).toBe('HIGH')

    // A citizen-visible acknowledgment of the call, plus an internal-only investigation note.
    await officerComplaintApi.uploadAttachments(
      complaint.id,
      [{ name: 'call-recording-transcript.pdf', mimeType: 'application/pdf', buffer: Buffer.from('transcript') }],
      true,
    )
    await officerComplaintApi.uploadAttachments(
      complaint.id,
      [{ name: 'internal-vendor-audit.pdf', mimeType: 'application/pdf', buffer: Buffer.from('confidential audit') }],
      false,
    )

    await officerComplaintApi.updateStatus(complaint.id, { toStatus: 'IN_PROGRESS' })
    await officerComplaintApi.updateStatus(complaint.id, {
      toStatus: 'RESOLVED',
      note: 'Vendor access revoked; data-sharing agreement terminated.',
    })

    const citizenView = await (await userComplaintApi.getMyComplaint(complaint.id)).json()
    expect(citizenView.status).toBe('RESOLVED')
    const citizenAttachmentNames = citizenView.attachments.map((a: { fileName: string }) => a.fileName)
    expect(citizenAttachmentNames).toContain('call-recording-transcript.pdf')
    expect(citizenAttachmentNames).not.toContain('internal-vendor-audit.pdf')
  })

  test('06.06.04 - Every complaint category is created with the priority GET /categories currently maps it to', async ({
    userComplaintApi,
  }) => {
    const categoriesResponse = await userComplaintApi.getMyCategories()
    const { data: categoryPriorities } = (await categoriesResponse.json()) as {
      data: { category: ComplaintCategory; priority: string }[]
    }
    expect(categoryPriorities.length).toBeGreaterThan(0)

    for (const { category, priority } of categoryPriorities) {
      const response = await userComplaintApi.createMyComplaint({
        subjectCategory: category,
        description: `Automated regression test: ${uniqueMarker(`category-${category}`)}`,
      })
      expect(response.status()).toBe(201)
      const body = await response.json()
      expect(body.priority).toBe(priority)
    }
  })

  test('06.06.05 - statutoryDueDate is consistently offset ahead of submittedAt across complaints', async ({
    userComplaintApi,
  }) => {
    // The offset (StatutoryDuePeriodPolicy, default 90 days) is a single JVM-wide, deployment-time
    // setting - rather than hardcoding "90 days" and risking a false failure against a deployment
    // that overrides CO_STATUTORY_DUE_PERIOD_DAYS, this checks that two complaints created back to
    // back get the *same* offset, and that the offset is a sane multi-day period.
    const first = await (
      await userComplaintApi.createMyComplaint({
        subjectCategory: 'OTHER',
        description: `Automated regression test: ${uniqueMarker('due-date-a')}`,
      })
    ).json()
    const second = await (
      await userComplaintApi.createMyComplaint({
        subjectCategory: 'OTHER',
        description: `Automated regression test: ${uniqueMarker('due-date-b')}`,
      })
    ).json()

    const offsetA = first.statutoryDueDate - first.submittedAt
    const offsetB = second.statutoryDueDate - second.submittedAt
    expect(offsetA).toBe(offsetB)

    const oneDayMs = 24 * 60 * 60 * 1000
    expect(offsetA).toBeGreaterThan(oneDayMs) // sane lower bound - more than a single day
    expect(offsetA).toBeLessThan(400 * oneDayMs) // sane upper bound - less than a year
  })

  test('06.06.06 - A citizen and an officer replying at nearly the same time both land in the timeline', async ({
    userComplaintApi,
    officerComplaintApi,
  }) => {
    const created = await userComplaintApi.createMyComplaint({
      subjectCategory: 'OTHER',
      description: `Automated regression test: ${uniqueMarker('concurrent-replies')}`,
    })
    const { id } = await created.json()

    const citizenMessage = `Citizen follow-up: ${uniqueMarker('concurrent-citizen')}`
    const officerMessage = `Officer follow-up: ${uniqueMarker('concurrent-officer')}`

    const [citizenResult, officerResult] = await Promise.all([
      userComplaintApi.addMyComment(id, { message: citizenMessage }),
      officerComplaintApi.addComment(id, { message: officerMessage, isPublic: true }),
    ])
    expect(citizenResult.ok()).toBe(true)
    expect(officerResult.ok()).toBe(true)

    const timeline = await (await officerComplaintApi.getTimeline(id)).json()
    const messages = timeline.data.map((entry: { message: string }) => entry.message)
    expect(messages).toContain(citizenMessage)
    expect(messages).toContain(officerMessage)
    // Both landed exactly once each - a shared-write race didn't silently drop or duplicate one.
    expect(messages.filter((m: string) => m === citizenMessage)).toHaveLength(1)
    expect(messages.filter((m: string) => m === officerMessage)).toHaveLength(1)
  })
})
