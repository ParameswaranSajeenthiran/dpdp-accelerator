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

import { test, expect } from '../../fixtures/auth.fixtures'
import { AdminConsentRegistryPage } from '../../pages/AdminConsentRegistryPage'
import { ConsentDetailPage } from '../../pages/ConsentDetailPage'
import { env } from '../../utils/env'
import { seedConsent } from '../../utils/consentSetup'
import { uniqueServiceId } from '../../utils/testData'

/**
 * The admin registry (/administration/consents) only ever offers Revoke, never Approve/Reject -
 * ConsentRegistryTable.tsx's `canApprove` prop is never passed on this page and
 * ConsentDetailsPage.tsx only computes canApprove/canReject when `variant === 'self'` - so several
 * tests below assert that invariant directly rather than assuming it. Only Consent creation goes
 * through the admin API (see utils/consentSetup.ts); the Element and Purpose it needs are
 * created through the real admin UI forms first, on this same consentAdminPage.
 */
test.describe('Admin consent registry (UI)', () => {
  test.describe('Happy paths', () => {
    test('02.02.01 - A consent created via the API appears in the admin list with its subject', async ({
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

      const registryPage = new AdminConsentRegistryPage(consentAdminPage)
      await registryPage.goto()
      // The unfiltered list is sorted oldest-first with no way to jump pages, so a freshly
      // created row is found by its own id rather than by browsing - see
      // tests/plan.md.
      await registryPage.searchByConsentId(consentId)
      await expect(registryPage.rowByConsentId(consentId)).toContainText(env.dataPrincipal.username)
      await expect(registryPage.rowByConsentId(consentId)).toContainText(serviceId)
    })

    test('02.02.02 - Admin can revoke an Active consent from the list', async ({
      consentAdminPage,
      consentAdminConsentApi,
      consentCleanupTracker,
    }) => {
      const { consentId } = await seedConsent(
        consentAdminPage,
        consentAdminConsentApi,
        consentCleanupTracker,
        env.dataPrincipal.username,
        'ACTIVE',
      )

      const registryPage = new AdminConsentRegistryPage(consentAdminPage)
      await registryPage.goto()
      await registryPage.searchByConsentId(consentId)
      await registryPage.revokeFromList(consentId)
      await consentAdminPage.getByRole('button', { name: 'Revoke Consent' }).click()

      await expect(registryPage.rowByConsentId(consentId)).toContainText('Revoked')
    })

    test('02.02.03 - Filtering by the exact consent id shows only that consent and disables the state filter', async ({
      consentAdminPage,
      consentAdminConsentApi,
      consentCleanupTracker,
    }) => {
      const first = await seedConsent(
        consentAdminPage,
        consentAdminConsentApi,
        consentCleanupTracker,
        env.dataPrincipal.username,
        'ACTIVE',
      )
      const second = await seedConsent(
        consentAdminPage,
        consentAdminConsentApi,
        consentCleanupTracker,
        env.dataPrincipal.username,
        'ACTIVE',
      )

      const registryPage = new AdminConsentRegistryPage(consentAdminPage)
      await registryPage.goto()
      await registryPage.searchByConsentId(first.consentId)

      await expect(registryPage.rowByConsentId(first.consentId)).toBeVisible()
      await expect(registryPage.rowByConsentId(second.consentId)).toHaveCount(0)
      await expect(registryPage.stateFilter).toBeDisabled()
    })

    test('02.02.04 - The advanced subject and service filters narrow the list', async ({
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

      const registryPage = new AdminConsentRegistryPage(consentAdminPage)
      await registryPage.goto()
      await registryPage.filterBySubjectAndService(env.dataPrincipal.username, serviceId)

      await expect(registryPage.rowByConsentId(consentId)).toBeVisible()
      await expect(registryPage.activeFilterChip(`User: ${env.dataPrincipal.username}`)).toBeVisible()
      await expect(registryPage.activeFilterChip(`Service: ${serviceId}`)).toBeVisible()

      await registryPage.clearAllFilters()
      await expect(consentAdminPage.getByPlaceholder('Search by consent ID')).toHaveValue('')
    })

    test('02.02.05 - Combining the state filter with the advanced subject/service filters narrows the list further', async ({
      consentAdminPage,
      consentAdminConsentApi,
      consentCleanupTracker,
    }) => {
      // Unlike the Consent ID filter (which disables the state filter - see the test above), the
      // subject/service filters don't - confirmed in AdminConsentFilters.tsx: only
      // `filters.consentId` disables it. Both seeded under the same unique service id and
      // narrowed to it first, so the state filter's effect is checked within a controlled
      // two-row set instead of the full, ever-growing unfiltered list.
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

      const registryPage = new AdminConsentRegistryPage(consentAdminPage)
      await registryPage.goto()
      await registryPage.filterBySubjectAndService(env.dataPrincipal.username, serviceId)
      await expect(registryPage.stateFilter).toBeEnabled()
      await registryPage.filterByState('Pending')

      await expect(registryPage.rowByConsentId(pending.consentId)).toBeVisible()
      await expect(registryPage.rowByConsentId(active.consentId)).toHaveCount(0)
    })

    test('02.02.06 - The admin detail page shows Revoke but never Approve or Reject for an Active consent', async ({
      consentAdminPage,
      consentAdminConsentApi,
      consentCleanupTracker,
    }) => {
      const { consentId } = await seedConsent(
        consentAdminPage,
        consentAdminConsentApi,
        consentCleanupTracker,
        env.dataPrincipal.username,
        'ACTIVE',
      )

      const detailPage = new ConsentDetailPage(consentAdminPage, 'admin')
      await detailPage.goto(consentId)
      await expect(consentAdminPage.getByRole('button', { name: 'Revoke', exact: true })).toBeVisible()
      await expect(consentAdminPage.getByRole('button', { name: 'Approve', exact: true })).toHaveCount(0)
      await expect(consentAdminPage.getByRole('button', { name: 'Reject', exact: true })).toHaveCount(0)
    })
  })

  test.describe('Validation violations', () => {
    test('02.02.07 - The admin list shows no Approve action for a Pending consent, and no Revoke action either', async ({
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

      const registryPage = new AdminConsentRegistryPage(consentAdminPage)
      await registryPage.goto()
      await registryPage.searchByConsentId(consentId)
      const row = registryPage.rowByConsentId(consentId)
      await expect(row).toBeVisible()
      await expect(row.getByRole('button', { name: 'Approve' })).toHaveCount(0)
      await expect(row.getByRole('button', { name: 'Revoke' })).toHaveCount(0)
    })

    test('02.02.08 - The admin detail page offers no action at all for a Pending consent', async ({
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

      const detailPage = new ConsentDetailPage(consentAdminPage, 'admin')
      await detailPage.goto(consentId)
      await expect(consentAdminPage.getByRole('button', { name: 'Approve', exact: true })).toHaveCount(0)
      await expect(consentAdminPage.getByRole('button', { name: 'Reject', exact: true })).toHaveCount(0)
      await expect(consentAdminPage.getByRole('button', { name: 'Revoke', exact: true })).toHaveCount(0)
    })

    test('02.02.09 - An unknown consent id shows the load-failed message with a way back to the registry', async ({
      consentAdminPage,
    }) => {
      const detailPage = new ConsentDetailPage(consentAdminPage, 'admin')
      await detailPage.goto('00000000-0000-0000-0000-000000000000')
      await expect(detailPage.loadFailedMessage).toBeVisible()
      await detailPage.backButton.click()
      await expect(consentAdminPage).toHaveURL(/\/administration\/consents$/)
    })

    test('02.02.10 - Searching by a non-existent consent id shows the load-failed message, not the empty-results one', async ({
      consentAdminPage,
    }) => {
      // Unlike subjectId/serviceId (which go through the real list-filter API), a Consent ID
      // search does a direct GET-by-ID (see useAdminConsentListQuery in
      // useAdminConsentQueries.ts) - confirmed by actually running this: a non-existent id 404s,
      // which the query surfaces as a load failure, never as "no results". A truncated/partial
      // id would 404 the same way, so there's no separate "partial match" case to test here,
      // unlike serviceId's real substring-vs-exact distinction (see the equivalent self-service
      // test).
      const registryPage = new AdminConsentRegistryPage(consentAdminPage)
      await registryPage.goto()
      await registryPage.searchByConsentId('00000000-0000-0000-0000-000000000000')
      await expect(registryPage.loadFailedMessage).toBeVisible()
    })

    test('02.02.11 - A subject/service filter matching nothing shows the empty-results message', async ({
      consentAdminPage,
    }) => {
      // Unlike Consent ID (see the test above), subjectId/serviceId go through the real
      // list-filter API, so a non-match here legitimately produces "no results", not an error.
      const registryPage = new AdminConsentRegistryPage(consentAdminPage)
      await registryPage.goto()
      await registryPage.filterBySubjectAndService(
        `no-such-subject-${Date.now().toString()}`,
        `no-such-service-${Date.now().toString()}`,
      )
      await expect(registryPage.emptyStateMessage).toBeVisible()
    })
  })
})
