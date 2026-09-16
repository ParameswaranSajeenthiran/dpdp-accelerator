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

import {
  test,
  expect,
  getPersonaState,
  hasSecondUser,
  loginAsUser,
  loginAsConsentAdmin,
  pageForPersonaState,
} from '../../fixtures/auth.fixtures'
import { ConsentDetailPage } from '../../pages/ConsentDetailPage'
import { MyConsentPage } from '../../pages/MyConsentPage'
import { seedConsent } from '../../utils/consentSetup'

/**
 * A user's own consent detail page at /consents/:id: what it renders, the load-failed
 * path for an unknown id, and that a different user can't reach someone else's
 * consent by guessing its id. Also the registry list's own pagination cap - see
 * tests/04-consents/04.01-user-acting-on-consents.spec.ts for approve/reject/revoke.
 */
test.describe('User viewing Consents (UI)', () => {
  test('04.02.01 - The detail page renders subject, service, and purpose/element structure', async ({
    browser,
    target,
    consentAdminConsentApi,
    consentCleanupTracker,
  }) => {
    const userPage = await loginAsUser(browser)
    const consentAdminPage = await loginAsConsentAdmin(browser)
    const { consentId, purposeName, elementDisplayName, serviceId } = await seedConsent(
      consentAdminPage,
      consentAdminConsentApi,
      consentCleanupTracker,
      target.personas.user.username,
      'ACTIVE',
    )

    const detailPage = new ConsentDetailPage(userPage, 'self')
    await detailPage.goto(consentId)
    await expect(userPage.getByText(target.personas.user.username)).toBeVisible()
    await expect(userPage.getByText(serviceId)).toBeVisible()
    await expect(userPage.getByText('Not applicable')).toBeVisible()

    await detailPage.expandPurpose(purposeName)
    await expect(detailPage.elementRow(elementDisplayName)).toBeVisible()
    await userPage.context().close()
    await consentAdminPage.context().close()
  })

  test('04.02.02 - An unknown consent id shows the load-failed message with a way back to the registry', async ({
    browser,
  }) => {
    const userPage = await loginAsUser(browser)
    const detailPage = new ConsentDetailPage(userPage, 'self')
    await detailPage.goto('00000000-0000-0000-0000-000000000000')
    await expect(detailPage.loadFailedMessage).toBeVisible()
    await detailPage.backButton.click()
    await expect(userPage).toHaveURL(/\/consents$/)
    await userPage.context().close()
  })

  test("04.02.03 - A different user cannot open another user's consent by its URL", async ({
    browser,
    target,
    consentAdminConsentApi,
    consentCleanupTracker,
  }) => {
    test.skip(!hasSecondUser(), 'personas.user2 is not configured')
    // hasSecondUser() already confirmed this is set - the skip above guards it.
    const secondUser = target.personas.user2
    if (!secondUser) {
      throw new Error('Unreachable: hasSecondUser() already checked this above.')
    }

    const consentAdminPage = await loginAsConsentAdmin(browser)
    const { consentId } = await seedConsent(
      consentAdminPage,
      consentAdminConsentApi,
      consentCleanupTracker,
      target.personas.user.username,
      'ACTIVE',
    )

    // No always-on fixture exists for this persona (it's only ever needed here) - shares the
    // same file-based login cache as every other persona via getPersonaState, so this doesn't
    // log in again if any earlier test in the run already did.
    const secondPersonaState = await getPersonaState(browser, 'user-2', secondUser)
    const otherPage = await pageForPersonaState(browser, secondPersonaState, secondUser)
    const otherDetailPage = new ConsentDetailPage(otherPage, 'self')
    await otherDetailPage.goto(consentId)
    await expect(otherDetailPage.loadFailedMessage).toBeVisible()

    await otherPage.context().close()
    await consentAdminPage.context().close()
  })

  test('04.02.04 - The rows-per-page control caps the number of rendered rows at the selected size', async ({
    browser,
    target,
    consentAdminConsentApi,
    consentCleanupTracker,
  }) => {
    // Six sequential seedConsent calls, each its own real admin-UI round trip (Element, Purpose,
    // then the consent itself) - comfortably over the default 30s on a loaded or CPU-constrained
    // runner (confirmed timing out in CI, not locally). The assertions this test actually cares
    // about are cheap; only the setup is slow.
    test.setTimeout(60_000)
    const userPage = await loginAsUser(browser)
    const consentAdminPage = await loginAsConsentAdmin(browser)
    // One more than the smallest page size, so there's guaranteed to be a next page regardless
    // of how many consents this persona already has from earlier runs - consents accumulate
    // forever (AGENTS.md), so this is never seeding into a genuinely empty list.
    const seedCount = 6
    for (let i = 0; i < seedCount; i += 1) {
      await seedConsent(consentAdminPage, consentAdminConsentApi, consentCleanupTracker, target.personas.user.username, 'ACTIVE')
    }

    const listPage = new MyConsentPage(userPage)
    await listPage.goto()
    await listPage.setRowsPerPage(5)

    await expect(listPage.rows).toHaveCount(5)
    await expect(listPage.nextPageButton).toBeEnabled()
    await userPage.context().close()
    await consentAdminPage.context().close()
  })

  test('04.02.05 - A rejected consent shows Rejected and no further action on a fresh detail-page load', async ({
    browser,
    target,
    consentAdminConsentApi,
    consentCleanupTracker,
  }) => {
    const userPage = await loginAsUser(browser)
    const consentAdminPage = await loginAsConsentAdmin(browser)
    const { consentId } = await seedConsent(
      consentAdminPage,
      consentAdminConsentApi,
      consentCleanupTracker,
      target.personas.user.username,
      'PENDING',
    )

    const detailPage = new ConsentDetailPage(userPage, 'self')
    await detailPage.goto(consentId)
    await detailPage.openActionDialog('reject')
    await detailPage.confirmAction('reject')
    await expect(userPage.getByText('Rejected', { exact: true }).first()).toBeVisible()

    // A fresh navigation, not just the in-page state after confirming - proves the server
    // actually persisted the rejection, not just that the dialog's own optimistic update looked
    // right (see the identical rationale in 02.02.04/03.02.04's re-navigation checks).
    await detailPage.goto(consentId)
    await expect(userPage.getByText('Rejected', { exact: true }).first()).toBeVisible()
    // Rejected is not terminal for Approve specifically: isApprovableByCurrentUser (consentAuthorization.ts)
    // deliberately treats REJECTED the same as PENDING, so the subject can change their mind
    // later - confirmed against the actual source, not assumed. Reject and Revoke, however, both
    // require a state Rejected no longer is (isRejectableByCurrentUser excludes REJECTED;
    // isConsentRevokableState requires ACTIVE), so those two genuinely disappear.
    await expect(detailPage.actionAvailable('approve')).toHaveCount(1)
    await expect(detailPage.actionAvailable('reject')).toHaveCount(0)
    await expect(detailPage.actionAvailable('revoke')).toHaveCount(0)
    await userPage.context().close()
    await consentAdminPage.context().close()
  })
})
