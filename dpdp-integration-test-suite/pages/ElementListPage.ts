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

/** ElementListPage.tsx - the read-only catalog of consent Elements at /elements. */
export class ElementListPage {
  readonly table: Locator
  readonly heading: Locator
  readonly addElementButton: Locator
  readonly nameSearch: Locator
  readonly searchButton: Locator
  readonly resetButton: Locator

  constructor(private readonly page: Page) {
    this.table = page.getByRole('table', { name: 'Consent elements' })
    this.heading = page.getByRole('heading', { name: 'Elements' })
    this.addElementButton = page.getByRole('button', { name: 'Add Element' })
    this.nameSearch = page.getByPlaceholder('Search by element name')
    this.searchButton = page.getByRole('button', { name: 'Search' })
    this.resetButton = page.getByRole('button', { name: 'Reset' })
  }

  async openCreateDialog(): Promise<void> {
    await this.addElementButton.click()
  }

  async searchByName(name: string): Promise<void> {
    await this.nameSearch.fill(name)
    await this.searchButton.click()
  }

  async resetSearch(): Promise<void> {
    await this.resetButton.click()
  }

  async goto(): Promise<void> {
    // No leading slash - see the comment in MyConsentPage.goto() for why.
    await this.page.goto('elements')
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

  async setRowsPerPage(count: number): Promise<void> {
    await this.page.getByLabel('Rows per page').click()
    await this.page.getByRole('option', { name: String(count), exact: true }).click()
  }

  get previousPageButton(): Locator {
    return this.page.getByRole('button', { name: 'Previous' })
  }

  get nextPageButton(): Locator {
    return this.page.getByRole('button', { name: 'Next' })
  }
}
