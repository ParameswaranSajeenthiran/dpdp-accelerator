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

/** ElementDetailsPage.tsx - a single Element's fields (id, name, display name, description). */
export class ElementDetailPage {
  readonly loadFailedMessage: Locator
  readonly backButton: Locator

  constructor(private readonly page: Page) {
    this.loadFailedMessage = page.getByText('Unable to load elements right now.')
    this.backButton = page.getByRole('button', { name: 'Back to elements' })
  }

  async goto(elementId: string): Promise<void> {
    // No leading slash - see the comment in ConsentRegistryPage.goto() for why.
    await this.page.goto(`elements/${elementId}`)
  }

  heading(name: string): Locator {
    return this.page.getByRole('heading', { name })
  }
}
