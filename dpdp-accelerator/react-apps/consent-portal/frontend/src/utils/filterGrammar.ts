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
 * Quotes and escapes a value for the Identity Server's SCIM-style `filter`
 * query parameter, shared by the Elements, Purposes and Consents list APIs.
 * Quoting keeps a value containing spaces or quotes as a single filter
 * token rather than letting it be parsed as separate filter grammar.
 */
export function escapeFilterValue(value: string): string {
  return value.replace(/\\/g, '\\\\').replace(/"/g, '\\"')
}

/** Joins non-empty filter clauses with the grammar's `and` operator. */
export function combineFilters(...clauses: (string | undefined)[]): string | undefined {
  const nonEmpty = clauses.filter((clause): clause is string => Boolean(clause))
  return nonEmpty.length > 0 ? nonEmpty.join(' and ') : undefined
}

/**
 * Builds a `timestamp ge/le <epoch-ms>` clause (or both, joined with `and`)
 * for the consent creation time. Either bound may be omitted.
 */
export function buildTimestampFilter(
  afterEpochMillis?: number,
  beforeEpochMillis?: number,
): string | undefined {
  return combineFilters(
    afterEpochMillis !== undefined ? `timestamp ge ${String(afterEpochMillis)}` : undefined,
    beforeEpochMillis !== undefined ? `timestamp le ${String(beforeEpochMillis)}` : undefined,
  )
}
