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
 * ConsentFullHistoryDialog.tsx - opened from ConsentDetailPage's "View Full Snapshot History"
 * button. Each entry is an MUI Accordion with a real button as its summary.
 *
 * Summary text is "<action> · <actor>" (a middle dot), not the lifecycle table's "<action> by
 * <actor>" - matching on "by" here finds nothing. `entry()` bridges whatever separator applies.
 */
export class ConsentFullHistoryDialogPage {
  readonly dialog: Locator
  readonly closeButton: Locator
  readonly emptyMessage: Locator
  /** DOM order of every entry's summary, newest-first - see sortHistoryDescending's contract. */
  readonly entrySummaries: Locator

  constructor(page: Page) {
    this.dialog = page.getByRole('dialog').filter({
      has: page.getByRole('heading', { name: 'View Full Snapshot History' }),
    })
    this.closeButton = this.dialog.getByRole('button', { name: 'Close' })
    this.emptyMessage = this.dialog.getByText('No history is recorded for this consent.')
    this.entrySummaries = this.dialog.locator('.MuiAccordionSummary-root')
  }

  /** `action` is the rendered action label (e.g. "Approved", "Consent created"), `actor` the
   *  rendered actionBy (a username, or "System"). */
  entry(action: string, actor: string): Locator {
    return this.dialog.getByRole('button', { name: new RegExp(`${action}.*${actor}`) })
  }

  async expand(action: string, actor: string): Promise<void> {
    await this.entry(action, actor).click()
  }

  /**
   * The whole accordion (summary + details) for one entry, scoping diff assertions to it so they
   * can't match a sibling entry's panel. Uses `hasText`, not `has: this.entry(...)` - chaining a
   * filter-derived locator into another filter's `has:` silently matches nothing here.
   */
  private accordionFor(action: string, actor: string): Locator {
    return this.dialog
      .locator('.MuiAccordion-root')
      .filter({ hasText: new RegExp(`${action}.*${actor}`) })
  }

  initialSnapshotChip(action: string, actor: string): Locator {
    return this.accordionFor(action, actor).getByText('Initial snapshot')
  }

  changedTag(action: string, actor: string): Locator {
    return this.accordionFor(action, actor).getByText('Changed', { exact: true })
  }

  noChangesNote(action: string, actor: string): Locator {
    return this.accordionFor(action, actor).getByText(
      'No changes were recorded in this update.',
    )
  }

  /**
   * The before→after chip pair rendered when a snapshot's `state` changed (e.g. "Active" →
   * "Revoked"). `state` is diffed separately from fields/properties/authorizations, so a
   * state-only change matches neither `changedTag` nor `noChangesNote`.
   */
  stateTransition(action: string, actor: string, from: string, to: string): Locator {
    return this.accordionFor(action, actor).getByText(new RegExp(`${from}.*→.*${to}`))
  }

  /**
   * Either outcome is legitimate - which one depends on product-internal cascade behavior this
   * repo can't decide. Use where the point is only "a real diff rendered", not a specific outcome.
   */
  diffRendered(action: string, actor: string): Locator {
    return this.changedTag(action, actor).or(this.noChangesNote(action, actor))
  }

  async close(): Promise<void> {
    await this.closeButton.click()
  }
}
