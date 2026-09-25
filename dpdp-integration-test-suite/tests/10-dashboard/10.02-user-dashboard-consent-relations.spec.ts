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

import { test } from '../../fixtures/auth.fixtures'
import { DashboardPage } from '../../pages/DashboardPage'
import {
  SHORT_EXPIRY_MS,
  authorizeConsentViaApi,
  expectConsentStateViaApi,
  revokeConsentViaApi,
  seedCatalogViaApi,
  seedConsentForCatalogViaApi,
  waitUntilConsentExpiredViaApi,
} from '../../utils/consentSetup'
import { expectDashboardCounts } from '../../utils/dashboardCounts'

/**
 * Which consents count on whose dashboard, by how each person relates to them. Two throwaway
 * accounts, U and V, are the parties whose counts are asserted (exact, because nothing else can
 * write a consent naming them); the `user` persona, P, is an unrelated third party whose consents
 * must never count for either.
 *
 * The dashboard counts with `relation=ANY` (#273/#274 - it once sent none, and IS defaults to
 * SUBJECT). IS's ANY is "subject is me OR an authorization names me" inside one row filter, so a
 * consent matching both counts once, and the match is on the authorization's user id alone, not
 * its type. A consent's state comes from all its authorizations together: any REVOKED -> REVOKED,
 * else all APPROVED -> ACTIVE, else any REJECTED -> REJECTED, else PENDING (IS
 * ConsentManagerImpl.computeConsentStatus).
 */
test.describe('User dashboard consent relations', () => {
  test('10.02.01 - The dashboard counts every consent the user is the subject or an authorizer of, once each, and no others', async ({
    throwawayAccounts,
    consentAdminConsentApi,
    target,
  }) => {
    const u = await throwawayAccounts.create('dpdp-e2e-dash-rel-u')
    const v = await throwawayAccounts.create('dpdp-e2e-dash-rel-v')
    const U = u.user.username
    const V = v.user.username
    const P = target.personas.user.username
    const catalog = await seedCatalogViaApi(consentAdminConsentApi)
    const seed = (subjectId: string, authorizations?: Array<{ userId: string; type: string }>) =>
      seedConsentForCatalogViaApi(consentAdminConsentApi, catalog, {
        subjectId,
        ...(authorizations ? { state: 'PENDING', authorizations } : { state: 'ACTIVE' }),
      })

    await Promise.all([
      seed(U), // C1: U's own, no authorizations
      seed(U, [{ userId: V, type: 'USER' }]), // C2: U's consent, decided by someone else
      seed(V, [{ userId: U, type: 'USER' }]), // C3: someone else's consent U decides
      seed(U, [{ userId: U, type: 'USER' }]), // C4: U is subject and authorizer
      seed(U, [
        { userId: U, type: 'USER' },
        { userId: V, type: 'USER' },
      ]), // C5: U is subject and one of two authorizers
      seed(P, [
        { userId: U, type: 'USER' },
        { userId: V, type: 'USER' },
      ]), // C6: a third party's consent, U one of two authorizers
      seed(V, [{ userId: U, type: 'PARENT' }]), // C7: as C3, with a non-USER authorization type
      seed(P), // C8: unrelated to U and V
      seed(V), // C9: V's own
    ])

    const uDashboard = new DashboardPage(u.session.page)
    const vDashboard = new DashboardPage(v.session.page)
    await Promise.all([uDashboard.goto(), vDashboard.goto()])

    // U: C1 active; C2-C7 pending. Not C8 or C9. C4 and C5 once each.
    await expectDashboardCounts(uDashboard, {
      'consent-pending': 6,
      'consent-active': 1,
      'consent-rejected': 0,
      'consent-revoked': 0,
      'consent-expired': 0,
    })
    // V: C9 active; C2, C3, C5, C6, C7 pending. Not C1, C4 or C8.
    await expectDashboardCounts(vDashboard, {
      'consent-pending': 5,
      'consent-active': 1,
      'consent-rejected': 0,
      'consent-revoked': 0,
      'consent-expired': 0,
    })
  })

  /**
   * Four consents, one per kind of change, each ending in a different state - so every card
   * belongs to exactly one scenario and a wrong number names the one that broke.
   */
  test("10.02.02 - Consent state changes by any party are reflected on every party's dashboard", async ({
    throwawayAccounts,
    consentAdminConsentApi,
    target,
  }) => {
    const u = await throwawayAccounts.create('dpdp-e2e-dash-chg-u')
    const v = await throwawayAccounts.create('dpdp-e2e-dash-chg-v')
    const U = u.user.username
    const V = v.user.username
    const P = target.personas.user.username
    const catalog = await seedCatalogViaApi(consentAdminConsentApi)
    const bothAuthorize = [
      { userId: U, type: 'USER' },
      { userId: V, type: 'USER' },
    ]

    const [approvedByBoth, rejectedByOne, revokedByAuthorizer] = await Promise.all([
      seedConsentForCatalogViaApi(consentAdminConsentApi, catalog, {
        subjectId: P,
        state: 'PENDING',
        authorizations: bothAuthorize,
      }),
      seedConsentForCatalogViaApi(consentAdminConsentApi, catalog, {
        subjectId: P,
        state: 'PENDING',
        authorizations: bothAuthorize,
      }),
      seedConsentForCatalogViaApi(consentAdminConsentApi, catalog, {
        subjectId: V,
        state: 'PENDING',
        authorizations: [{ userId: U, type: 'USER' }],
      }),
    ])
    const uDashboard = new DashboardPage(u.session.page)
    const vDashboard = new DashboardPage(v.session.page)

    // One of two authorizers approving leaves the consent pending for everyone.
    await authorizeConsentViaApi(u.consentApi, approvedByBoth.consentId, 'APPROVED')
    await expectConsentStateViaApi(u.consentApi, approvedByBoth.consentId, 'PENDING')
    await Promise.all([uDashboard.goto(), vDashboard.goto()])
    for (const dashboard of [uDashboard, vDashboard]) {
      await expectDashboardCounts(dashboard, { 'consent-pending': 3, 'consent-active': 0 })
    }

    // Seeded only now, so the pending counts above can't be caught mid-expiry; the remaining
    // setup below then covers most of its wait.
    const expiring = await seedConsentForCatalogViaApi(consentAdminConsentApi, catalog, {
      subjectId: V,
      state: 'PENDING',
      authorizations: [{ userId: U, type: 'USER' }],
      expiryTime: Date.now() + SHORT_EXPIRY_MS,
    })

    await authorizeConsentViaApi(v.consentApi, approvedByBoth.consentId, 'APPROVED')
    await expectConsentStateViaApi(v.consentApi, approvedByBoth.consentId, 'ACTIVE')

    // V rejects while U hasn't decided - one rejection is enough.
    await authorizeConsentViaApi(v.consentApi, rejectedByOne.consentId, 'REJECTED')
    await expectConsentStateViaApi(u.consentApi, rejectedByOne.consentId, 'REJECTED')

    // Revoked by its authorizer, not its subject: IS's self-service revoke acts on the caller's own
    // authorization, so on a consent with authorizers the subject's revoke changes nothing.
    await authorizeConsentViaApi(u.consentApi, revokedByAuthorizer.consentId, 'APPROVED')
    await expectConsentStateViaApi(v.consentApi, revokedByAuthorizer.consentId, 'ACTIVE')
    await revokeConsentViaApi(u.consentApi, revokedByAuthorizer.consentId)
    await expectConsentStateViaApi(v.consentApi, revokedByAuthorizer.consentId, 'REVOKED')

    await waitUntilConsentExpiredViaApi(v.consentApi, expiring.consentId)

    await Promise.all([uDashboard.goto(), vDashboard.goto()])
    for (const dashboard of [uDashboard, vDashboard]) {
      await expectDashboardCounts(dashboard, {
        'consent-pending': 0,
        'consent-active': 1,
        'consent-rejected': 1,
        'consent-revoked': 1,
        'consent-expired': 1,
      })
    }
  })
})
