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

import type { ConsentSnapshot, ConsentSnapshotAuthorization } from '../types/consentHistory'

export type ConsentSnapshotChangeKind = 'added' | 'removed' | 'changed'

export interface ConsentSnapshotFieldChange {
  field: 'expiryTime'
  before?: number
  after?: number
}

export interface ConsentSnapshotPropertyChange {
  key: string
  kind: ConsentSnapshotChangeKind
  before?: string
  after?: string
}

export interface ConsentSnapshotAuthorizationChange {
  userId: string
  kind: ConsentSnapshotChangeKind
  before?: ConsentSnapshotAuthorization
  after?: ConsentSnapshotAuthorization
}

export interface ConsentSnapshotDiff {
  /** True for the very first entry a consent ever has - there is no earlier snapshot to diff. */
  isInitial: boolean
  hasChanges: boolean
  fields: ConsentSnapshotFieldChange[]
  properties: ConsentSnapshotPropertyChange[]
  authorizations: ConsentSnapshotAuthorizationChange[]
}

function diffFields(
  before: ConsentSnapshot | undefined,
  after: ConsentSnapshot,
): ConsentSnapshotFieldChange[] {
  return before?.expiryTime !== after.expiryTime
    ? [{ field: 'expiryTime', before: before?.expiryTime, after: after.expiryTime }]
    : []
}

function diffProperties(
  before: Record<string, string> | undefined,
  after: Record<string, string> | undefined,
): ConsentSnapshotPropertyChange[] {
  const beforeEntries = before ?? {}
  const afterEntries = after ?? {}
  const keys = new Set([...Object.keys(beforeEntries), ...Object.keys(afterEntries)])

  return [...keys]
    .map((key): ConsentSnapshotPropertyChange | undefined => {
      const beforeValue = beforeEntries[key]
      const afterValue = afterEntries[key]

      if (beforeValue === afterValue) {
        return undefined
      }
      if (beforeValue === undefined) {
        return { key, kind: 'added', after: afterValue }
      }
      if (afterValue === undefined) {
        return { key, kind: 'removed', before: beforeValue }
      }
      return { key, kind: 'changed', before: beforeValue, after: afterValue }
    })
    .filter((change): change is ConsentSnapshotPropertyChange => change !== undefined)
}

function diffAuthorizations(
  before: ConsentSnapshotAuthorization[] | undefined,
  after: ConsentSnapshotAuthorization[] | undefined,
): ConsentSnapshotAuthorizationChange[] {
  const beforeByUser = new Map(
    (before ?? []).map((authorization) => [authorization.userId, authorization]),
  )
  const afterByUser = new Map(
    (after ?? []).map((authorization) => [authorization.userId, authorization]),
  )
  const userIds = new Set([...beforeByUser.keys(), ...afterByUser.keys()])

  return [...userIds]
    .map((userId): ConsentSnapshotAuthorizationChange | undefined => {
      const beforeAuthorization = beforeByUser.get(userId)
      const afterAuthorization = afterByUser.get(userId)

      if (beforeAuthorization && !afterAuthorization) {
        return { userId, kind: 'removed', before: beforeAuthorization }
      }
      if (afterAuthorization && !beforeAuthorization) {
        return { userId, kind: 'added', after: afterAuthorization }
      }
      if (
        beforeAuthorization &&
        afterAuthorization &&
        (beforeAuthorization.status !== afterAuthorization.status ||
          beforeAuthorization.updatedTime !== afterAuthorization.updatedTime)
      ) {
        return { userId, kind: 'changed', before: beforeAuthorization, after: afterAuthorization }
      }
      return undefined
    })
    .filter((change): change is ConsentSnapshotAuthorizationChange => change !== undefined)
}

/**
 * Diffs one history entry's snapshot against the chronologically previous one, restricted to
 * `expiryTime`, `properties` and `authorizations` - the only fields `ReceiptUpdateInput` (the
 * model backing the Identity Server's own consent update path) can actually change. Diffing
 * `state`/`piiPrincipalId`/`language`/`services` would only ever show noise: those fields never
 * change via update, and state transitions are already shown by the lifecycle table's own
 * per-entry status dot.
 *
 * `before` is undefined for the CREATE entry - there is nothing earlier to compare against, so
 * every property/authorization in `after` reports as "added" rather than the caller needing a
 * separate baseline-rendering code path.
 */
export function diffConsentSnapshots(
  before: ConsentSnapshot | undefined,
  after: ConsentSnapshot,
): ConsentSnapshotDiff {
  const fields = diffFields(before, after)
  const properties = diffProperties(before?.properties, after.properties)
  const authorizations = diffAuthorizations(before?.authorizations, after.authorizations)

  return {
    isInitial: before === undefined,
    hasChanges: fields.length > 0 || properties.length > 0 || authorizations.length > 0,
    fields,
    properties,
    authorizations,
  }
}

/**
 * Renders the record as it actually looks (like `ConsentPropertiesSection` /
 * `ConsentAuthorizationsSection` elsewhere in the app), annotated with what changed, rather than
 * a separate list of change lines: unchanged content renders plain, added content is tagged
 * `added`, changed content carries its `before` value alongside the current one, and removed
 * content - present in the previous snapshot but not this one - is folded back in so it stays
 * visible instead of disappearing.
 */
export type AnnotatedChangeKind = 'unchanged' | 'added' | 'removed' | 'changed'

export interface AnnotatedField {
  field: ConsentSnapshotFieldChange['field']
  value?: number
  before?: number
  kind: AnnotatedChangeKind
}

export interface AnnotatedProperty {
  key: string
  value?: string
  before?: string
  kind: AnnotatedChangeKind
}

export interface AnnotatedAuthorization {
  userId: string
  type: string
  status: string
  updatedTime: number
  before?: ConsentSnapshotAuthorization
  kind: AnnotatedChangeKind
}

export interface AnnotatedSnapshot {
  fields: AnnotatedField[]
  properties: AnnotatedProperty[]
  authorizations: AnnotatedAuthorization[]
}

function annotateFields(snapshot: ConsentSnapshot, diff: ConsentSnapshotDiff): AnnotatedField[] {
  const change = diff.fields.find((fieldChange) => fieldChange.field === 'expiryTime')
  if (snapshot.expiryTime === undefined && !change) {
    return []
  }
  return [
    {
      field: 'expiryTime',
      value: snapshot.expiryTime,
      before: change?.before,
      kind: change ? 'changed' : 'unchanged',
    },
  ]
}

function annotateProperties(
  snapshot: ConsentSnapshot,
  diff: ConsentSnapshotDiff,
): AnnotatedProperty[] {
  const changeByKey = new Map(diff.properties.map((change) => [change.key, change]))
  const rows: AnnotatedProperty[] = Object.entries(snapshot.properties ?? {}).map(
    ([key, value]) => {
      const change = changeByKey.get(key)
      return { key, value, before: change?.before, kind: change?.kind ?? 'unchanged' }
    },
  )

  diff.properties
    .filter((change) => change.kind === 'removed')
    .forEach((change) => rows.push({ key: change.key, before: change.before, kind: 'removed' }))

  return rows
}

function annotateAuthorizations(
  snapshot: ConsentSnapshot,
  diff: ConsentSnapshotDiff,
): AnnotatedAuthorization[] {
  const changeByUser = new Map(diff.authorizations.map((change) => [change.userId, change]))
  const rows: AnnotatedAuthorization[] = (snapshot.authorizations ?? []).map((authorization) => {
    const change = changeByUser.get(authorization.userId)
    return { ...authorization, before: change?.before, kind: change?.kind ?? 'unchanged' }
  })

  diff.authorizations
    .filter((change) => change.kind === 'removed' && change.before)
    .forEach((change) => {
      const before = change.before as ConsentSnapshotAuthorization
      rows.push({ ...before, before, kind: 'removed' })
    })

  return rows
}

export function annotateSnapshot(
  snapshot: ConsentSnapshot,
  diff: ConsentSnapshotDiff,
): AnnotatedSnapshot {
  return {
    fields: annotateFields(snapshot, diff),
    properties: annotateProperties(snapshot, diff),
    authorizations: annotateAuthorizations(snapshot, diff),
  }
}
