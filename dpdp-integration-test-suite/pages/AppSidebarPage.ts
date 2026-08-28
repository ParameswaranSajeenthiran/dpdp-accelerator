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
 * AppSidebar.tsx - the primary left nav. Each category ("Consent", "Definitions",
 * "Administration") and its items are filtered out of the DOM entirely (not just
 * disabled/greyed) when the current persona lacks the item's required scope - so absence here
 * means "no matching element exists", not "exists but hidden/disabled".
 */
export class AppSidebarPage {
  readonly nav: Locator

  constructor(page: Page) {
    // The "Primary navigation" aria-label lands on the outer <Sidebar> wrapper, which renders
    // as a `complementary` (<aside>) landmark - the inner <Sidebar.Nav> is an unnamed <nav> -
    // confirmed via an actual accessibility-tree snapshot, not guessed. "Dashboard" also appears
    // as a main-content heading, so this scope is required to avoid ambiguous matches.
    this.nav = page.getByRole('complementary')
  }

  /** A category heading or item label, matched by its exact visible text. */
  label(text: string): Locator {
    return this.nav.getByText(text, { exact: true })
  }
}
