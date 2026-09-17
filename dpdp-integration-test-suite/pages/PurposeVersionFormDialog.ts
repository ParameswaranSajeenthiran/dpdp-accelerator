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
 * PurposeVersionFormDialog.tsx, opened from PurposeDetailPage's "Add Version" button. Pre-fills
 * description/elements/properties from whichever version is currently latest - this dialog only
 * ever needs to override `version` and, deliberately, `setAsLatest` (defaults to checked).
 */
export class PurposeVersionFormDialog {
  readonly root: Locator
  readonly title: Locator
  readonly versionField: Locator
  readonly setAsLatestCheckbox: Locator
  readonly descriptionField: Locator
  readonly createButton: Locator
  readonly cancelButton: Locator
  readonly errorAlert: Locator

  constructor(page: Page) {
    this.root = page.getByRole('dialog')
    this.title = this.root.getByRole('heading', { name: 'Add Version' })
    // Required field renders its label as "Version *" - see the identical comment in
    // PurposeFormDialog.ts.
    this.versionField = this.root.getByLabel('Version *', { exact: true })
    this.setAsLatestCheckbox = this.root.getByRole('checkbox', { name: 'Set as the latest version' })
    this.descriptionField = this.root.getByLabel('Description', { exact: true })
    this.createButton = this.root.getByRole('button', { name: 'Create' })
    this.cancelButton = this.root.getByRole('button', { name: 'Cancel' })
    this.errorAlert = this.root.getByRole('alert')
  }

  async fill(fields: { version?: string; description?: string }): Promise<void> {
    if (fields.version !== undefined) {
      await this.versionField.fill(fields.version)
    }
    if (fields.description !== undefined) {
      await this.descriptionField.fill(fields.description)
    }
  }

  /** Checked by default (a new version becomes latest unless this is unchecked first). */
  async setAsLatest(value: boolean): Promise<void> {
    if (value) {
      await this.setAsLatestCheckbox.check()
    } else {
      await this.setAsLatestCheckbox.uncheck()
    }
  }

  async submit(): Promise<void> {
    await this.createButton.click()
  }

  async cancel(): Promise<void> {
    await this.cancelButton.click()
  }

  /** Clicks into the Version field and back out to the dialog title, to trigger a real blur event. */
  async blurVersion(): Promise<void> {
    await this.versionField.click()
    await this.title.click()
  }
}
