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

import { test, expect, hasSecondDataPrincipal } from '../../fixtures/auth.fixtures'
import { ConsentDetailPage } from '../../pages/ConsentDetailPage'
import { ConsentRegistryPage } from '../../pages/ConsentRegistryPage'
import { authStateFile, env } from '../../utils/env'
import { seedConsent } from '../../utils/consentSetup'
import { uniqueServiceId } from '../../utils/testData'

/**
 * Only Consent creation goes through the admin API (see utils/consentSetup.ts - it has no create
 * UI at all); the Element and Purpose each seeded consent needs are created through the real
 * admin "Add Element"/"Add Purpose" forms. This file itself only drives the Data Principal's own
 * read/approve/reject/revoke UI at /consents, /consents/:id. `internal_login` alone (granted to
 * every authenticated user) carries portal:consents:{read,write}:self, so the existing
 * data-principal persona needs no extra role for any of this.
 */
test.describe('Consent self-service registry (UI)', () => {
  test.describe('Happy paths', () => {
    test('02.01.01 - Approving a Pending consent from the list moves it to Active', async ({
      dataPrincipalPage,
      consentAdminPage,
      consentAdminConsentApi,
      consentCleanupTracker,
    }) => {
      const { consentId, serviceId } = await seedConsent(
        consentAdminPage,
        consentAdminConsentApi,
        consentCleanupTracker,
        env.dataPrincipal.username,
        'PENDING',
      )

      const registryPage = new ConsentRegistryPage(dataPrincipalPage)
      await registryPage.goto()
      // Filtered to this test's own unique service id - see the identical comment on the revoke
      // test above.
      await registryPage.searchByService(serviceId)
      await expect(registryPage.rowByConsentId(consentId)).toContainText('Pending')

      await registryPage.approveFromList(consentId)
      await dataPrincipalPage.getByRole('button', { name: 'Approve Consent' }).click()

      await expect(registryPage.rowByConsentId(consentId)).toContainText('Active')
    })

    test('02.01.02 - Rejecting a Pending consent from its detail page moves it to Rejected', async ({
      dataPrincipalPage,
      consentAdminPage,
      consentAdminConsentApi,
      consentCleanupTracker,
    }) => {
      const { consentId } = await seedConsent(
        consentAdminPage,
        consentAdminConsentApi,
        consentCleanupTracker,
        env.dataPrincipal.username,
        'PENDING',
      )

      const detailPage = new ConsentDetailPage(dataPrincipalPage, 'self')
      await detailPage.goto(consentId)
      await detailPage.openActionDialog('reject')
      await expect(detailPage.dialogTitle('reject')).toBeVisible()
      await detailPage.confirmAction('reject')

      // .first(): the metadata card's state chip and the authorizations table's own state chip
      // both render the literal state text once the sole authorizer (this same Data Principal)
      // is also moved to Rejected.
      await expect(dataPrincipalPage.getByText('Rejected', { exact: true }).first()).toBeVisible()
    })

    test('02.01.03 - Revoking an Active consent from the list moves it to Revoked and removes the revoke action', async ({
      dataPrincipalPage,
      consentAdminPage,
      consentAdminConsentApi,
      consentCleanupTracker,
    }) => {
      const { consentId, serviceId } = await seedConsent(
        consentAdminPage,
        consentAdminConsentApi,
        consentCleanupTracker,
        env.dataPrincipal.username,
        'ACTIVE',
      )

      const registryPage = new ConsentRegistryPage(dataPrincipalPage)
      await registryPage.goto()
      // Filtered to this test's own unique service id: the unfiltered list is sorted and paged,
      // and a persistent environment can easily push a freshly created row off the first page.
      await registryPage.searchByService(serviceId)
      await registryPage.revokeFromList(consentId)
      await dataPrincipalPage.getByRole('button', { name: 'Revoke Consent' }).click()

      await expect(registryPage.rowByConsentId(consentId)).toContainText('Revoked')
      await expect(
        registryPage.rowByConsentId(consentId).getByRole('button', { name: 'Revoke' }),
      ).toHaveCount(0)
    })

    test('02.01.04 - Approving from the detail page works the same way as from the list', async ({
      dataPrincipalPage,
      consentAdminPage,
      consentAdminConsentApi,
      consentCleanupTracker,
    }) => {
      const { consentId } = await seedConsent(
        consentAdminPage,
        consentAdminConsentApi,
        consentCleanupTracker,
        env.dataPrincipal.username,
        'PENDING',
      )

      const detailPage = new ConsentDetailPage(dataPrincipalPage, 'self')
      await detailPage.goto(consentId)
      await detailPage.openActionDialog('approve')
      await detailPage.confirmAction('approve')

      await expect(dataPrincipalPage.getByText('Active', { exact: true })).toBeVisible()
    })

    test('02.01.05 - The detail page renders subject, service, and purpose/element structure', async ({
      dataPrincipalPage,
      consentAdminPage,
      consentAdminConsentApi,
      consentCleanupTracker,
    }) => {
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
    })

    test('02.01.06 - The state filter narrows the list to only the selected state', async ({
      dataPrincipalPage,
      consentAdminPage,
      consentAdminConsentApi,
      consentCleanupTracker,
    }) => {
      // Both seeded under the same unique service id and narrowed to it first, so the state
      // filter's effect is checked within a controlled two-row set instead of the full,
      // ever-growing unfiltered list.
      const serviceId = uniqueServiceId()
      const pending = await seedConsent(
        consentAdminPage,
        consentAdminConsentApi,
        consentCleanupTracker,
        env.dataPrincipal.username,
        'PENDING',
        serviceId,
      )
      const active = await seedConsent(
        consentAdminPage,
        consentAdminConsentApi,
        consentCleanupTracker,
        env.dataPrincipal.username,
        'ACTIVE',
        serviceId,
      )

      const registryPage = new ConsentRegistryPage(dataPrincipalPage)
      await registryPage.goto()
      await registryPage.searchByService(serviceId)
      await registryPage.filterByState('Pending')

      await expect(registryPage.rowByConsentId(pending.consentId)).toBeVisible()
      await expect(registryPage.rowByConsentId(active.consentId)).toHaveCount(0)

      await registryPage.clearFilters()
      await expect(registryPage.serviceSearch).toHaveValue('')
    })

    test('02.01.07 - Searching by the exact service id finds the matching consent', async ({
      dataPrincipalPage,
      consentAdminPage,
      consentAdminConsentApi,
      consentCleanupTracker,
    }) => {
      const serviceId = uniqueServiceId()
      const { consentId } = await seedConsent(
        consentAdminPage,
        consentAdminConsentApi,
        consentCleanupTracker,
        env.dataPrincipal.username,
        'ACTIVE',
        serviceId,
      )

      const registryPage = new ConsentRegistryPage(dataPrincipalPage)
      await registryPage.goto()
      await registryPage.searchByService(serviceId)
      await expect(registryPage.rowByConsentId(consentId)).toBeVisible()
    })
  })

  test.describe('Validation violations', () => {
    test('02.01.08 - An unknown consent id shows the load-failed message with a way back to the registry', async ({
      dataPrincipalPage,
    }) => {
      const detailPage = new ConsentDetailPage(dataPrincipalPage, 'self')
      await detailPage.goto('00000000-0000-0000-0000-000000000000')
      await expect(detailPage.loadFailedMessage).toBeVisible()
      await detailPage.backButton.click()
      await expect(dataPrincipalPage).toHaveURL(/\/consents$/)
    })

    test('02.01.09 - A Rejected consent offers no approve, reject, or revoke action on its detail page', async ({
      dataPrincipalPage,
      consentAdminPage,
      consentAdminConsentApi,
      consentCleanupTracker,
    }) => {
      const { consentId } = await seedConsent(
        consentAdminPage,
        consentAdminConsentApi,
        consentCleanupTracker,
        env.dataPrincipal.username,
        'REJECTED',
      )

      const detailPage = new ConsentDetailPage(dataPrincipalPage, 'self')
      await detailPage.goto(consentId)
      await expect(dataPrincipalPage.getByRole('button', { name: 'Approve', exact: true })).toHaveCount(0)
      await expect(dataPrincipalPage.getByRole('button', { name: 'Reject', exact: true })).toHaveCount(0)
      await expect(dataPrincipalPage.getByRole('button', { name: 'Revoke', exact: true })).toHaveCount(0)
    })

    test('02.01.10 - A service filter matching nothing shows the empty-results message', async ({
      dataPrincipalPage,
    }) => {
      const registryPage = new ConsentRegistryPage(dataPrincipalPage)
      await registryPage.goto()
      await registryPage.searchByService(`no-such-service-${Date.now().toString()}`)
      await expect(registryPage.emptyStateMessage).toBeVisible()
    })

    test("02.01.11 - A different Data Principal cannot open another user's consent by its URL", async ({
      consentAdminPage,
      consentAdminConsentApi,
      consentCleanupTracker,
      browser,
    }) => {
      test.skip(!hasSecondDataPrincipal(), 'TEST_DATA_PRINCIPAL_2_USERNAME/PASSWORD is not configured')

      const { consentId } = await seedConsent(
        consentAdminPage,
        consentAdminConsentApi,
        consentCleanupTracker,
        env.dataPrincipal.username,
        'ACTIVE',
      )

      const otherContext = await browser.newContext({
        storageState: authStateFile('data-principal-2'),
        baseURL: env.portalNavigationBaseUrl,
        ignoreHTTPSErrors: env.ignoreHttpsErrors,
      })
      const otherPage = await otherContext.newPage()
      const otherDetailPage = new ConsentDetailPage(otherPage, 'self')
      await otherDetailPage.goto(consentId)
      await expect(otherDetailPage.loadFailedMessage).toBeVisible()

      await otherContext.close()
    })
  })
})
