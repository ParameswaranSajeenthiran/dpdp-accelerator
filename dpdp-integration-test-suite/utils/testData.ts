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

// This suite runs against a real, persistent environment (no per-test tenant reset), so every
// scenario that creates a record stamps a unique marker into its name and asserts by that
// marker or by the server-issued ID - never by "the list is empty" or "there's exactly one
// record", both of which would be false against an environment with prior runs' data still in it.
export function uniqueMarker(label: string): string {
  return `${label}-${Date.now()}-${Math.random().toString(36).slice(2, 8)}`
}

// Catalog-management/lifecycle tests create real Purposes/Elements/Consents through the admin API as setup for
// what the UI is actually being tested on (see tests/plan.md notes on why
// Purpose/Element authoring itself has no UI to drive) - unique names keep those records
// distinguishable from whatever prior runs left in the shared environment.
export function uniquePurposeName(): string {
  return uniqueMarker('purpose')
}

export function uniqueElementName(): string {
  return uniqueMarker('element')
}

export function uniqueServiceId(): string {
  return uniqueMarker('service')
}
