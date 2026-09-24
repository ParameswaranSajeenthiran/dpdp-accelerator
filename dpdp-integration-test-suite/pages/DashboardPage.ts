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

export const CONSENT_STATS = [
  'consent-pending',
  'consent-active',
  'consent-rejected',
  'consent-revoked',
  'consent-expired',
] as const

export const COMPLAINT_STATS = [
  'complaint-open',
  'complaint-in-progress',
  'complaint-waiting-on-client',
  'complaint-waiting-on-dpo',
  'complaint-resolved',
] as const

export const CATALOG_STATS = ['catalog-purposes', 'catalog-elements'] as const

export type DashboardStat =
  | (typeof CONSENT_STATS)[number]
  | (typeof COMPLAINT_STATS)[number]
  | (typeof CATALOG_STATS)[number]

/**
 * DashboardPage.tsx at /dashboard. Every count card carries a `data-stat` attribute - the
 * dashboard's own E2E hook, pinned by DashboardPage.test.tsx - and StatCard renders its value as
 * an `h5`, so a count is read without depending on MUI class names or on label text.
 */
export class DashboardPage {
  readonly heading: Locator
  readonly adminSubtitle: Locator
  readonly consentsHeading: Locator
  readonly complaintsHeading: Locator
  readonly viewConsentsLink: Locator
  readonly viewComplaintsLink: Locator
  /** Cards #291 removed - `dashboard.totalConsents`/`totalComplaints` no longer exist in i18n. */
  readonly totalConsentsCard: Locator
  readonly totalComplaintsCard: Locator

  constructor(private readonly page: Page) {
    this.heading = page.getByRole('heading', { name: 'Dashboard', exact: true })
    this.adminSubtitle = page.getByText('An overview of consent activity across all users.')
    this.consentsHeading = page.getByRole('heading', { name: 'Consents', exact: true })
    this.complaintsHeading = page.getByRole('heading', { name: 'Complaints', exact: true })
    this.viewConsentsLink = page.getByRole('link', { name: 'View all consents' })
    this.viewComplaintsLink = page.getByRole('link', { name: 'View all complaints' })
    this.totalConsentsCard = page.getByText('Total consents', { exact: true })
    this.totalComplaintsCard = page.getByText('Total complaints', { exact: true })
  }

  async goto(): Promise<void> {
    // No leading slash - see the comment in MyConsentPage.goto() for why.
    await this.page.goto('dashboard')
  }

  card(stat: DashboardStat): Locator {
    return this.page.locator(`[data-stat="${stat}"]`)
  }

  /** The card's number: "-" while its query loads, then a count, or "100+" past the cap. */
  statValue(stat: DashboardStat): Locator {
    return this.card(stat).locator('h5')
  }

  /**
   * Every listed card's current value, keyed by stat. A one-shot read - wrap it in `expect.poll`
   * so it retries past the "-" loading placeholder rather than snapshotting it.
   */
  async readStats<S extends DashboardStat>(stats: readonly S[]): Promise<Record<S, string>> {
    const entries = await Promise.all(
      stats.map(async (stat) => [stat, ((await this.statValue(stat).textContent()) ?? '').trim()] as const),
    )
    return Object.fromEntries(entries) as Record<S, string>
  }
}
