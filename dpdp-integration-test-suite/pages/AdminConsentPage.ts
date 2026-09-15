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

import { type Locator, type Page } from '@playwright/test'
import { submitFilterValue } from '../utils/filterCommit'
import { ConsentRegistryTable } from './ConsentRegistryTable'

/** AdminConsentRegistryPage.tsx - the admin view of every subject's consents. */
export class AdminConsentPage extends ConsentRegistryTable {
  readonly consentIdSearch: Locator
  readonly advancedFiltersButton: Locator

  constructor(page: Page) {
    super(page)
    this.consentIdSearch = page.getByPlaceholder('Search by consent ID')
    this.advancedFiltersButton = page.getByRole('button', { name: 'Advanced filters' })
  }

  async goto(): Promise<void> {
    // No leading slash - see the comment in MyConsentPage.goto() for why.
    await this.page.goto('administration/consents')
  }

  async searchByConsentId(consentId: string): Promise<void> {
    await submitFilterValue(
      this.page,
      this.consentIdSearch,
      async () => {
        await this.consentIdSearch.press('Enter')
      },
      'consentId',
      consentId,
    )
  }

  async openAdvancedFilters(): Promise<void> {
    await this.advancedFiltersButton.click()
  }

  async filterBySubjectAndService(subjectId: string, serviceId: string): Promise<void> {
    await this.openAdvancedFilters()
    // exact: true - a new "Relation" filter's helper text ("Set a User to filter by relation")
    // also carries an aria-label containing "User", so a substring match resolves to two elements.
    await this.page.getByLabel('User', { exact: true }).fill(subjectId)
    await this.page.getByLabel('Service').fill(serviceId)
    await this.page.getByRole('button', { name: 'Apply' }).click()
  }

  get relationFilter(): Locator {
    // getByLabel, not getByRole('combobox', ...) like stateFilter above - confirmed live this
    // Select's rendered role="combobox" isn't recognised as such by Chromium's own accessibility
    // tree (missing aria-controls, most likely), so a role query resolves to nothing even though
    // the label association is otherwise identical. getByLabel resolves it correctly regardless.
    return this.page.getByLabel('Relation', { exact: true })
  }

  /**
   * Distinguishes "consents about this user" (Subject) from "consents this user authorized,
   * possibly on someone else's behalf" (Authorizer) - the one mechanism for finding a delegated
   * consent by its authorizer rather than its subject. No separate Apply click needed here: the
   * Select's own onChange applies immediately, carrying along whatever User value is already
   * typed - unlike filterBySubjectAndService's plain text inputs, which stay in a draft state
   * until Apply is clicked.
   *
   * User/Relation/State are their own always-visible toolbar controls, distinct from the
   * "Advanced filters" popover (Service/Purpose/etc.) - unlike filterBySubjectAndService, this
   * never opens that popover. It did once, and that popover's own full-viewport invisible
   * backdrop then intercepted the click meant for the toolbar's Relation control, which sits
   * outside the popover entirely - not a product bug, just this method clicking through a panel
   * it never needed open.
   */
  async filterByUserAndRelation(userId: string, relation: 'Any' | 'Subject' | 'Authorizer'): Promise<void> {
    const userField = this.page.getByLabel('User', { exact: true })
    await userField.fill(userId)
    await userField.press('Tab')
    await this.relationFilter.click()
    await this.page.getByRole('option', { name: relation, exact: true }).click()
  }

  get stateFilter(): Locator {
    // getByLabel('State') also matches an unrelated tooltip whose aria-label contains "state" as
    // a substring ("Remove the Consent ID filter to use the state filter."), so this goes
    // straight to the combobox by role instead.
    return this.page.getByRole('combobox', { name: 'State' })
  }

  async filterByState(stateLabel: string): Promise<void> {
    await this.stateFilter.click()
    await this.page.getByRole('option', { name: stateLabel, exact: true }).click()
  }

  activeFilterChip(labelAndValue: string): Locator {
    return this.page.getByText(labelAndValue, { exact: true })
  }

  async clearAllFilters(): Promise<void> {
    await this.page.getByRole('button', { name: 'Clear all' }).click()
  }

  /**
   * Shown by the registry TABLE itself (distinct from ConsentDetailPage.loadFailedMessage) when
   * a Consent ID search 404s - useAdminConsentListQuery does a direct GET-by-ID for consentId
   * (not a list filter like subjectId/serviceId), so a non-existent id surfaces as a load
   * failure, never as "no results".
   */
  get loadFailedMessage(): Locator {
    return this.page.getByText('Unable to load consents right now.')
  }
}
