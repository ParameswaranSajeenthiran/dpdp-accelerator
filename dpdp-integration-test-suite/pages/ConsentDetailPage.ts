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

export type ConsentDetailVariant = 'self' | 'admin'

const CONFIRM_LABEL = {
  approve: 'Approve Consent',
  reject: 'Reject Consent',
  revoke: 'Revoke Consent',
} as const

const DIALOG_TITLE = {
  approve: 'Confirm Approval',
  reject: 'Confirm Rejection',
  revoke: 'Confirm Revocation',
} as const

/**
 * ConsentDetailsPage.tsx - one consent's full detail, reached at either /consents/:id (self,
 * `variant: 'self'`) or /administration/consents/:id (admin, `variant: 'admin'`). Both variants
 * render the same metadata/purposes/authorizations sections and the same ConsentActionDialog,
 * just with a different action set available (see ConsentDetailsPage.tsx's canApprove/canReject/
 * canRevoke, which are all variant- and state-gated).
 */
export class ConsentDetailPage {
  readonly loadFailedMessage: Locator
  readonly notFoundMessage: Locator
  readonly backButton: Locator
  readonly purposesSection: Locator
  readonly authorizationsSection: Locator

  constructor(
    private readonly page: Page,
    private readonly variant: ConsentDetailVariant = 'self',
  ) {
    this.loadFailedMessage = page.getByText('Unable to load consents right now.')
    this.notFoundMessage = page.getByText('Consent record not found')
    this.backButton = page.getByRole('button', { name: 'Back to Registry' })
    this.purposesSection = page
      .locator('.MuiCard-root')
      .filter({ has: page.getByRole('heading', { name: 'Consent Purposes' }) })
    this.authorizationsSection = page
      .locator('.MuiCard-root')
      .filter({ has: page.getByRole('heading', { name: 'Authorizations' }) })
  }

  async goto(consentId: string): Promise<void> {
    const basePath = this.variant === 'admin' ? 'administration/consents' : 'consents'
    // No leading slash - see the comment in ConsentRegistryPage.goto() for why.
    await this.page.goto(`${basePath}/${consentId}`)
  }

  purposeSummary(purposeName: string): Locator {
    return this.purposesSection.getByRole('button', { name: new RegExp(purposeName) })
  }

  async expandPurpose(purposeName: string): Promise<void> {
    await this.purposeSummary(purposeName).click()
  }

  elementRow(elementName: string): Locator {
    return this.purposesSection.getByRole('row', { name: new RegExp(elementName) })
  }

  authorizationRow(userId: string): Locator {
    return this.authorizationsSection.getByRole('row', { name: new RegExp(userId) })
  }

  private actionButton(action: keyof typeof CONFIRM_LABEL): Locator {
    const label = action === 'approve' ? 'Approve' : action === 'reject' ? 'Reject' : 'Revoke'
    return this.page.getByRole('button', { name: label, exact: true })
  }

  async openActionDialog(action: keyof typeof CONFIRM_LABEL): Promise<void> {
    await this.actionButton(action).click()
  }

  async confirmAction(action: keyof typeof CONFIRM_LABEL): Promise<void> {
    await this.page.getByRole('button', { name: CONFIRM_LABEL[action] }).click()
  }

  dialogTitle(action: keyof typeof CONFIRM_LABEL): Locator {
    return this.page.getByRole('heading', { name: DIALOG_TITLE[action], exact: true })
  }

  get dialogErrorAlert(): Locator {
    return this.page.getByRole('alert')
  }

  async cancelDialog(): Promise<void> {
    await this.page.getByRole('button', { name: 'Cancel' }).click()
  }
}
