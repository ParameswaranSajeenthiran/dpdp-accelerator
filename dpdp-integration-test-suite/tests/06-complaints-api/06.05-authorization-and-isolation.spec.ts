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

import { test, expect, getSecondUserComplaintApi, hasSecondUser } from '../../fixtures/auth.fixtures'
import { complaintsApiUrl, meComplaintsApiUrl } from '../../utils/env'
import { uniqueMarker } from '../../utils/testData'

/**
 * Authentication/authorization mechanics (TokenIntrospectionFilter, ScopeAuthorizationFilter) and
 * cross-user ownership isolation (ComplaintServiceImpl#requireOwnedComplaint) - as opposed to the
 * business-rule visibility covered in 06.04.09 (public reply vs. internal note).
 *
 * Only one direction of the self/any scope cross-check is meaningful here: TEST_USER (self-only)
 * calling an officer (/complaints/*) endpoint must be rejected. The reverse isn't testable through
 * scope alone - the officer persona (dpdp-consent-admin) is granted every portal:complaints:*
 * scope, self included (DPDPConsentPortalRoleProvisioningUtil), so an officer's token calling
 * /me/complaints/* legitimately succeeds; it just acts as whichever user the token belongs to.
 */
test.describe('Complaint API authorization and ownership isolation', () => {
  test('06.05.01 - No Authorization header returns 401 CO-4010 on both surfaces', async ({ request }) => {
    const selfResponse = await request.get(meComplaintsApiUrl('/categories'))
    expect(selfResponse.status()).toBe(401)
    expect((await selfResponse.json()).code).toBe('CO-4010')

    const officerResponse = await request.get(complaintsApiUrl('/categories'))
    expect(officerResponse.status()).toBe(401)
    expect((await officerResponse.json()).code).toBe('CO-4010')
  })

  test('06.05.02 - A malformed bearer token returns 401 CO-4010', async ({ request }) => {
    const response = await request.get(meComplaintsApiUrl('/categories'), {
      headers: { Authorization: 'Bearer this-is-not-a-real-token' },
    })
    expect(response.status()).toBe(401)
    expect((await response.json()).code).toBe('CO-4010')
  })

  test("06.05.03 - A Data Principal's token is rejected by the officer surface with 403 CO-4030", async ({
    userComplaintApi,
  }) => {
    // ComplaintApiClient methods are named by which namespace they call, not which persona
    // constructed the client - this deliberately uses userComplaintApi (self-scope-only headers)
    // against an officer-namespace method.
    const response = await userComplaintApi.listComplaints()
    expect(response.status()).toBe(403)
    expect((await response.json()).code).toBe('CO-4030')
  })

  test.describe('Ownership isolation (needs TEST_USER_2_USERNAME/PASSWORD)', () => {
    test('06.05.04 - A second user cannot read, comment on, transition, or upload to the first user\'s complaint', async ({
      browser,
      request,
      userComplaintApi,
    }) => {
      test.skip(!hasSecondUser(), 'TEST_USER_2_USERNAME/PASSWORD is not configured')
      const otherUserApi = await getSecondUserComplaintApi(browser, request)
      if (!otherUserApi) {
        throw new Error('Unreachable: hasSecondUser() already checked this above.')
      }

      const owned = await userComplaintApi.createMyComplaint({
        subjectCategory: 'OTHER',
        description: `Automated regression test: ${uniqueMarker('isolation-target')}`,
      })
      const { id } = await owned.json()

      // requireOwnedComplaint returns 404 (not 403) on a mismatch - "to avoid confirming the ID's
      // existence to a caller who shouldn't see it" (complaint-server-API.yaml).
      const getResponse = await otherUserApi.getMyComplaint(id)
      expect(getResponse.status()).toBe(404)

      const commentResponse = await otherUserApi.addMyComment(id, { message: 'I should not be able to post this.' })
      expect(commentResponse.status()).toBe(404)

      const statusResponse = await otherUserApi.updateMyStatus(id, 'IN_PROGRESS')
      expect(statusResponse.status()).toBe(404)

      const uploadResponse = await otherUserApi.uploadMyAttachments(id, [
        { name: 'intrusion.pdf', mimeType: 'application/pdf', buffer: Buffer.from('should not land') },
      ])
      expect(uploadResponse.status()).toBe(404)

      // The rightful owner is unaffected by the rejected attempts above.
      const stillOwned = await (await userComplaintApi.getMyComplaint(id)).json()
      expect(stillOwned.status).toBe('OPEN')
    })

    test('06.05.05 - A second user cannot download the first user\'s public attachment by guessing its id', async ({
      browser,
      request,
      userComplaintApi,
    }) => {
      test.skip(!hasSecondUser(), 'TEST_USER_2_USERNAME/PASSWORD is not configured')
      const otherUserApi = await getSecondUserComplaintApi(browser, request)
      if (!otherUserApi) {
        throw new Error('Unreachable: hasSecondUser() already checked this above.')
      }

      const owned = await userComplaintApi.createMyComplaint({
        subjectCategory: 'OTHER',
        description: `Automated regression test: ${uniqueMarker('isolation-attachment')}`,
      })
      const { id } = await owned.json()
      const uploaded = await userComplaintApi.uploadMyAttachments(id, [
        { name: 'evidence.pdf', mimeType: 'application/pdf', buffer: Buffer.from('owner-only') },
      ])
      const [{ attachmentId }] = await uploaded.json()

      // The complaint itself already 404s for a non-owner, so the attachment is unreachable
      // through it - same requireOwnedComplaint guard as 06.05.04.
      const response = await otherUserApi.downloadMyAttachment(id, attachmentId)
      expect(response.status()).toBe(404)
    })
  })
})
