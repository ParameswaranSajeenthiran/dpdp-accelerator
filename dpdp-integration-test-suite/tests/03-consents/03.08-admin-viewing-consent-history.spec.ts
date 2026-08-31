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

import { test, expect, loginAsUser, loginAsConsentAdmin } from '../../fixtures/auth.fixtures'
import { ConsentDetailPage } from '../../pages/ConsentDetailPage'
import { ConsentFullHistoryDialogPage } from '../../pages/ConsentFullHistoryDialogPage'
import { env } from '../../utils/env'
import { seedConsent } from '../../utils/consentSetup'

/**
 * The admin surface (/administration/consents/:id) of the same two components covered in
 * tests/03-consents/03.07-user-viewing-consent-history.spec.ts - see that file's comment for the
 * shared "why goto() twice" note (useRevokeConsentMutation doesn't invalidate the history query
 * keys, so a fresh page load is needed after any action that should show up in history) and for
 * why every seeded consent's CREATE entry is attributed to the admin persona regardless of who
 * the consent's subject is. `dpdp-consent-admin`'s role permission set is a superset that
 * includes both the ANY and SELF variants of every consent-history scope
 * (DPDPIdentityExtensionTenantMgtListener authorizes the whole API resource, not scope-by-scope),
 * so the admin persona needs no extra setup either.
 *
 * Beyond the admin acting on its own seeded consent, this file also proves the ANY-scoped
 * endpoints genuinely return a *different* user's own history, not just the admin's own actions -
 * something no unit test (mocked API) or the self-viewing spec (single persona) can demonstrate.
 */
test.describe('Admin viewing Consent History (UI)', () => {
  test('02.08.01 - Revoking an Active consent as admin attributes CREATE and REVOKE to the admin, showing only the state transition in the diff', async ({
    browser,
    consentAdminConsentApi,
    consentCleanupTracker,
  }) => {
    const consentAdminPage = await loginAsConsentAdmin(browser)
    const { consentId } = await seedConsent(
      consentAdminPage,
      consentAdminConsentApi,
      consentCleanupTracker,
      env.user.username,
      'ACTIVE',
    )

    const detailPage = new ConsentDetailPage(consentAdminPage, 'admin')
    await detailPage.goto(consentId)
    await detailPage.openActionDialog('revoke')
    await detailPage.confirmAction('revoke')
    await expect(consentAdminPage.getByText('Revoked', { exact: true }).first()).toBeVisible()

    // See the file-level comment above - the history queries need a fresh page load.
    await detailPage.goto(consentId)

    await expect(
      detailPage.lifecycleRow('Consent created', env.consentAdmin.username),
    ).toBeVisible()
    await expect(detailPage.lifecycleRow('Revoked', env.consentAdmin.username)).toBeVisible()

    const rowTexts = await detailPage.lifecycleRows.allTextContents()
    const createdIndex = rowTexts.findIndex((text) => text.includes('Consent created'))
    const revokedIndex = rowTexts.findIndex((text) => text.includes('Revoked by'))
    expect(revokedIndex).toBeGreaterThan(createdIndex)

    await detailPage.openFullHistoryDialog()
    const dialog = new ConsentFullHistoryDialogPage(consentAdminPage)
    await expect(dialog.dialog).toBeVisible()

    // Revoking never touched expiryTime/properties/authorizations - this consent had none of the
    // last to begin with (created with an explicit ACTIVE state, not via authorizations) - but
    // `state` itself is diffed too (see consentSnapshotDiff.ts), and it did change (Active ->
    // Revoked), so the diff renders that transition rather than the noChangesNote fallback.
    // Unlike 02.07.03's revoke-after-approve case, there is no ambiguity here: no authorizer
    // record ever existed to cascade.
    await dialog.expand('Revoked', env.consentAdmin.username)
    await expect(
      dialog.stateTransition('Revoked', env.consentAdmin.username, 'Active', 'Revoked'),
    ).toBeVisible()

    await dialog.close()
    await consentAdminPage.context().close()
  })

  test("02.08.02 - The admin surface shows the data principal's own approval, not just admin-authored history", async ({
    browser,
    consentAdminConsentApi,
    consentCleanupTracker,
  }) => {
    const userPage = await loginAsUser(browser)
    const consentAdminPage = await loginAsConsentAdmin(browser)
    const { consentId } = await seedConsent(
      consentAdminPage,
      consentAdminConsentApi,
      consentCleanupTracker,
      env.user.username,
      'PENDING',
    )

    // The data principal, not the admin, performs the approval - on the self surface.
    const selfDetailPage = new ConsentDetailPage(userPage, 'self')
    await selfDetailPage.goto(consentId)
    await selfDetailPage.openActionDialog('approve')
    await selfDetailPage.confirmAction('approve')
    await expect(userPage.getByText('Active', { exact: true })).toBeVisible()

    // The admin's first-ever load of this consent's detail page happens after the approval
    // above already landed server-side, so this single navigation - unlike the "goto() twice"
    // cases elsewhere in this file - already reflects fresh data; there was never a stale
    // history query to invalidate on this page instance in the first place.
    const adminDetailPage = new ConsentDetailPage(consentAdminPage, 'admin')
    await adminDetailPage.goto(consentId)

    await expect(
      adminDetailPage.lifecycleRow('Consent created', env.consentAdmin.username),
    ).toBeVisible()
    await expect(adminDetailPage.lifecycleRow('Approved', env.user.username)).toBeVisible()

    await adminDetailPage.openFullHistoryDialog()
    const dialog = new ConsentFullHistoryDialogPage(consentAdminPage)
    await expect(dialog.entry('Approved', env.user.username)).toBeVisible()

    await dialog.expand('Approved', env.user.username)
    await expect(dialog.changedTag('Approved', env.user.username)).toBeVisible()

    await dialog.close()
    await userPage.context().close()
    await consentAdminPage.context().close()
  })

  test('02.08.03 - A full multi-actor lifecycle (admin creates, the data principal approves, admin revokes) is captured in order with each actor attributed correctly', async ({
    browser,
    consentAdminConsentApi,
    consentCleanupTracker,
  }) => {
    const userPage = await loginAsUser(browser)
    const consentAdminPage = await loginAsConsentAdmin(browser)
    const { consentId } = await seedConsent(
      consentAdminPage,
      consentAdminConsentApi,
      consentCleanupTracker,
      env.user.username,
      'PENDING',
    )

    const selfDetailPage = new ConsentDetailPage(userPage, 'self')
    await selfDetailPage.goto(consentId)
    await selfDetailPage.openActionDialog('approve')
    await selfDetailPage.confirmAction('approve')
    await expect(userPage.getByText('Active', { exact: true })).toBeVisible()

    const adminDetailPage = new ConsentDetailPage(consentAdminPage, 'admin')
    await adminDetailPage.goto(consentId)
    await adminDetailPage.openActionDialog('revoke')
    await adminDetailPage.confirmAction('revoke')
    await expect(consentAdminPage.getByText('Revoked', { exact: true }).first()).toBeVisible()

    // The revoke mutation above doesn't invalidate the history queries - see the file-level
    // comment. This is the admin page's second navigation to this URL, specifically to pick up
    // the REVOKE entry it just produced.
    await adminDetailPage.goto(consentId)

    // Waiting on a visible element (rather than reading text straight off goto()) absorbs this
    // app's post-navigation OAuth redirect settling - every full page load re-drives the SPA's
    // silent sign-in redirect (see fixtures/auth.fixtures.ts's own comments on this), and reading
    // .allTextContents() immediately after goto() can hit a destroyed execution context mid-redirect.
    await expect(
      adminDetailPage.lifecycleRow('Revoked', env.consentAdmin.username),
    ).toBeVisible()

    const rowTexts = await adminDetailPage.lifecycleRows.allTextContents()
    const createdIndex = rowTexts.findIndex(
      (text) => text.includes('Consent created') && text.includes(env.consentAdmin.username),
    )
    const approvedIndex = rowTexts.findIndex(
      (text) => text.includes('Approved by') && text.includes(env.user.username),
    )
    const revokedIndex = rowTexts.findIndex(
      (text) => text.includes('Revoked by') && text.includes(env.consentAdmin.username),
    )
    expect(createdIndex).toBeGreaterThanOrEqual(0)
    expect(approvedIndex).toBeGreaterThan(createdIndex)
    expect(revokedIndex).toBeGreaterThan(approvedIndex)

    await adminDetailPage.openFullHistoryDialog()
    const dialog = new ConsentFullHistoryDialogPage(consentAdminPage)
    await expect(dialog.dialog).toBeVisible()
    // The dialog's own summary text is "<action> · <actor>", not "by" - see
    // ConsentFullHistoryDialogPage's comment - so these substring checks drop "by".
    const summaryTexts = await dialog.entrySummaries.allTextContents()
    const dialogCreatedIndex = summaryTexts.findIndex(
      (text) => text.includes('Consent created') && text.includes(env.consentAdmin.username),
    )
    const dialogApprovedIndex = summaryTexts.findIndex(
      (text) => text.includes('Approved') && text.includes(env.user.username),
    )
    const dialogRevokedIndex = summaryTexts.findIndex(
      (text) => text.includes('Revoked') && text.includes(env.consentAdmin.username),
    )
    // Newest-first: REVOKE (admin), then APPROVE (user), then CREATE (admin).
    expect(dialogRevokedIndex).toBeGreaterThanOrEqual(0)
    expect(dialogApprovedIndex).toBeGreaterThan(dialogRevokedIndex)
    expect(dialogCreatedIndex).toBeGreaterThan(dialogApprovedIndex)

    await dialog.close()
    await userPage.context().close()
    await consentAdminPage.context().close()
  })
})
