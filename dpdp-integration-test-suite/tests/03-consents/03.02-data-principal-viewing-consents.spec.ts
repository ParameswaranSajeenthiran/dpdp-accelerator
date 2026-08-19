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
  hasSecondDataPrincipal,
  loginAsDataPrincipal,
  loginAsConsentAdmin,
} from '../../fixtures/auth.fixtures'
import { ConsentDetailPage } from '../../pages/ConsentDetailPage'
import { env } from '../../utils/env'
import { seedConsent } from '../../utils/consentSetup'

/**
 * A Data Principal's own consent detail page at /consents/:id: what it renders, the load-failed
 * path for an unknown id, and that a different Data Principal can't reach someone else's
 * consent by guessing its id. See
 * tests/02-consents/02.01-data-principal-acting-on-consents.spec.ts for approve/reject/revoke.
 */
test.describe('Data Principal viewing Consents (UI)', () => {
  test('02.02.01 - The detail page renders subject, service, and purpose/element structure', async ({
    browser,
    consentAdminConsentApi,
    consentCleanupTracker,
  }) => {
    const dataPrincipalPage = await loginAsDataPrincipal(browser)
    const consentAdminPage = await loginAsConsentAdmin(browser)
    const { consentId, purposeName, elementDisplayName, serviceId } = await seedConsent(
      consentAdminPage,
      consentAdminConsentApi,
      consentCleanupTracker,
      env.dataPrincipal.username,
      'ACTIVE',
    )

    const detailPage = new ConsentDetailPage(dataPrincipalPage, 'self')
    await detailPage.goto(consentId)
    await expect(dataPrincipalPage.getByText(env.dataPrincipal.username)).toBeVisible()
    await expect(dataPrincipalPage.getByText(serviceId)).toBeVisible()
    await expect(dataPrincipalPage.getByText('Not applicable')).toBeVisible()

    await detailPage.expandPurpose(purposeName)
    await expect(detailPage.elementRow(elementDisplayName)).toBeVisible()
    await dataPrincipalPage.context().close()
    await consentAdminPage.context().close()
  })

  test('02.02.02 - An unknown consent id shows the load-failed message with a way back to the registry', async ({
    browser,
  }) => {
    const dataPrincipalPage = await loginAsDataPrincipal(browser)
    const detailPage = new ConsentDetailPage(dataPrincipalPage, 'self')
    await detailPage.goto('00000000-0000-0000-0000-000000000000')
    await expect(detailPage.loadFailedMessage).toBeVisible()
    await detailPage.backButton.click()
    await expect(dataPrincipalPage).toHaveURL(/\/consents$/)
    await dataPrincipalPage.context().close()
  })

  test("02.02.03 - A different Data Principal cannot open another user's consent by its URL", async ({
    browser,
    consentAdminConsentApi,
    consentCleanupTracker,
  }) => {
    test.skip(!hasSecondDataPrincipal(), 'TEST_DATA_PRINCIPAL_2_USERNAME/PASSWORD is not configured')
    // hasSecondDataPrincipal() already confirmed this is set - the skip above guards it.
    const secondDataPrincipal = env.secondDataPrincipal()
    if (!secondDataPrincipal) {
      throw new Error('Unreachable: hasSecondDataPrincipal() already checked this above.')
    }

    const consentAdminPage = await loginAsConsentAdmin(browser)
    const { consentId } = await seedConsent(
      consentAdminPage,
      consentAdminConsentApi,
      consentCleanupTracker,
      env.dataPrincipal.username,
      'ACTIVE',
    )

    // No always-on fixture exists for this persona (it's only ever needed here) - shares the
    // same file-based login cache as every other persona via getPersonaState, so this doesn't
    // log in again if any earlier test in the run already did.
    const secondPersonaState = await getPersonaState(browser, 'data-principal-2', secondDataPrincipal)
    const otherContext = await browser.newContext({
      storageState: secondPersonaState,
      baseURL: env.portalNavigationBaseUrl,
      ignoreHTTPSErrors: env.ignoreHttpsErrors,
    })
    const otherPage = await otherContext.newPage()
    const otherDetailPage = new ConsentDetailPage(otherPage, 'self')
    await otherDetailPage.goto(consentId)
    await expect(otherDetailPage.loadFailedMessage).toBeVisible()

    await otherContext.close()
    await consentAdminPage.context().close()
  })
})
