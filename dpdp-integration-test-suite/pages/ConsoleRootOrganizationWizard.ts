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
import { withCrossProcessLock } from '../utils/crossProcessLock'

export interface NewTenantFields {
  domain: string
  firstName: string
  lastName: string
  username: string
  email: string
  password: string
}

/**
 * The "Create a Root Organization" dialog, opened from the super tenant's Console at
 * `/t/carbon.super/console/root/organizations` via its "New Root Organization" button. A "root
 * organization" here IS a classic WSO2 IS tenant: the list page it's opened from
 * is backed by `GET /api/server/v1/tenants` (the Tenant Management REST API), the exact same
 * tenants `provision-test-users.sh` and this suite's own admin persona already operate in.
 *
 * Deliberately not the raw Tenant Management REST API (`POST /api/server/v1/tenants`), even
 * though that also works: that endpoint's `owners[].password` does not actually become usable
 * for login until a separate follow-up call, while this dialog's password
 * field works immediately. See docs/plan discussion for the full comparison; this dialog is the
 * only tenant-creation path this suite uses.
 */
export class ConsoleRootOrganizationWizard {
  private readonly page: Page
  readonly root: Locator
  readonly newRootOrganizationButton: Locator
  readonly domainField: Locator
  readonly firstNameField: Locator
  readonly lastNameField: Locator
  readonly usernameField: Locator
  readonly emailField: Locator
  readonly passwordField: Locator
  readonly createButton: Locator

  constructor(page: Page) {
    this.page = page
    this.newRootOrganizationButton = page.getByRole('button', { name: 'New Root Organization' })
    this.root = page.getByRole('dialog').filter({ hasText: 'Create a Root Organization' })
    // Placeholders carry a typographic right single-quote ('), not an ASCII apostrophe -
    // a straight-quote locator silently matches nothing.
    this.domainField = this.root.getByPlaceholder('Enter organization handle (domain)')
    this.firstNameField = this.root.getByPlaceholder('Enter the admin’s first name.')
    this.lastNameField = this.root.getByPlaceholder('Enter the admin’s last name.')
    this.usernameField = this.root.getByPlaceholder('Enter the username')
    this.emailField = this.root.getByPlaceholder('Enter the admin’s email address.')
    this.passwordField = this.root.getByPlaceholder('Enter a password for the administrator.')
    this.createButton = this.root.getByRole('button', { name: 'Create' })
  }

  async open(): Promise<void> {
    await this.newRootOrganizationButton.click()
  }

  async createTenant(fields: NewTenantFields): Promise<void> {
    await this.domainField.fill(fields.domain)
    await this.firstNameField.fill(fields.firstName)
    await this.lastNameField.fill(fields.lastName)
    await this.usernameField.fill(fields.username)
    await this.emailField.fill(fields.email)
    await this.passwordField.fill(fields.password)
    // Returns once the dialog's own POST has answered rather than after a fixed delay. Provisioning,
    // the accelerator's onTenantCreate included, finishes within that request, so the caller can
    // close the context straight away.
    //
    // One creation at a time across workers: two overlapping tenant creations can fail one of them
    // with a 500 (TM-65002, a ConcurrentModificationException in IS's OIDC-scope setup during
    // onTenantCreate) - wso2/product-is#28519. Remove the lock once that is fixed.
    const response = await withCrossProcessLock('tenant-creation', async () => {
      const created = this.page.waitForResponse(
        (candidate) =>
          candidate.url().includes('/api/server/v1/tenants') && candidate.request().method() === 'POST',
      )
      await this.createButton.click()
      return created
    })
    if (!response.ok()) {
      throw new Error(`Tenant creation failed: POST /api/server/v1/tenants returned ${String(response.status())}`)
    }
  }
}
