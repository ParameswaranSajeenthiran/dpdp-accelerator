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
 * UserProfileMenu.tsx - the account menu in the header. "Delete my account" is rendered only
 * when the session carries `account:self:delete`, which the dpdp-consent-user role grants and
 * dpdp-consent-admin does not - so its absence here means no such element exists, not that it
 * is hidden or disabled.
 */
export class UserProfileMenuPage {
  readonly trigger: Locator

  readonly signOutItem: Locator

  readonly deleteAccountItem: Locator

  constructor(private readonly page: Page) {
    this.trigger = page.getByRole('button', { name: 'Account' })
    this.signOutItem = page.getByRole('menuitem', { name: 'Sign out' })
    this.deleteAccountItem = page.getByRole('menuitem', { name: 'Delete my account' })
  }

  async open(): Promise<void> {
    await this.trigger.click()
  }

  /** The confirm button inside the deletion dialog, which repeats the menu item's wording. */
  confirmDeleteButton(): Locator {
    return this.page.getByRole('button', { name: 'Delete my account' })
  }

  cancelDeleteButton(): Locator {
    return this.page.getByRole('button', { name: 'Cancel' })
  }
}
