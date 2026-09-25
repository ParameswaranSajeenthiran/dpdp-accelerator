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

/**
 * A Delegated Consent: `target.personas.user` is the subject but never appears in
 * `authorizations`, so they have no personal stake in the decision - `target.personas.user2`
 * stands in as the sole named authoriser, the same "parent decides for the child" shape
 * 04.07.04 already uses for history attribution. This file covers what the subject and the
 * authoriser each see on the detail page, which 04.07.04 never asserts (it only checks who
 * history attributes the decision to). See getSelfConsentActionView (consentAuthorization.ts):
 * a caller with no `authorizations` entry on a consent that has any is a pure observer, at every
 * state, regardless of who else decides.
 */
test.describe('User acting on Delegated Consents (UI)', () => {
  test('04.10.01 - The subject of a Pending delegated consent sees only a waiting message, never a button', async ({
    browser,
    target,
    consentAdminConsentApi,
  }) => {
    const authoriser = target.personas.user2
    const { consentId } = await seedConsentViaApi(
      consentAdminConsentApi,
      target.personas.user.username,
      'PENDING',
      undefined,
      undefined,
      [{ userId: authoriser.username, type: 'PARENT' }],
    )

    const subjectPage = await loginAsUser(browser)
    const detailPage = new ConsentDetailPage(subjectPage, 'self')
    await detailPage.goto(consentId)
    await expect(detailPage.actionAvailable('approve')).toHaveCount(0)
    await expect(detailPage.actionAvailable('reject')).toHaveCount(0)
    await expect(detailPage.actionAvailable('revoke')).toHaveCount(0)
    await expect(subjectPage.getByText('Waiting for authoriser approval.')).toBeVisible()
    await subjectPage.context().close()
  })

  test('04.10.02 - The subject of an Active delegated consent (approved by the authoriser) sees only an approved message', async ({
    browser,
    request,
    target,
    consentAdminConsentApi,
  }) => {
    const authoriser = target.personas.user2
    const { consentId } = await seedConsentViaApi(
      consentAdminConsentApi,
      target.personas.user.username,
      'PENDING',
      undefined,
      undefined,
      [{ userId: authoriser.username, type: 'PARENT' }],
    )

    const authoriserPersonaState = await getPersonaState(browser, 'user-2', authoriser)
    const authoriserConsentApi = new ConsentApiClient(
      request,
      authHeadersFromPersonaState(authoriserPersonaState),
      target.tenantDomain,
    )
    const authorizeResponse = await authoriserConsentApi.authorizeMyConsent(consentId, 'APPROVED')
    expect(authorizeResponse.ok()).toBe(true)

    const subjectPage = await loginAsUser(browser)
    const detailPage = new ConsentDetailPage(subjectPage, 'self')
    await detailPage.goto(consentId)
    await expect(detailPage.actionAvailable('approve')).toHaveCount(0)
    await expect(detailPage.actionAvailable('reject')).toHaveCount(0)
    await expect(detailPage.actionAvailable('revoke')).toHaveCount(0)
    await expect(subjectPage.getByText('This consent has been approved.')).toBeVisible()
    await subjectPage.context().close()
  })

  test('04.10.03 - The subject of a Rejected delegated consent (rejected by the authoriser) sees only a rejected message', async ({
    browser,
    request,
    target,
    consentAdminConsentApi,
  }) => {
    const authoriser = target.personas.user2
    const { consentId } = await seedConsentViaApi(
      consentAdminConsentApi,
      target.personas.user.username,
      'PENDING',
      undefined,
      undefined,
      [{ userId: authoriser.username, type: 'PARENT' }],
    )

    const authoriserPersonaState = await getPersonaState(browser, 'user-2', authoriser)
    const authoriserConsentApi = new ConsentApiClient(
      request,
      authHeadersFromPersonaState(authoriserPersonaState),
      target.tenantDomain,
    )
    const authorizeResponse = await authoriserConsentApi.authorizeMyConsent(consentId, 'REJECTED')
    expect(authorizeResponse.ok()).toBe(true)

    const subjectPage = await loginAsUser(browser)
    const detailPage = new ConsentDetailPage(subjectPage, 'self')
    await detailPage.goto(consentId)
    await expect(detailPage.actionAvailable('approve')).toHaveCount(0)
    await expect(detailPage.actionAvailable('reject')).toHaveCount(0)
    await expect(detailPage.actionAvailable('revoke')).toHaveCount(0)
    await expect(subjectPage.getByText('This consent has been rejected.')).toBeVisible()
    await subjectPage.context().close()
  })

  test('04.10.04 - The named authoriser, not the subject, sees Approve and Reject while the consent is Pending', async ({
    browser,
    target,
    consentAdminConsentApi,
  }) => {
    const authoriser = target.personas.user2
    const { consentId } = await seedConsentViaApi(
      consentAdminConsentApi,
      target.personas.user.username,
      'PENDING',
      undefined,
      undefined,
      [{ userId: authoriser.username, type: 'PARENT' }],
    )

    const authoriserPersonaState = await getPersonaState(browser, 'user-2', authoriser)
    const authoriserPage = await pageForPersonaState(browser, authoriserPersonaState, authoriser)
    const detailPage = new ConsentDetailPage(authoriserPage, 'self')
    await detailPage.goto(consentId)
    await expect(detailPage.actionAvailable('approve')).toBeVisible()
    await expect(detailPage.actionAvailable('reject')).toBeVisible()
    await expect(detailPage.actionAvailable('revoke')).toHaveCount(0)
    await authoriserPage.context().close()
  })

  test('04.10.05 - The subject of a Revoked delegated consent sees only a revoked message, even though they were never a decision-maker', async ({
    browser,
    request,
    target,
    consentAdminConsentApi,
  }) => {
    const authoriser = target.personas.user2
    const { consentId } = await seedConsentViaApi(
      consentAdminConsentApi,
      target.personas.user.username,
      'PENDING',
      undefined,
      undefined,
      [{ userId: authoriser.username, type: 'PARENT' }],
    )

    const authoriserPersonaState = await getPersonaState(browser, 'user-2', authoriser)
    const authoriserConsentApi = new ConsentApiClient(
      request,
      authHeadersFromPersonaState(authoriserPersonaState),
      target.tenantDomain,
    )
    // Approve first, then revoke - only an Active consent can be revoked through the
    // self-service endpoint (see isRevokableByCurrentUser, consentAuthorization.ts). Anyone
    // with an authorizations entry can revoke it, not only the subject.
    expect((await authoriserConsentApi.authorizeMyConsent(consentId, 'APPROVED')).ok()).toBe(true)
    expect((await authoriserConsentApi.revokeMyConsent(consentId)).ok()).toBe(true)

    const subjectPage = await loginAsUser(browser)
    const detailPage = new ConsentDetailPage(subjectPage, 'self')
    await detailPage.goto(consentId)
    await expect(detailPage.actionAvailable('approve')).toHaveCount(0)
    await expect(detailPage.actionAvailable('reject')).toHaveCount(0)
    await expect(detailPage.actionAvailable('revoke')).toHaveCount(0)
    await expect(subjectPage.getByText('This consent has been revoked.')).toBeVisible()
    await subjectPage.context().close()
  })
})
