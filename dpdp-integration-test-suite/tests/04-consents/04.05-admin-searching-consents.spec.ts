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

import { test, expect, loginAsConsentAdmin } from '../../fixtures/auth.fixtures'
import { AdminConsentPage } from '../../pages/AdminConsentPage'
import { seedConsentViaApi } from '../../utils/consentSetup'
import { randomServiceId } from '../../utils/testData'

/**
 * The admin registry's Consent ID search, advanced subject/service filters, and the state
 * filter's interaction with both. Consent ID goes through a direct GET-by-ID
 * (`useAdminConsentListQuery`), not a list filter like subjectId/serviceId - see the
 * non-existent-id test below for why that means it can only ever load-fail, never show "no
 * results".
 */
test.describe('Admin searching Consents (UI)', () => {
  test('04.05.01 - Filtering by the exact consent id shows only that consent and disables the state filter', async ({
    browser,
    target,
    consentAdminConsentApi,
  }) => {
    const consentAdminPage = await loginAsConsentAdmin(browser)
    const first = await seedConsentViaApi(
      consentAdminConsentApi,
      target.personas.user.username,
      'ACTIVE',
    )
    const second = await seedConsentViaApi(
      consentAdminConsentApi,
      target.personas.user.username,
      'ACTIVE',
    )

    const registryPage = new AdminConsentPage(consentAdminPage)
    await registryPage.goto()
    await registryPage.searchByConsentId(first.consentId)

    await expect(registryPage.rowByConsentId(first.consentId)).toBeVisible()
    await expect(registryPage.rowByConsentId(second.consentId)).toHaveCount(0)
    await expect(registryPage.stateFilter).toBeDisabled()
    await consentAdminPage.context().close()
  })

  test('04.05.02 - The advanced subject and service filters narrow the list', async ({
    browser,
    target,
    consentAdminConsentApi,
  }) => {
    const consentAdminPage = await loginAsConsentAdmin(browser)
    const serviceId = randomServiceId()
    const { consentId } = await seedConsentViaApi(
      consentAdminConsentApi,
      target.personas.user.username,
      'ACTIVE',
      serviceId,
    )

    const registryPage = new AdminConsentPage(consentAdminPage)
    await registryPage.goto()
    await registryPage.filterBySubjectAndService(target.personas.user.username, serviceId)

    await expect(registryPage.rowByConsentId(consentId)).toBeVisible()
    await expect(registryPage.activeFilterChip(`User: ${target.personas.user.username}`)).toBeVisible()
    await expect(registryPage.activeFilterChip(`Service: ${serviceId}`)).toBeVisible()

    await registryPage.clearAllFilters()
    await expect(consentAdminPage.getByPlaceholder('Search by consent ID')).toHaveValue('')
    await consentAdminPage.context().close()
  })

  test('04.05.03 - Combining the state filter with the advanced subject/service filters narrows the list further', async ({
    browser,
    target,
    consentAdminConsentApi,
  }) => {
    const consentAdminPage = await loginAsConsentAdmin(browser)
    // Unlike the Consent ID filter (which disables the state filter - see the test above), the
    // subject/service filters don't - confirmed in AdminConsentFilters.tsx: only
    // `filters.consentId` disables it. Both seeded under the same unique service id and
    // narrowed to it first, so the state filter's effect is checked within a controlled
    // two-row set instead of the full, ever-growing unfiltered list.
    const serviceId = randomServiceId()
    const pending = await seedConsentViaApi(
      consentAdminConsentApi,
      target.personas.user.username,
      'PENDING',
      serviceId,
    )
    const active = await seedConsentViaApi(
      consentAdminConsentApi,
      target.personas.user.username,
      'ACTIVE',
      serviceId,
    )

    const registryPage = new AdminConsentPage(consentAdminPage)
    await registryPage.goto()
    await registryPage.filterBySubjectAndService(target.personas.user.username, serviceId)
    await expect(registryPage.stateFilter).toBeEnabled()
    await registryPage.filterByState('Pending')

    await expect(registryPage.rowByConsentId(pending.consentId)).toBeVisible()
    await expect(registryPage.rowByConsentId(active.consentId)).toHaveCount(0)
    await consentAdminPage.context().close()
  })

  test('04.05.04 - Searching by a non-existent consent id shows the load-failed message, not the empty-results one', async ({
    browser,
  }) => {
    const consentAdminPage = await loginAsConsentAdmin(browser)
    // Unlike subjectId/serviceId (which go through the real list-filter API), a Consent ID
    // search does a direct GET-by-ID (see useAdminConsentListQuery in
    // useAdminConsentQueries.ts) - confirmed by actually running this: a non-existent id 404s,
    // which the query surfaces as a load failure, never as "no results". A truncated/partial
    // id would 404 the same way, so there's no separate "partial match" case to test here,
    // unlike serviceId's real substring-vs-exact distinction (see the equivalent self-service
    // test).
    const registryPage = new AdminConsentPage(consentAdminPage)
    await registryPage.goto()
    await registryPage.searchByConsentId('00000000-0000-0000-0000-000000000000')
    await expect(registryPage.loadFailedMessage).toBeVisible()
    await consentAdminPage.context().close()
  })

  test('04.05.05 - A subject/service filter matching nothing shows the empty-results message', async ({
    browser,
  }) => {
    const consentAdminPage = await loginAsConsentAdmin(browser)
    // Unlike Consent ID (see the test above), subjectId/serviceId go through the real
    // list-filter API, so a non-match here legitimately produces "no results", not an error.
    const registryPage = new AdminConsentPage(consentAdminPage)
    await registryPage.goto()
    await registryPage.filterBySubjectAndService(
      `no-such-subject-${Date.now().toString()}`,
      `no-such-service-${Date.now().toString()}`,
    )
    await expect(registryPage.emptyStateMessage).toBeVisible()
    await consentAdminPage.context().close()
  })

  test("04.05.06 - The Relation filter distinguishes a consent's subject from its authorizer", async ({
    browser,
    target,
    consentAdminConsentApi,
  }) => {
    // No dedicated "parent"/"child" persona exists - the second, generic user account stands in
    // for the authorizer, same as tests/04-consents/04.07-user-viewing-consent-history.spec.ts's
    // delegated-consent case.
    const authorizer = target.personas.user2

    const consentAdminPage = await loginAsConsentAdmin(browser)
    // subjectId (target.personas.user) and authorizations[].userId (authorizer) deliberately
    // don't match - see the identical rationale in 04.07's delegated-consent test.
    const { consentId, serviceId } = await seedConsentViaApi(
      consentAdminConsentApi,
      target.personas.user.username,
      'PENDING',
      undefined,
      undefined,
      [{ userId: authorizer.username, type: 'PARENT' }],
    )

    const registryPage = new AdminConsentPage(consentAdminPage)
    await registryPage.goto()
    // Also narrowed to this consent's own serviceId - the authorizer persona is shared and never
    // cleaned up (AGENTS.md), so relation+user alone identifies a set that keeps growing across
    // every run that has ever used it, not this one consent. Without the extra narrowing, this
    // consent falls off the default page-1/oldest-first window once that set outgrows one page -
    // a guaranteed eventual failure, not a timing flake. See filterByUserRelationAndService's
    // own doc comment.
    await registryPage.filterByUserRelationAndService(authorizer.username, 'Authorizer', serviceId)
    await expect(registryPage.rowByConsentId(consentId)).toBeVisible()

    await registryPage.clearAllFilters()
    await registryPage.filterByUserAndRelation(authorizer.username, 'Subject')
    await expect(registryPage.rowByConsentId(consentId)).toHaveCount(0)
    await consentAdminPage.context().close()
  })
})
