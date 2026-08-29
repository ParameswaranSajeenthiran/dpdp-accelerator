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

import type {
  ConsentSnapshot,
  ConsentSnapshotAuthorization,
  ConsentSnapshotElement,
  ConsentSnapshotPurpose,
} from '../types/consentHistory'

export type ConsentSnapshotChangeKind = 'added' | 'removed' | 'changed'

export interface ConsentSnapshotFieldChange {
  field: 'state' | 'language' | 'expiryTime' | 'piiPrincipalId'
  before?: string | number
  after?: string | number
}

export interface ConsentSnapshotPropertyChange {
  key: string
  kind: ConsentSnapshotChangeKind
  before?: string
  after?: string
}

export interface ConsentSnapshotPurposeChange {
  service: string
  purpose: string
  kind: 'added' | 'removed'
}

export interface ConsentSnapshotElementChange {
  service: string
  purpose: string
  elementName: string
  elementDisplayName: string
  kind: ConsentSnapshotChangeKind
  consentedBefore?: boolean
  consentedAfter?: boolean
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
  purposes: ConsentSnapshotPurposeChange[]
  elements: ConsentSnapshotElementChange[]
  authorizations: ConsentSnapshotAuthorizationChange[]
}

function purposeKey(service: string, uuid: string): string {
  return `${service}::${uuid}`
}

function elementKey(service: string, purposeUuid: string, elementName: string): string {
  return `${service}::${purposeUuid}::${elementName}`
}

function diffFields(
  before: ConsentSnapshot | undefined,
  after: ConsentSnapshot,
): ConsentSnapshotFieldChange[] {
  const candidates: ConsentSnapshotFieldChange['field'][] = [
    'state',
    'piiPrincipalId',
    'language',
    'expiryTime',
  ]

  return candidates
    .map((field) => ({ field, before: before?.[field], after: after[field] }))
    .filter(({ before: beforeValue, after: afterValue }) => beforeValue !== afterValue)
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

interface IndexedPurpose {
  service: string
  purpose: ConsentSnapshotPurpose
}

/** `service::purposeUuid` -> the purpose, flattened across every service. */
function indexPurposes(snapshot: ConsentSnapshot | undefined): Map<string, IndexedPurpose> {
  const index = new Map<string, IndexedPurpose>()

  snapshot?.services?.forEach((service) => {
    service.purposes.forEach((purpose) => {
      index.set(purposeKey(service.service, purpose.uuid), { service: service.service, purpose })
    })
  })

  return index
}

function diffPurposesAndElements(
  before: ConsentSnapshot | undefined,
  after: ConsentSnapshot,
): { purposes: ConsentSnapshotPurposeChange[]; elements: ConsentSnapshotElementChange[] } {
  const beforeIndex = indexPurposes(before)
  const afterIndex = indexPurposes(after)
  const purposeKeys = new Set([...beforeIndex.keys(), ...afterIndex.keys()])

  const purposes: ConsentSnapshotPurposeChange[] = []
  const elements: ConsentSnapshotElementChange[] = []

  purposeKeys.forEach((key) => {
    const beforeEntry = beforeIndex.get(key)
    const afterEntry = afterIndex.get(key)

    if (!afterEntry && beforeEntry) {
      purposes.push({
        service: beforeEntry.service,
        purpose: beforeEntry.purpose.purpose,
        kind: 'removed',
      })
      beforeEntry.purpose.elements.forEach((element) => {
        elements.push({
          service: beforeEntry.service,
          purpose: beforeEntry.purpose.purpose,
          elementName: element.name,
          elementDisplayName: element.displayName,
          kind: 'removed',
          consentedBefore: element.consented,
        })
      })
      return
    }

    if (afterEntry && !beforeEntry) {
      purposes.push({
        service: afterEntry.service,
        purpose: afterEntry.purpose.purpose,
        kind: 'added',
      })
      afterEntry.purpose.elements.forEach((element) => {
        elements.push({
          service: afterEntry.service,
          purpose: afterEntry.purpose.purpose,
          elementName: element.name,
          elementDisplayName: element.displayName,
          kind: 'added',
          consentedAfter: element.consented,
        })
      })
      return
    }

    if (!afterEntry || !beforeEntry) {
      return
    }

    const beforeElements = new Map(
      beforeEntry.purpose.elements.map((element) => [
        elementKey(beforeEntry.service, beforeEntry.purpose.uuid, element.name),
        element,
      ]),
    )
    const afterElements = new Map(
      afterEntry.purpose.elements.map((element) => [
        elementKey(afterEntry.service, afterEntry.purpose.uuid, element.name),
        element,
      ]),
    )
    const elementNameKeys = new Set([...beforeElements.keys(), ...afterElements.keys()])

    elementNameKeys.forEach((elementNameKey) => {
      const beforeElement = beforeElements.get(elementNameKey)
      const afterElement = afterElements.get(elementNameKey)

      if (beforeElement && !afterElement) {
        elements.push({
          service: beforeEntry.service,
          purpose: beforeEntry.purpose.purpose,
          elementName: beforeElement.name,
          elementDisplayName: beforeElement.displayName,
          kind: 'removed',
          consentedBefore: beforeElement.consented,
        })
        return
      }

      if (afterElement && !beforeElement) {
        elements.push({
          service: afterEntry.service,
          purpose: afterEntry.purpose.purpose,
          elementName: afterElement.name,
          elementDisplayName: afterElement.displayName,
          kind: 'added',
          consentedAfter: afterElement.consented,
        })
        return
      }

      if (beforeElement && afterElement && beforeElement.consented !== afterElement.consented) {
        elements.push({
          service: afterEntry.service,
          purpose: afterEntry.purpose.purpose,
          elementName: afterElement.name,
          elementDisplayName: afterElement.displayName,
          kind: 'changed',
          consentedBefore: beforeElement.consented,
          consentedAfter: afterElement.consented,
        })
      }
    })
  })

  return { purposes, elements }
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
 * Diffs one history entry's snapshot against the chronologically previous one.
 *
 * `before` is undefined for the CREATE entry - there is nothing earlier to compare against, so
 * every purpose/element/property in `after` reports as "added" rather than the caller needing a
 * separate baseline-rendering code path.
 */
export function diffConsentSnapshots(
  before: ConsentSnapshot | undefined,
  after: ConsentSnapshot,
): ConsentSnapshotDiff {
  const fields = diffFields(before, after)
  const properties = diffProperties(before?.properties, after.properties)
  const { purposes, elements } = diffPurposesAndElements(before, after)
  const authorizations = diffAuthorizations(before?.authorizations, after.authorizations)

  return {
    isInitial: before === undefined,
    hasChanges:
      fields.length > 0 ||
      properties.length > 0 ||
      purposes.length > 0 ||
      elements.length > 0 ||
      authorizations.length > 0,
    fields,
    properties,
    purposes,
    elements,
    authorizations,
  }
}

/**
 * Renders the record as it actually looks (like `ConsentPropertiesSection` /
 * `ConsentPurposesSection` / `ConsentAuthorizationsSection` elsewhere in the app), annotated with
 * what changed, rather than a separate list of change lines: unchanged content renders plain,
 * added content is tagged `added`, changed content carries its `before` value alongside the
 * current one, and removed content - present in the previous snapshot but not this one - is
 * folded back in so it stays visible instead of disappearing.
 */
export type AnnotatedChangeKind = 'unchanged' | 'added' | 'removed' | 'changed'

export interface AnnotatedField {
  field: ConsentSnapshotFieldChange['field']
  value?: string | number
  before?: string | number
  kind: AnnotatedChangeKind
}

export interface AnnotatedProperty {
  key: string
  value?: string
  before?: string
  kind: AnnotatedChangeKind
}

export interface AnnotatedElement {
  name: string
  displayName: string
  consented: boolean
  consentedBefore?: boolean
  kind: AnnotatedChangeKind
}

export interface AnnotatedPurpose {
  service: string
  purpose: string
  kind: AnnotatedChangeKind
  elements: AnnotatedElement[]
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
  purposes: AnnotatedPurpose[]
  authorizations: AnnotatedAuthorization[]
}

const ANNOTATED_FIELD_NAMES: ConsentSnapshotFieldChange['field'][] = [
  'state',
  'piiPrincipalId',
  'language',
  'expiryTime',
]

function annotateFields(snapshot: ConsentSnapshot, diff: ConsentSnapshotDiff): AnnotatedField[] {
  const changeByField = new Map(diff.fields.map((change) => [change.field, change]))

  return ANNOTATED_FIELD_NAMES.map((field): AnnotatedField => {
    const change = changeByField.get(field)
    return {
      field,
      value: snapshot[field],
      before: change?.before,
      kind: change ? 'changed' : 'unchanged',
    }
  }).filter((field) => field.value !== undefined || field.kind === 'changed')
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

function annotateElement(
  element: ConsentSnapshotElement,
  change: ConsentSnapshotElementChange | undefined,
): AnnotatedElement {
  return {
    name: element.name,
    displayName: element.displayName,
    consented: element.consented,
    consentedBefore: change?.consentedBefore,
    kind: change?.kind ?? 'unchanged',
  }
}

function purposeNameKey(service: string, purpose: string): string {
  return `${service}::${purpose}`
}

function annotatePurposes(
  snapshot: ConsentSnapshot,
  diff: ConsentSnapshotDiff,
): AnnotatedPurpose[] {
  const purposeKindByKey = new Map(
    diff.purposes.map((change) => [purposeNameKey(change.service, change.purpose), change.kind]),
  )
  const elementChangesByPurposeKey = new Map<string, ConsentSnapshotElementChange[]>()
  diff.elements.forEach((change) => {
    const key = purposeNameKey(change.service, change.purpose)
    const existing = elementChangesByPurposeKey.get(key)
    if (existing) {
      existing.push(change)
    } else {
      elementChangesByPurposeKey.set(key, [change])
    }
  })

  const groups: AnnotatedPurpose[] = []
  const seenKeys = new Set<string>()

  snapshot.services?.forEach((service) => {
    service.purposes.forEach((purpose) => {
      const key = purposeNameKey(service.service, purpose.purpose)
      seenKeys.add(key)
      const elementChangeByName = new Map(
        (elementChangesByPurposeKey.get(key) ?? []).map((change) => [change.elementName, change]),
      )
      groups.push({
        service: service.service,
        purpose: purpose.purpose,
        kind: purposeKindByKey.get(key) ?? 'unchanged',
        elements: purpose.elements.map((element) =>
          annotateElement(element, elementChangeByName.get(element.name)),
        ),
      })
    })
  })

  diff.purposes
    .filter(
      (change) =>
        change.kind === 'removed' && !seenKeys.has(purposeNameKey(change.service, change.purpose)),
    )
    .forEach((change) => {
      const key = purposeNameKey(change.service, change.purpose)
      const elements = (elementChangesByPurposeKey.get(key) ?? []).map((elementChange) => ({
        name: elementChange.elementName,
        displayName: elementChange.elementDisplayName,
        consented: elementChange.consentedBefore ?? false,
        consentedBefore: elementChange.consentedBefore,
        kind: 'removed' as const,
      }))
      groups.push({ service: change.service, purpose: change.purpose, kind: 'removed', elements })
    })

  return groups
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
    purposes: annotatePurposes(snapshot, diff),
    authorizations: annotateAuthorizations(snapshot, diff),
  }
}
