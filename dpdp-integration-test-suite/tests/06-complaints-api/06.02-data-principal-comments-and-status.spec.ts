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
import type { ComplaintApiClient } from '../../clients/ComplaintApiClient'
import { uniqueMarker } from '../../utils/testData'

async function createOpenComplaint(api: ComplaintApiClient, label: string): Promise<string> {
  const response = await api.createMyComplaint({
    subjectCategory: 'OTHER',
    description: `Automated regression test: ${uniqueMarker(label)}`,
  })
  const { id } = await response.json()
  return id as string
}

/**
 * A Data Principal replying in their own complaint's thread and transitioning its status, via
 * /me/complaints/{id}/comments and /me/complaints/{id}/status - ComplaintEventServiceImpl#addComment
 * and #updateStatus, StatusTransitionValidator for the state machine. See
 * tests/06-complaints-api/README.md for "A likely bug found while writing this suite" - 06.02.08
 * and 06.02.09 below are that finding's two halves, encoded as regression tests.
 */
test.describe('Data Principal comments and status transitions (API)', () => {
  test('06.02.01 - Adding a comment appends a public, USER-authored entry to the timeline', async ({
    userComplaintApi,
  }) => {
    const complaintId = await createOpenComplaint(userComplaintApi, 'comment-basic')
    const message = `Automated reply: ${uniqueMarker('reply')}`

    const response = await userComplaintApi.addMyComment(complaintId, { message })
    expect(response.status()).toBe(200)
    const comment = await response.json()
    expect(comment.message).toBe(message)
    expect(comment.actorRole).toBe('USER')
    expect(comment.isPublic).toBe(true)
    expect(comment.toStatus).toBeNull()

    const timeline = await userComplaintApi.getMyTimeline(complaintId)
    const { data } = await timeline.json()
    expect(data.some((entry: { message: string }) => entry.message === message)).toBe(true)
  })

  test('06.02.02 - An empty message is rejected with 422 CO-4002', async ({ userComplaintApi }) => {
    const complaintId = await createOpenComplaint(userComplaintApi, 'comment-empty')
    const response = await userComplaintApi.addMyComment(complaintId, { message: '   ' })
    expect(response.status()).toBe(422)
    expect((await response.json()).code).toBe('CO-4002')
  })

  test('06.02.03 - A message over 5000 characters is rejected with 422 CO-4002', async ({ userComplaintApi }) => {
    const complaintId = await createOpenComplaint(userComplaintApi, 'comment-too-long')
    const response = await userComplaintApi.addMyComment(complaintId, { message: 'x'.repeat(5001) })
    expect(response.status()).toBe(422)
    expect((await response.json()).code).toBe('CO-4002')
  })

  test('06.02.04 - A valid direct status transition succeeds via the status-only endpoint', async ({
    userComplaintApi,
  }) => {
    const complaintId = await createOpenComplaint(userComplaintApi, 'status-valid')
    // OPEN -> IN_PROGRESS is allowed (StatusTransitionValidator) and needs no note.
    const response = await userComplaintApi.updateMyStatus(complaintId, 'IN_PROGRESS')
    expect(response.status()).toBe(200)
    const body = await response.json()
    expect(body.toStatus).toBe('IN_PROGRESS')

    const complaint = await (await userComplaintApi.getMyComplaint(complaintId)).json()
    expect(complaint.status).toBe('IN_PROGRESS')
  })

  test('06.02.05 - An invalid direct status transition is rejected with 409 CO-4090', async ({
    userComplaintApi,
  }) => {
    const complaintId = await createOpenComplaint(userComplaintApi, 'status-invalid')
    // OPEN -> RESOLVED is never allowed directly - a complaint must pass through IN_PROGRESS or
    // AWAITING_INTERNAL_REVIEW first (StatusTransitionValidator's own doc comment).
    const response = await userComplaintApi.updateMyStatus(complaintId, 'RESOLVED')
    const body = await response.json()
    // Note this is NOT the note-required bug below - OPEN->RESOLVED is rejected as an invalid
    // transition (409) before the note check ever runs, regardless of actor.
    expect(response.status()).toBe(409)
    expect(body.code).toBe('CO-4090')
  })

  test('06.02.06 - Replying with toStatus on a valid transition moves the complaint and records it on the same entry', async ({
    userComplaintApi,
  }) => {
    const complaintId = await createOpenComplaint(userComplaintApi, 'comment-with-transition')
    const message = `Please gather more information: ${uniqueMarker('waiting')}`

    const response = await userComplaintApi.addMyComment(complaintId, { message, toStatus: 'WAITING_ON_CLIENT' })
    expect(response.status()).toBe(200)
    const comment = await response.json()
    expect(comment.fromStatus).toBe('OPEN')
    expect(comment.toStatus).toBe('WAITING_ON_CLIENT')

    const complaint = await (await userComplaintApi.getMyComplaint(complaintId)).json()
    expect(complaint.status).toBe('WAITING_ON_CLIENT')
  })

  test('06.02.07 - Replying with toStatus on an invalid transition is rejected with 409 and does not move the complaint', async ({
    userComplaintApi,
  }) => {
    const complaintId = await createOpenComplaint(userComplaintApi, 'comment-invalid-transition')
    const response = await userComplaintApi.addMyComment(complaintId, {
      message: 'Trying to jump straight to resolved.',
      toStatus: 'RESOLVED',
    })
    expect(response.status()).toBe(409)
    expect((await response.json()).code).toBe('CO-4090')

    const complaint = await (await userComplaintApi.getMyComplaint(complaintId)).json()
    expect(complaint.status).toBe('OPEN')
  })

  test('06.02.08 - [current behavior] A Data Principal CAN resolve their own complaint via a comment with toStatus', async ({
    userComplaintApi,
  }) => {
    const complaintId = await createOpenComplaint(userComplaintApi, 'self-resolve-via-comment')
    await userComplaintApi.updateMyStatus(complaintId, 'IN_PROGRESS')

    // Unlike the status-only endpoint (06.02.09), ComplaintEventServiceImpl#addComment's
    // status-transition branch never enforces NOTE_REQUIRED_FOR_RESOLVED_ERROR - it has no
    // actor-role check on that rule at all, so this path works for a citizen today.
    const response = await userComplaintApi.addMyComment(complaintId, {
      message: 'That resolves it, thanks - closing this out.',
      toStatus: 'RESOLVED',
    })
    expect(response.status()).toBe(200)
    expect((await response.json()).toStatus).toBe('RESOLVED')
  })

  test('06.02.09 - [likely bug] A Data Principal can NEVER resolve their own complaint via the status-only endpoint', async ({
    userComplaintApi,
  }) => {
    // See tests/06-complaints-api/README.md, "A likely bug found while writing this suite".
    // complaint-server-API.yaml's own example for this exact endpoint is
    // {"toStatus": "RESOLVED"} - but MeComplaintStatusUpdateRequestBean has no `note` field, and
    // ComplaintHandler#updateOwnStatus always calls the shared updateStatus() with note=null,
    // which unconditionally 422s on toStatus=RESOLVED with no note - for every actor role, not
    // just officers. This test documents that CURRENT behavior so a fix (adding the actor-role
    // guard, or adding a note field to the Me request) shows up here as an intentional test
    // update, not a silent regression either way.
    const complaintId = await createOpenComplaint(userComplaintApi, 'self-resolve-via-status-bug')
    await userComplaintApi.updateMyStatus(complaintId, 'IN_PROGRESS')

    const response = await userComplaintApi.updateMyStatus(complaintId, 'RESOLVED')
    expect(response.status()).toBe(422)
    const body = await response.json()
    expect(body.code).toBe('CO-4002')
    expect(body.description).toMatch(/note/i)

    const complaint = await (await userComplaintApi.getMyComplaint(complaintId)).json()
    expect(complaint.status).toBe('IN_PROGRESS')
  })

  test('06.02.10 - Replying to a RESOLVED complaint reopens it into AWAITING_INTERNAL_REVIEW', async ({
    userComplaintApi,
  }) => {
    const complaintId = await createOpenComplaint(userComplaintApi, 'reopen-after-resolve')
    await userComplaintApi.updateMyStatus(complaintId, 'IN_PROGRESS')
    // Resolve via the comment path (06.02.08) since the status-only path can't self-resolve (06.02.09).
    await userComplaintApi.addMyComment(complaintId, { message: 'Resolved, thanks.', toStatus: 'RESOLVED' })

    const reopen = await userComplaintApi.addMyComment(complaintId, {
      message: `Actually, this came back: ${uniqueMarker('reopen')}`,
      toStatus: 'AWAITING_INTERNAL_REVIEW',
    })
    expect(reopen.status()).toBe(200)
    const comment = await reopen.json()
    expect(comment.fromStatus).toBe('RESOLVED')
    expect(comment.toStatus).toBe('AWAITING_INTERNAL_REVIEW')

    const complaint = await (await userComplaintApi.getMyComplaint(complaintId)).json()
    expect(complaint.status).toBe('AWAITING_INTERNAL_REVIEW')
  })
})
