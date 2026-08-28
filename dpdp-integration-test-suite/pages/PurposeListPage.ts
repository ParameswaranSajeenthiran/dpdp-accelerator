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

/** PurposeListPage.tsx - the read-only catalog of consent Purposes at /purposes. */
export class PurposeListPage {
  readonly table: Locator
  readonly heading: Locator
  readonly addPurposeButton: Locator
  readonly nameSearch: Locator
  readonly typeFilter: Locator
  readonly searchButton: Locator
  readonly resetButton: Locator

  constructor(private readonly page: Page) {
    this.table = page.getByRole('table', { name: 'Consent purposes' })
    this.heading = page.getByRole('heading', { name: 'Purposes' })
    this.addPurposeButton = page.getByRole('button', { name: 'Add Purpose' })
    this.nameSearch = page.getByPlaceholder('Search by purpose name')
    this.typeFilter = page.getByLabel('Type', { exact: true })
    this.searchButton = page.getByRole('button', { name: 'Search' })
    this.resetButton = page.getByRole('button', { name: 'Reset' })
  }

  async openCreateDialog(): Promise<void> {
    await this.addPurposeButton.click()
  }

  /** Both fields are optional; either or both may be searched at once. */
  async search(options: { name?: string; type?: string } = {}): Promise<void> {
    if (options.name !== undefined) {
      await this.nameSearch.fill(options.name)
    }
    if (options.type !== undefined) {
      await this.typeFilter.fill(options.type)
    }
    await this.searchButton.click()
  }

  async resetSearch(): Promise<void> {
    await this.resetButton.click()
  }

  async goto(): Promise<void> {
    // No leading slash - see the comment in MyConsentPage.goto() for why.
    await this.page.goto('purposes')
  }

  rowByName(name: string): Locator {
    return this.table.getByRole('row', { name: new RegExp(name) })
  }

  /** Data rows only - scoped to tbody so the header row is never counted as a result. */
  get rows(): Locator {
    return this.table.locator('tbody').getByRole('row')
  }

  async openByName(name: string): Promise<void> {
    await this.rowByName(name).click()
  }

  async openRowsPerPageMenu(): Promise<void> {
    await this.page.getByLabel('Rows per page').click()
  }

  async setRowsPerPage(count: number): Promise<void> {
    await this.openRowsPerPageMenu()
    await this.page.getByRole('option', { name: String(count), exact: true }).click()
  }

  get previousPageButton(): Locator {
    return this.page.getByRole('button', { name: 'Previous' })
  }

  get nextPageButton(): Locator {
    return this.page.getByRole('button', { name: 'Next' })
  }
}
