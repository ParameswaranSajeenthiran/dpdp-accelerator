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

import { expect } from '@playwright/test'
import type { DashboardPage, DashboardStat } from '../pages/DashboardPage'

/** Generous - each card is its own request, and several run per page load under a busy server. */
const COUNTS_TIMEOUT_MS = 20_000

/**
 * Asserts every listed card's count at once, retrying past the "-" loading placeholder. Compares
 * the whole set in one assertion so a failure shows every card's actual value, not just the first
 * mismatch.
 */
export async function expectDashboardCounts(
  dashboard: DashboardPage,
  expected: Partial<Record<DashboardStat, number>>,
): Promise<void> {
  const stats = Object.keys(expected) as DashboardStat[]
  const want = Object.fromEntries(stats.map((stat) => [stat, String(expected[stat])]))
  await expect.poll(() => dashboard.readStats(stats), { timeout: COUNTS_TIMEOUT_MS }).toEqual(want)
}

/**
 * For a shared tenant's counts, which no test can predict: each card shows a real value - a
 * count, or "100+" past the cap - once loaded, rather than staying on "-" or rendering nothing.
 */
export async function expectDashboardCountsLoaded(
  dashboard: DashboardPage,
  stats: readonly DashboardStat[],
): Promise<void> {
  const want = Object.fromEntries(stats.map((stat) => [stat, expect.stringMatching(/^\d+\+?$/)]))
  await expect.poll(() => dashboard.readStats(stats), { timeout: COUNTS_TIMEOUT_MS }).toEqual(want)
}
