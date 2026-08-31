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
 * button. Each history entry is an independent MUI Accordion; its summary is a real button (see
 * the frontend's own ConsentLifecycleSection.test.tsx, which locates the same way).
 *
 * The summary text is "<action> · <actor>" (HistoryEntryAccordion composes it with a literal
 * middle dot) - NOT "<action> by <actor>", which is only the *lifecycle table*'s own phrasing
 * (ConsentLifecycleSection.describeEntry's i18n template). Confirmed live: matching on "by" here
 * finds nothing and hangs a `.click()` for the full test timeout. `entry()` takes the action label
 * and actor separately and bridges whatever sits between them, so callers never need to know
 * which separator applies where.
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
   * The whole accordion (summary + details) for one entry, so a diff assertion can't match a
   * sibling entry's panel that happens to render the same tag text.
   *
   * `hasText`, not `has: this.entry(...)` - confirmed live that `.filter({has: locator})` returns
   * zero matches here even though both the base locator and the `has` locator individually match
   * something. `this.entry()` is chained off `this.dialog`, which is itself a
   * `.filter({has: ...})`-derived locator - using a locator built on top of one filter as the
   * `has:` predicate of a second, outer filter silently fails to match. `hasText` takes a plain
   * string/regex instead of a locator, sidestepping the problem entirely.
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
   * The before→after chip pair `ConsentSnapshotView`'s `StateSummary` renders when a snapshot's
   * `state` itself changed (e.g. "Active" → "Revoked"). `state` is diffed separately from
   * fields/properties/authorizations (see consentSnapshotDiff.ts's own comment on why) and is
   * never routed through the shared `ChangeTag`/`noChangesNote` those three use - so a diff whose
   * *only* change is the state transition matches neither `changedTag` nor `noChangesNote`.
   */
  stateTransition(action: string, actor: string, from: string, to: string): Locator {
    return this.accordionFor(action, actor).getByText(new RegExp(`${from}.*→.*${to}`))
  }

  /**
   * Either outcome is a legitimate diff result - which one depends on whether the product's own
   * consent-mgt-core revoke operation also cascades onto per-authorizer records, which isn't
   * decidable from this repo (that logic lives in a vendored jar, not here). Use this instead of
   * `changedTag`/`noChangesNote` wherever the point is only "the diff rendered something, not the
   * needsMoreHistory fallback" rather than a specific outcome.
   */
  diffRendered(action: string, actor: string): Locator {
    return this.changedTag(action, actor).or(this.noChangesNote(action, actor))
  }

  async close(): Promise<void> {
    await this.closeButton.click()
  }
}
