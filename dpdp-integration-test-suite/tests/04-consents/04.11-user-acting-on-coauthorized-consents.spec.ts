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

import { test, expect, getPersonaState, loginAsUser, pageForPersonaState } from '../../fixtures/auth.fixtures'
import { ConsentApiClient } from '../../clients/ConsentApiClient'
import { ConsentDetailPage } from '../../pages/ConsentDetailPage'
import { authHeadersFromPersonaState } from '../../utils/authStorage'
import { seedConsentViaApi } from '../../utils/consentSetup'
import type { AuthorizationEntry } from '../../clients/ConsentApiClient'

/**
 * A Co-Authorized Consent: `target.personas.user` is both the subject and one of two named
 * authorisers, deciding alongside `target.personas.user2` exactly the same way any other
 * authoriser would. Nothing before this file ever seeds more than one `authorizations` entry -
 * `seedConsentViaApi`'s parameter already accepts a list, this is just the first caller to pass
 * more than one. IS's own authorize endpoint (see ConsentApiClient.authorizeMyConsent) moves the
 * consent to ACTIVE only once every listed authoriser has approved, and to REJECTED as soon as
 * any single one rejects - both asserted directly below.
 */
test.describe('User acting on Co-Authorized Consents (UI)', () => {
  function coAuthorizedEntries(subjectUsername: string, otherUsername: string): AuthorizationEntry[] {
    return [
      { userId: subjectUsername, type: 'USER' },
      { userId: otherUsername, type: 'USER' },
    ]
  }

  test('04.11.01 - Both co-authorisers independently see Approve and Reject while their own decision is pending', async ({
    browser,
    target,
    consentAdminConsentApi,
  }) => {
    const other = target.personas.user2
    const { consentId } = await seedConsentViaApi(
      consentAdminConsentApi,
      target.personas.user.username,
      'PENDING',
      undefined,
      undefined,
      coAuthorizedEntries(target.personas.user.username, other.username),
    )

    const userPage = await loginAsUser(browser)
    const userDetailPage = new ConsentDetailPage(userPage, 'self')
    await userDetailPage.goto(consentId)
    await expect(userDetailPage.actionAvailable('approve')).toBeVisible()
    await expect(userDetailPage.actionAvailable('reject')).toBeVisible()

    const otherPersonaState = await getPersonaState(browser, 'user-2', other)
    const otherPage = await pageForPersonaState(browser, otherPersonaState, other)
    const otherDetailPage = new ConsentDetailPage(otherPage, 'self')
    await otherDetailPage.goto(consentId)
    await expect(otherDetailPage.actionAvailable('approve')).toBeVisible()
    await expect(otherDetailPage.actionAvailable('reject')).toBeVisible()

    await userPage.context().close()
    await otherPage.context().close()
  })

  test('04.11.02 - Once one co-authoriser approves, they see a waiting message while the other still has their own Approve/Reject', async ({
    browser,
    target,
    consentAdminConsentApi,
  }) => {
    const other = target.personas.user2
    const { consentId } = await seedConsentViaApi(
      consentAdminConsentApi,
      target.personas.user.username,
      'PENDING',
      undefined,
      undefined,
      coAuthorizedEntries(target.personas.user.username, other.username),
    )

    const userPage = await loginAsUser(browser)
    const userDetailPage = new ConsentDetailPage(userPage, 'self')
    await userDetailPage.goto(consentId)
    await userDetailPage.openActionDialog('approve')
    await userDetailPage.confirmAction('approve')

    // The aggregate consent stays Pending (waiting on `other`), so there's no visible state-chip
    // change to gate on here - wait on this caller's own authorization row instead, or the fresh
    // navigation below can race ahead of the mutation actually landing server-side.
    await expect(userDetailPage.authorizationRow(target.personas.user.username)).toContainText(
      'Approved',
    )

    // A fresh navigation - the aggregate consent is still Pending (waiting on `other`), so this
    // proves the gate is on the caller's own entry, not the aggregate state.
    await userDetailPage.goto(consentId)
    await expect(userDetailPage.actionAvailable('approve')).toHaveCount(0)
    await expect(userDetailPage.actionAvailable('reject')).toHaveCount(0)
    await expect(
      userPage.getByText("You've made your decision. Waiting for the rest to decide."),
    ).toBeVisible()

    const otherPersonaState = await getPersonaState(browser, 'user-2', other)
    const otherPage = await pageForPersonaState(browser, otherPersonaState, other)
    const otherDetailPage = new ConsentDetailPage(otherPage, 'self')
    await otherDetailPage.goto(consentId)
    await expect(otherDetailPage.actionAvailable('approve')).toBeVisible()
    await expect(otherDetailPage.actionAvailable('reject')).toBeVisible()

    await userPage.context().close()
    await otherPage.context().close()
  })

  test('04.11.03 - Once every co-authoriser has approved, the consent is Active and either of them can revoke it', async ({
    browser,
    request,
    target,
    consentAdminConsentApi,
  }) => {
    const other = target.personas.user2
    const { consentId } = await seedConsentViaApi(
      consentAdminConsentApi,
      target.personas.user.username,
      'PENDING',
      undefined,
      undefined,
      coAuthorizedEntries(target.personas.user.username, other.username),
    )

    const otherPersonaState = await getPersonaState(browser, 'user-2', other)
    const otherConsentApi = new ConsentApiClient(
      request,
      authHeadersFromPersonaState(otherPersonaState),
      target.tenantDomain,
    )
    expect((await otherConsentApi.authorizeMyConsent(consentId, 'APPROVED')).ok()).toBe(true)

    const userPage = await loginAsUser(browser)
    const userDetailPage = new ConsentDetailPage(userPage, 'self')
    await userDetailPage.goto(consentId)
    await userDetailPage.openActionDialog('approve')
    await userDetailPage.confirmAction('approve')
    await expect(userPage.getByText('Active', { exact: true }).first()).toBeVisible()

    // `user` revokes, having been one of two approvers, not "whoever approved last" specifically -
    // proves revoke is available to anyone with a stake once Active, not tied to a single owner.
    await userDetailPage.openActionDialog('revoke')
    await userDetailPage.confirmAction('revoke')
    await expect(userPage.getByText('Revoked', { exact: true }).first()).toBeVisible()
    await userPage.context().close()
  })

  test('04.11.04 - A single co-authoriser rejecting ends the consent for both, immediately', async ({
    browser,
    request,
    target,
    consentAdminConsentApi,
  }) => {
    const other = target.personas.user2
    const { consentId } = await seedConsentViaApi(
      consentAdminConsentApi,
      target.personas.user.username,
      'PENDING',
      undefined,
      undefined,
      coAuthorizedEntries(target.personas.user.username, other.username),
    )

    const otherPersonaState = await getPersonaState(browser, 'user-2', other)
    const otherConsentApi = new ConsentApiClient(
      request,
      authHeadersFromPersonaState(otherPersonaState),
      target.tenantDomain,
    )
    expect((await otherConsentApi.authorizeMyConsent(consentId, 'REJECTED')).ok()).toBe(true)

    // `user` never decided (their own entry is still Pending) when `other`'s rejection ended the
    // consent - so `user` sees the generic message, not "You've rejected this consent."
    const userPage = await loginAsUser(browser)
    const userDetailPage = new ConsentDetailPage(userPage, 'self')
    await userDetailPage.goto(consentId)
    await expect(userDetailPage.actionAvailable('approve')).toHaveCount(0)
    await expect(userDetailPage.actionAvailable('reject')).toHaveCount(0)
    await expect(userPage.getByText('This consent has been rejected.')).toBeVisible()
    await userPage.context().close()

    const otherPage = await pageForPersonaState(browser, otherPersonaState, other)
    const otherDetailPage = new ConsentDetailPage(otherPage, 'self')
    await otherDetailPage.goto(consentId)
    await expect(otherPage.getByText("You've rejected this consent.")).toBeVisible()
    await otherPage.context().close()
  })

  test('04.11.05 - Revoking a still-Pending consent leaves an authoriser who already approved seeing only the revoked message', async ({
    browser,
    request,
    target,
    consentAdminConsentApi,
  }) => {
    const other = target.personas.user2
    const { consentId } = await seedConsentViaApi(
      consentAdminConsentApi,
      target.personas.user.username,
      'PENDING',
      undefined,
      undefined,
      coAuthorizedEntries(target.personas.user.username, other.username),
    )

    const otherPersonaState = await getPersonaState(browser, 'user-2', other)
    const otherConsentApi = new ConsentApiClient(
      request,
      authHeadersFromPersonaState(otherPersonaState),
      target.tenantDomain,
    )
    // `other` approves, but the aggregate stays Pending, waiting on `user`.
    expect((await otherConsentApi.authorizeMyConsent(consentId, 'APPROVED')).ok()).toBe(true)

    // Only an admin can revoke a still-Pending consent (isRevokableByAdmin, consentAuthorization.ts) -
    // a regular self-service revoke requires Active.
    const revokeResponse = await consentAdminConsentApi.revokeAdminConsent(consentId)
    expect(revokeResponse.ok()).toBe(true)

    // Regression for wso2/dpdp-accelerator#271: a stale APPROVED on `other`'s own entry must never
    // resurface a button once the aggregate consent is Revoked.
    const otherPage = await pageForPersonaState(browser, otherPersonaState, other)
    const otherDetailPage = new ConsentDetailPage(otherPage, 'self')
    await otherDetailPage.goto(consentId)
    await expect(otherDetailPage.actionAvailable('approve')).toHaveCount(0)
    await expect(otherDetailPage.actionAvailable('reject')).toHaveCount(0)
    await expect(otherDetailPage.actionAvailable('revoke')).toHaveCount(0)
    await expect(otherPage.getByText('This consent has been revoked.')).toBeVisible()
    await otherPage.context().close()
  })
})
