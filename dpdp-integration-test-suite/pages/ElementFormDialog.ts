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

/** ElementFormDialog.tsx, opened from ElementListPage's "Add Element" button. */
export class ElementFormDialog {
  readonly root: Locator
  readonly title: Locator
  readonly nameField: Locator
  readonly displayNameField: Locator
  readonly descriptionField: Locator
  readonly createButton: Locator
  readonly cancelButton: Locator
  readonly errorAlert: Locator

  constructor(page: Page) {
    this.root = page.getByRole('dialog')
    this.title = this.root.getByRole('heading', { name: 'Add Element' })
    // Required field renders its label as "Name *" - see the identical comment in
    // PurposeFormDialog.ts.
    this.nameField = this.root.getByLabel('Name *', { exact: true })
    this.displayNameField = this.root.getByLabel('Display name', { exact: true })
    this.descriptionField = this.root.getByLabel('Description', { exact: true })
    this.createButton = this.root.getByRole('button', { name: 'Create' })
    this.cancelButton = this.root.getByRole('button', { name: 'Cancel' })
    this.errorAlert = this.root.getByRole('alert')
  }

  async fill(fields: { name?: string; displayName?: string; description?: string }): Promise<void> {
    if (fields.name !== undefined) {
      await this.nameField.fill(fields.name)
    }
    if (fields.displayName !== undefined) {
      await this.displayNameField.fill(fields.displayName)
    }
    if (fields.description !== undefined) {
      await this.descriptionField.fill(fields.description)
    }
  }

  async addProperty(key: string, value: string): Promise<void> {
    await this.root.getByRole('button', { name: 'Add property' }).click()
    await this.root.getByLabel('Key', { exact: true }).last().fill(key)
    await this.root.getByLabel('Value', { exact: true }).last().fill(value)
  }

  async submit(): Promise<void> {
    await this.createButton.click()
  }

  async cancel(): Promise<void> {
    await this.cancelButton.click()
  }

  /** Clicks into the Name field and back out to the dialog title, to trigger a real blur event. */
  async blurName(): Promise<void> {
    await this.nameField.click()
    await this.title.click()
  }
}
