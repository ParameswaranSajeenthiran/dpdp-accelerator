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

/** PurposeDetailsPage.tsx - a single Purpose's fields, properties, elements and version history. */
export class PurposeDetailPage {
  readonly elementsTable: Locator
  readonly versionsTable: Locator
  readonly propertiesTable: Locator
  readonly loadFailedMessage: Locator
  readonly backButton: Locator

  constructor(private readonly page: Page) {
    this.elementsTable = page
      .locator('.MuiCard-root')
      .filter({ has: page.getByRole('heading', { name: 'Elements' }) })
      .getByRole('table')
    this.versionsTable = page
      .locator('.MuiCard-root')
      .filter({ has: page.getByRole('heading', { name: 'Version history' }) })
      .getByRole('table')
    this.propertiesTable = page
      .locator('.MuiCard-root')
      .filter({ has: page.getByRole('heading', { name: 'Properties' }) })
      .getByRole('table')
    this.loadFailedMessage = page.getByText('Unable to load purposes right now.')
    this.backButton = page.getByRole('button', { name: 'Back to purposes' })
  }

  async goto(purposeId: string): Promise<void> {
    // No leading slash - see the comment in MyConsentPage.goto() for why.
    await this.page.goto(`purposes/${purposeId}`)
  }

  heading(name: string): Locator {
    return this.page.getByRole('heading', { name })
  }

  elementRow(elementDisplayName: string): Locator {
    return this.elementsTable.getByRole('row', { name: new RegExp(elementDisplayName) })
  }

  versionRow(version: string): Locator {
    return this.versionsTable.getByRole('row', { name: new RegExp(version) })
  }

  /** A property row, matched by its key. */
  propertyRow(key: string): Locator {
    return this.propertiesTable.getByRole('row', { name: new RegExp(key) })
  }

  /**
   * The machine `name` field's rendered value - scoped to a <code> element since the same text
   * also appears as the page heading and the current-page breadcrumb, which would make a bare
   * page.getByText(name) ambiguous. See the identical pattern in ElementDetailPage.nameValue.
   */
  nameValue(name: string): Locator {
    return this.page.locator('code').filter({ hasText: name })
  }

  /** The Purpose ID shown (and copyable) in the card header above the name/type/version/description fields. */
  purposeIdValue(id: string): Locator {
    return this.page.getByText(id, { exact: true })
  }
}
