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
import { selectMuiOption } from '../utils/muiSelect'

/** ConsentRegistryPage.tsx - a user's own "All Consents" list at /consents. */
export class ConsentRegistryPage {
  readonly table: Locator
  readonly serviceSearch: Locator
  readonly clearFiltersButton: Locator

  constructor(private readonly page: Page) {
    this.table = page.getByRole('table', { name: 'Consent registry table' })
    this.serviceSearch = page.getByPlaceholder('Search by service')
    this.clearFiltersButton = page.getByRole('button', { name: 'Clear all filters' })
  }

  async goto(): Promise<void> {
    await this.page.goto('consents')
  }

  rowByConsentId(consentId: string): Locator {
    return this.table.locator(`tr[data-consent-id="${consentId}"]`)
  }

  async openByConsentId(consentId: string): Promise<void> {
    await this.rowByConsentId(consentId).click()
  }

  async approveFromList(consentId: string): Promise<void> {
    await this.rowByConsentId(consentId).getByRole('button', { name: 'Approve' }).click()
  }

  async revokeFromList(consentId: string): Promise<void> {
    await this.rowByConsentId(consentId).getByRole('button', { name: 'Revoke' }).click()
  }

  async searchByService(serviceId: string): Promise<void> {
    await this.serviceSearch.fill(serviceId)
    await this.serviceSearch.press('Enter')
  }

  async filterByState(stateLabel: string): Promise<void> {
    await selectMuiOption(this.page, 'consent-state', stateLabel)
  }

  async clearFilters(): Promise<void> {
    await this.clearFiltersButton.click()
  }

  get emptyStateMessage(): Locator {
    return this.page.getByText('No consents found for the selected filters.')
  }
}
