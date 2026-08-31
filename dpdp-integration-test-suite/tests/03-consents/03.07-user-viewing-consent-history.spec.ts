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
 * A user's own consent history: ConsentLifecycleSection (status-audit timeline) and
 * ConsentFullHistoryDialog (the per-snapshot diff view) on the self detail page
 * (/consents/:id). See tests/03-consents/03.08-admin-viewing-consent-history.spec.ts for the
 * admin surface (/administration/consents/:id) and the cross-persona scope-boundary tests.
 * `dpdp-consent-user` is provisioned with STATUS_HISTORY_VIEW_SELF/HISTORY_VIEW_SELF
 * unconditionally (DPDPIdentityExtensionTenantMgtListener), so the default user persona needs no
 * extra setup to see either surface.
 *
 * `seedConsent` always creates the consent via the *admin* API (see utils/consentSetup.ts - it's
 * the one step with no create UI), regardless of who the consent's subject is - so every "Consent
 * created by ..." entry below is attributed to `env.consentAdmin.username`, never
 * `env.user.username`, even on the self-service tests in this file. Confirmed live: asserting
 * the subject's own name here finds nothing.
 *
 * `detailPage.goto(consentId)` is called again after every UI action that should show up in
 * history. This is deliberate, not caution: useApproveConsentMutation/useRejectConsentMutation/
 * useRevokeConsentMutation only invalidate the `['consent', id]` / `['consents']` query keys,
 * never `['consent-status-history', id]` or `['consent-full-history', id]` - so the lifecycle
 * card and full-history dialog would otherwise keep showing pre-action data until a fresh page
 * load. A real user watching the page live would see the same staleness; that's a product gap,
 * not something to work around silently here.
 */
test.describe('User viewing Consent History (UI)', () => {
  test('02.07.01 - Approving a Pending consent records CREATE then AUTHORIZE_APPROVE, oldest-first in the table and newest-first in the dialog', async ({
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

    const detailPage = new ConsentDetailPage(userPage, 'self')
    await detailPage.goto(consentId)
    await detailPage.openActionDialog('approve')
    await detailPage.confirmAction('approve')
    await expect(userPage.getByText('Active', { exact: true })).toBeVisible()

    // See the file-level comment above - the history queries need a fresh page load.
    await detailPage.goto(consentId)

    await expect(detailPage.lifecycleSection).toBeVisible()
    await expect(
      detailPage.lifecycleRow('Consent created', env.consentAdmin.username),
    ).toBeVisible()
    await expect(detailPage.lifecycleRow('Approved', env.user.username)).toBeVisible()

    // Oldest-first in the table: CREATE row precedes the AUTHORIZE_APPROVE row.
    const rowTexts = await detailPage.lifecycleRows.allTextContents()
    const createdIndex = rowTexts.findIndex((text) => text.includes('Consent created'))
    const approvedIndex = rowTexts.findIndex((text) => text.includes('Approved by'))
    expect(createdIndex).toBeGreaterThanOrEqual(0)
    expect(approvedIndex).toBeGreaterThan(createdIndex)

    await detailPage.openFullHistoryDialog()
    const dialog = new ConsentFullHistoryDialogPage(userPage)
    await expect(dialog.dialog).toBeVisible()

    // Newest-first in the dialog: the same two entries, reversed relative to the table above.
    // The dialog's own summary text is "<action> · <actor>", not "by" - see
    // ConsentFullHistoryDialogPage's comment - so these substring checks drop "by".
    const summaryTexts = await dialog.entrySummaries.allTextContents()
    const dialogApprovedIndex = summaryTexts.findIndex((text) => text.includes('Approved'))
    const dialogCreatedIndex = summaryTexts.findIndex((text) => text.includes('Consent created'))
    expect(dialogApprovedIndex).toBeGreaterThanOrEqual(0)
    expect(dialogCreatedIndex).toBeGreaterThan(dialogApprovedIndex)

    await dialog.expand('Consent created', env.consentAdmin.username)
    await expect(
      dialog.initialSnapshotChip('Consent created', env.consentAdmin.username),
    ).toBeVisible()

    // The subject's own authorization moves from its pre-approval status to APPROVED - a real
    // diff against real server data, not a fixture.
    await dialog.expand('Approved', env.user.username)
    await expect(dialog.changedTag('Approved', env.user.username)).toBeVisible()

    await dialog.close()
    await userPage.context().close()
    await consentAdminPage.context().close()
  })

  test('02.07.02 - Rejecting a Pending consent records AUTHORIZE_REJECT with a diffed authorization', async ({
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

    const detailPage = new ConsentDetailPage(userPage, 'self')
    await detailPage.goto(consentId)
    await detailPage.openActionDialog('reject')
    await detailPage.confirmAction('reject')
    // .first(): the metadata card's state chip and the authorizations table's own state chip
    // both render the literal state text (see 02.01.02's identical comment).
    await expect(userPage.getByText('Rejected', { exact: true }).first()).toBeVisible()

    await detailPage.goto(consentId)

    await expect(detailPage.lifecycleRow('Rejected', env.user.username)).toBeVisible()

    await detailPage.openFullHistoryDialog()
    const dialog = new ConsentFullHistoryDialogPage(userPage)
    await dialog.expand('Rejected', env.user.username)
    await expect(dialog.changedTag('Rejected', env.user.username)).toBeVisible()

    await dialog.close()
    await userPage.context().close()
    await consentAdminPage.context().close()
  })

  test('02.07.03 - A full self-service lifecycle (created, approved, then revoked) is captured in order end to end', async ({
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

    const detailPage = new ConsentDetailPage(userPage, 'self')
    await detailPage.goto(consentId)
    await detailPage.openActionDialog('approve')
    await detailPage.confirmAction('approve')
    await expect(userPage.getByText('Active', { exact: true })).toBeVisible()

    // Chained on the same loaded page, no intermediate reload - the Revoke action becomes
    // available reactively once the Active state above is reflected (see 02.01.03/04's identical
    // pattern of chaining UI actions without a navigation between them).
    await detailPage.openActionDialog('revoke')
    await detailPage.confirmAction('revoke')
    await expect(userPage.getByText('Revoked', { exact: true }).first()).toBeVisible()

    // See the file-level comment above - the history queries need a fresh page load.
    await detailPage.goto(consentId)

    // Waiting on a visible element (rather than reading text straight off goto()) absorbs this
    // app's post-navigation OAuth redirect settling - every full page load re-drives the SPA's
    // silent sign-in redirect (see fixtures/auth.fixtures.ts's own comments on this), and reading
    // .allTextContents() immediately after goto() can hit a destroyed execution context mid-redirect.
    await expect(detailPage.lifecycleRow('Revoked', env.user.username)).toBeVisible()

    const rowTexts = await detailPage.lifecycleRows.allTextContents()
    const createdIndex = rowTexts.findIndex((text) => text.includes('Consent created'))
    const approvedIndex = rowTexts.findIndex((text) => text.includes('Approved by'))
    const revokedIndex = rowTexts.findIndex((text) => text.includes('Revoked by'))
    expect(createdIndex).toBeGreaterThanOrEqual(0)
    expect(approvedIndex).toBeGreaterThan(createdIndex)
    expect(revokedIndex).toBeGreaterThan(approvedIndex)

    await detailPage.openFullHistoryDialog()
    const dialog = new ConsentFullHistoryDialogPage(userPage)
    await expect(dialog.dialog).toBeVisible()
    const summaryTexts = await dialog.entrySummaries.allTextContents()
    const dialogCreatedIndex = summaryTexts.findIndex((text) => text.includes('Consent created'))
    const dialogApprovedIndex = summaryTexts.findIndex((text) => text.includes('Approved'))
    const dialogRevokedIndex = summaryTexts.findIndex((text) => text.includes('Revoked'))
    // Newest-first: REVOKE, then APPROVE, then CREATE.
    expect(dialogRevokedIndex).toBeGreaterThanOrEqual(0)
    expect(dialogApprovedIndex).toBeGreaterThan(dialogRevokedIndex)
    expect(dialogCreatedIndex).toBeGreaterThan(dialogApprovedIndex)

    // The REVOKE step's diff outcome (whether the authorization itself is also touched) isn't
    // decidable from this repo - see ConsentFullHistoryDialogPage.diffRendered's own comment.
    // This only proves the diff rendered a real result, not the needsMoreHistory fallback.
    await dialog.expand('Revoked', env.user.username)
    await expect(dialog.diffRendered('Revoked', env.user.username)).toBeVisible()

    await dialog.close()
    await userPage.context().close()
    await consentAdminPage.context().close()
  })
})
