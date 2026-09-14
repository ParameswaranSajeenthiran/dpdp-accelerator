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

/**
 * Merges one level deeper than a spread: `override` can name individual nested settings without
 * having to restate every sibling key of every group it touches. Shared by utils/config.ts (for
 * e2e-config.local.json) and utils/runState.ts (for .e2e-run-state.json) - same shape, same need.
 */
export function deepMerge(base: Record<string, unknown>, override: Record<string, unknown>): Record<string, unknown> {
  const merged: Record<string, unknown> = { ...base }
  for (const [key, value] of Object.entries(override)) {
    const existing = merged[key]
    const bothPlainObjects =
      existing !== null &&
      value !== null &&
      typeof existing === 'object' &&
      typeof value === 'object' &&
      !Array.isArray(existing) &&
      !Array.isArray(value)
    merged[key] = bothPlainObjects
      ? deepMerge(existing as Record<string, unknown>, value as Record<string, unknown>)
      : value
  }
  return merged
}
