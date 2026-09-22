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

import { test, expect, getPersonaState, loginAsUser } from '../../fixtures/auth.fixtures'
import { ConsentApiClient } from '../../clients/ConsentApiClient'
import { ConsentDetailPage } from '../../pages/ConsentDetailPage'
import { ConsentFullHistoryDialogPage } from '../../pages/ConsentFullHistoryDialogPage'
import { authHeadersFromPersonaState } from '../../utils/authStorage'
import { seedConsentViaApi } from '../../utils/consentSetup'

/**
 * A user's own consent history: the lifecycle timeline and full-history dialog on the self
 * detail page (/consents/:id). See 04.08-admin-viewing-consent-history.spec.ts for the admin
 * surface and cross-persona checks. `dpdp-consent-user` gets *_VIEW_SELF scopes by default, so
 * no extra setup is needed here.
 *
 * `seedConsentViaApi` always creates via the admin API, so every "Consent created by ..." entry below
 * is attributed to `target.personas.consentAdmin.username`, even in these self-service tests.
 *
 * `detailPage.goto(consentId)` is called again after each action that should appear in history:
 * the approve/reject/revoke mutations don't invalidate the history query keys, so the lifecycle
 * card and dialog would otherwise keep showing stale data - a real product gap, not a test quirk.
 */
test.describe('User viewing Consent History (UI)', () => {
  test('04.07.01 - Approving a Pending consent records CREATE then AUTHORIZE_APPROVE, oldest-first in the table and newest-first in the dialog', async ({
    browser,
    target,
    consentAdminConsentApi,
  }) => {
    const userPage = await loginAsUser(browser)
    const { consentId } = await seedConsentViaApi(
      consentAdminConsentApi,
      target.personas.user.username,
      'PENDING',
    )

    const detailPage = new ConsentDetailPage(userPage, 'self')
    await detailPage.goto(consentId)
    await detailPage.openActionDialog('approve')
    await detailPage.confirmAction('approve')
    await expect(userPage.getByText('Active', { exact: true }).first()).toBeVisible()

    // See the file-level comment above - the history queries need a fresh page load.
    await detailPage.goto(consentId)

    await expect(detailPage.lifecycleSection).toBeVisible()
    await expect(
      detailPage.lifecycleRow('Consent created', target.personas.consentAdmin.username),
    ).toBeVisible()
    await expect(detailPage.lifecycleRow('Approved', target.personas.user.username)).toBeVisible()

    // Oldest-first in the table: CREATE row precedes the AUTHORIZE_APPROVE row.
    const rowTexts = await detailPage.lifecycleRows.allTextContents()
    const createdIndex = rowTexts.findIndex((text) => text.includes('Consent created'))
    const approvedIndex = rowTexts.findIndex((text) => text.includes('Approved by'))
    expect(createdIndex).toBeGreaterThanOrEqual(0)
    expect(approvedIndex).toBeGreaterThan(createdIndex)

    await detailPage.openFullHistoryDialog()
    const dialog = new ConsentFullHistoryDialogPage(userPage)
    // The dialog's heading renders as soon as it opens, but its history is fetched lazily *on
    // open* - so gating on `dialog.dialog` (which is defined by that heading) proves nothing
    // about the entries. allTextContents() below does not retry, so without a gate on real
    // content it can snapshot an empty accordion list. Gate on the newest entry: it is the last
    // one the fetch can produce, so its presence means the list is fully rendered.
    await expect(dialog.entry('Approved', target.personas.user.username)).toBeVisible()

    // Newest-first in the dialog, reversed relative to the table above. Summary text uses "·",
    // not "by" - see ConsentFullHistoryDialogPage - so these checks drop "by".
    const summaryTexts = await dialog.entrySummaries.allTextContents()
    const dialogApprovedIndex = summaryTexts.findIndex((text) => text.includes('Approved'))
    const dialogCreatedIndex = summaryTexts.findIndex((text) => text.includes('Consent created'))
    expect(dialogApprovedIndex).toBeGreaterThanOrEqual(0)
    expect(dialogCreatedIndex).toBeGreaterThan(dialogApprovedIndex)

    await dialog.expand('Consent created', target.personas.consentAdmin.username)
    await expect(
      dialog.initialSnapshotChip('Consent created', target.personas.consentAdmin.username),
    ).toBeVisible()

    // A real diff against real server data: the subject's authorization moves to APPROVED.
    await dialog.expand('Approved', target.personas.user.username)
    await expect(dialog.changedTag('Approved', target.personas.user.username)).toBeVisible()

    await dialog.close()
    await userPage.context().close()
  })

  test('04.07.02 - Rejecting a Pending consent records AUTHORIZE_REJECT with a diffed authorization', async ({
    browser,
    target,
    consentAdminConsentApi,
  }) => {
    const userPage = await loginAsUser(browser)
    const { consentId } = await seedConsentViaApi(
      consentAdminConsentApi,
      target.personas.user.username,
      'PENDING',
    )

    const detailPage = new ConsentDetailPage(userPage, 'self')
    await detailPage.goto(consentId)
    await detailPage.openActionDialog('reject')
    await detailPage.confirmAction('reject')
    // .first(): the metadata card's state chip and the authorizations table's own state chip
    // both render the literal state text (see 04.03.02's identical comment).
    await expect(userPage.getByText('Rejected', { exact: true }).first()).toBeVisible()

    await detailPage.goto(consentId)

    await expect(detailPage.lifecycleRow('Rejected', target.personas.user.username)).toBeVisible()

    await detailPage.openFullHistoryDialog()
    const dialog = new ConsentFullHistoryDialogPage(userPage)
    await dialog.expand('Rejected', target.personas.user.username)
    await expect(dialog.changedTag('Rejected', target.personas.user.username)).toBeVisible()

    await dialog.close()
    await userPage.context().close()
  })

  test('04.07.03 - A full self-service lifecycle (created, approved, then revoked) is captured in order end to end', async ({
    browser,
    target,
    consentAdminConsentApi,
  }) => {
    const userPage = await loginAsUser(browser)
    const { consentId } = await seedConsentViaApi(
      consentAdminConsentApi,
      target.personas.user.username,
      'PENDING',
    )

    const detailPage = new ConsentDetailPage(userPage, 'self')
    await detailPage.goto(consentId)
    await detailPage.openActionDialog('approve')
    await detailPage.confirmAction('approve')
    await expect(userPage.getByText('Active', { exact: true }).first()).toBeVisible()

    // Chained without a reload - Revoke becomes available reactively once Active is reflected.
    await detailPage.openActionDialog('revoke')
    await detailPage.confirmAction('revoke')
    await expect(userPage.getByText('Revoked', { exact: true }).first()).toBeVisible()

    // See the file-level comment above - the history queries need a fresh page load.
    await detailPage.goto(consentId)

    // Wait on a visible element rather than reading text straight off goto() - every full page
    // load re-drives the SPA's silent sign-in redirect, which can otherwise hit a destroyed
    // execution context (see fixtures/auth.fixtures.ts).
    const revokedRow = detailPage.lifecycleRow('Revoked', target.personas.user.username)
    await expect(revokedRow).toBeVisible()
    // Exact text, not a loose substring match: a self-service revoke's own label used to read
    // "Revoked by reviewer" before being composed with "by <actor>", rendering the doubled
    // "Revoked by reviewer by <actor>" - a real product bug that `.includes('Revoked by')` below
    // would never have caught, since it's still a substring of the broken text too.
    await expect(detailPage.lifecycleDescription(revokedRow)).toHaveText(
      `Revoked by ${target.personas.user.username}`,
    )

    const rowTexts = await detailPage.lifecycleRows.allTextContents()
    const createdIndex = rowTexts.findIndex((text) => text.includes('Consent created'))
    const approvedIndex = rowTexts.findIndex((text) => text.includes('Approved by'))
    const revokedIndex = rowTexts.findIndex((text) => text.includes('Revoked by'))
    expect(createdIndex).toBeGreaterThanOrEqual(0)
    expect(approvedIndex).toBeGreaterThan(createdIndex)
    expect(revokedIndex).toBeGreaterThan(approvedIndex)

    await detailPage.openFullHistoryDialog()
    const dialog = new ConsentFullHistoryDialogPage(userPage)
    // The dialog's heading renders as soon as it opens, but its history is fetched lazily *on
    // open* - so gating on `dialog.dialog` (which is defined by that heading) proves nothing
    // about the entries. allTextContents() below does not retry, so without a gate on real
    // content it can snapshot an empty accordion list. Gate on the newest entry: it is the last
    // one the fetch can produce, so its presence means the list is fully rendered.
    await expect(dialog.entry('Revoked', target.personas.user.username)).toBeVisible()
    const summaryTexts = await dialog.entrySummaries.allTextContents()
    const dialogCreatedIndex = summaryTexts.findIndex((text) => text.includes('Consent created'))
    const dialogApprovedIndex = summaryTexts.findIndex((text) => text.includes('Approved'))
    const dialogRevokedIndex = summaryTexts.findIndex((text) => text.includes('Revoked'))
    // Newest-first: REVOKE, then APPROVE, then CREATE.
    expect(dialogRevokedIndex).toBeGreaterThanOrEqual(0)
    expect(dialogApprovedIndex).toBeGreaterThan(dialogRevokedIndex)
    expect(dialogCreatedIndex).toBeGreaterThan(dialogApprovedIndex)

    // Only proves the diff rendered a real result - see diffRendered's own comment.
    await dialog.expand('Revoked', target.personas.user.username)
    await expect(dialog.diffRendered('Revoked', target.personas.user.username)).toBeVisible()

    await dialog.close()
    await userPage.context().close()
  })

  test('04.07.04 - A delegated consent (parent approving on behalf of a child) attributes the approval to the parent, not the subject', async ({
    browser,
    request,
    target,
    consentAdminConsentApi,
  }) => {
    // No dedicated "parent"/"child" persona exists - the second, generic user account stands in
    // for the parent, and target.personas.user (this file's usual subject) stands in for the child.
    const parent = target.personas.user2

    // authorizations lists only the parent, never the child - carbon-consent-mgt-core's model has
    // no separate "subject" field on an authorization; delegation is expressed purely by the
    // subjectId (child) and authorizations[].userId (parent) not matching.
    const { consentId } = await seedConsentViaApi(
      consentAdminConsentApi,
      target.personas.user.username,
      'PENDING',
      undefined,
      undefined,
      [{ userId: parent.username, type: 'PARENT' }],
    )

    // The parent approves on the child's behalf through the same self-service endpoint a subject
    // would use - IS's own consent-mgt resolves authorization by matching the caller against the
    // receipt's authorizations list, not by requiring caller === subject, which is exactly what
    // makes a delegated/guardian approval possible at all. Called directly via the API (not the
    // UI) since this test's point is the resulting history attribution, not the approve form.
    const parentPersonaState = await getPersonaState(browser, 'user-2', parent)
    const parentConsentApi = new ConsentApiClient(
      request,
      authHeadersFromPersonaState(parentPersonaState),
      target.tenantDomain,
    )
    const authorizeResponse = await parentConsentApi.authorizeMyConsent(consentId, 'APPROVED')
    expect(authorizeResponse.ok()).toBe(true)

    // The child - the consent's subject - sees their own consent's history with the approval
    // attributed to the PARENT, not to themselves: actionBy records who actually performed the
    // action (DPDPConsentHistoryListener.getActionBy(), the caller's own PrivilegedCarbonContext
    // username), which is a different thing from whose consent this is.
    const childPage = await loginAsUser(browser)
    const detailPage = new ConsentDetailPage(childPage, 'self')
    await detailPage.goto(consentId)
    // The page's own metadata card renders the subject as plain text (see 04.01.01) - confirms
    // the child, not the parent, is who this consent is about.
    await expect(childPage.getByText(target.personas.user.username)).toBeVisible()
    await expect(detailPage.lifecycleRow('Approved', parent.username)).toBeVisible()
    await expect(detailPage.lifecycleRow('Approved', target.personas.user.username)).toHaveCount(0)

    await detailPage.openFullHistoryDialog()
    const dialog = new ConsentFullHistoryDialogPage(childPage)
    await dialog.expand('Approved', parent.username)
    await expect(dialog.changedTag('Approved', parent.username)).toBeVisible()
    await dialog.close()

    await childPage.context().close()
  })
})
