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

/**
 * ConsentRegistryTable.tsx - the consent list rendered by both registries.
 *
 * The self-service registry (MyConsentPage, /consents) and the admin registry
 * (AdminConsentPage, /administration/consents) wrap the same table component with different
 * filter controls around it, so everything that addresses a row or the table's own empty state
 * lives here. Subclasses add their own filters and their own goto().
 */
export abstract class ConsentRegistryTable {
  readonly table: Locator

  // Protected rather than private: every subclass drives its own filter controls and its own
  // goto() off the same page handle.
  protected constructor(protected readonly page: Page) {
    this.table = page.getByRole('table', { name: 'Consent registry table' })
  }

  abstract goto(): Promise<void>

  /**
   * Rows are addressed by the consent ID the server issued, exposed by the component as a data
   * attribute. The environment is shared and never reset, so positional selectors (nth-child,
   * "the first row") would be matching whatever a concurrent test happened to create.
   */
  rowByConsentId(consentId: string): Locator {
    return this.table.locator(`tr[data-consent-id="${consentId}"]`)
  }

  async openByConsentId(consentId: string): Promise<void> {
    await this.rowByConsentId(consentId).click()
  }

  async revokeFromList(consentId: string): Promise<void> {
    await this.rowByConsentId(consentId).getByRole('button', { name: 'Revoke' }).click()
  }

  get emptyStateMessage(): Locator {
    return this.page.getByText('No consents found for the selected filters.')
  }
}
