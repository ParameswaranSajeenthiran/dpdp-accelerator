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

import { describe, expect, it } from 'vitest'
import type { ConsentSnapshot } from '../types/consentHistory'
import { annotateSnapshot, diffConsentSnapshots } from '../utils/consentSnapshotDiff'

function buildSnapshot(overrides: Partial<ConsentSnapshot> = {}): ConsentSnapshot {
  return {
    expiryTime: 1_700_000_000_000,
    properties: { region: 'EU' },
    authorizations: [
      { userId: 'admin.reviewer@wso2.com', type: 'ADMIN', status: 'APPROVED', updatedTime: 1 },
    ],
    ...overrides,
  }
}

describe('diffConsentSnapshots', () => {
  it('reports no changes when the two snapshots are identical', () => {
    const snapshot = buildSnapshot()
    const diff = diffConsentSnapshots(snapshot, buildSnapshot())

    expect(diff.hasChanges).toBe(false)
    expect(diff.isInitial).toBe(false)
  })

  it('treats a missing "before" as the initial snapshot, reporting everything as added', () => {
    const diff = diffConsentSnapshots(undefined, buildSnapshot())

    expect(diff.isInitial).toBe(true)
    expect(diff.hasChanges).toBe(true)
    expect(diff.properties).toEqual([{ key: 'region', kind: 'added', after: 'EU' }])
  })

  it('detects an expiryTime change', () => {
    const diff = diffConsentSnapshots(
      buildSnapshot({ expiryTime: 1_700_000_000_000 }),
      buildSnapshot({ expiryTime: 1_800_000_000_000 }),
    )

    expect(diff.fields).toEqual([
      { field: 'expiryTime', before: 1_700_000_000_000, after: 1_800_000_000_000 },
    ])
  })

  it('detects properties added, removed and changed', () => {
    const before = buildSnapshot({ properties: { region: 'EU', dataCategory: 'financial' } })
    const after = buildSnapshot({ properties: { region: 'APAC', collectionMethod: 'web-form' } })

    const diff = diffConsentSnapshots(before, after)

    expect(diff.properties).toEqual(
      expect.arrayContaining([
        { key: 'region', kind: 'changed', before: 'EU', after: 'APAC' },
        { key: 'dataCategory', kind: 'removed', before: 'financial' },
        { key: 'collectionMethod', kind: 'added', after: 'web-form' },
      ]),
    )
  })

  it('detects an authorization added, removed and changed', () => {
    const before = buildSnapshot({
      authorizations: [
        { userId: 'admin.reviewer@wso2.com', type: 'ADMIN', status: 'APPROVED', updatedTime: 1 },
      ],
    })
    const after = buildSnapshot({
      authorizations: [
        { userId: 'admin.reviewer@wso2.com', type: 'ADMIN', status: 'REVOKED', updatedTime: 2 },
        { userId: 'second.reviewer@wso2.com', type: 'ADMIN', status: 'APPROVED', updatedTime: 3 },
      ],
    })

    const diff = diffConsentSnapshots(before, after)

    expect(diff.authorizations).toEqual(
      expect.arrayContaining([
        {
          userId: 'admin.reviewer@wso2.com',
          kind: 'changed',
          before: {
            userId: 'admin.reviewer@wso2.com',
            type: 'ADMIN',
            status: 'APPROVED',
            updatedTime: 1,
          },
          after: {
            userId: 'admin.reviewer@wso2.com',
            type: 'ADMIN',
            status: 'REVOKED',
            updatedTime: 2,
          },
        },
        {
          userId: 'second.reviewer@wso2.com',
          kind: 'added',
          after: {
            userId: 'second.reviewer@wso2.com',
            type: 'ADMIN',
            status: 'APPROVED',
            updatedTime: 3,
          },
        },
      ]),
    )
  })
})

describe('annotateSnapshot', () => {
  it('tags every current property/authorization as unchanged when nothing changed', () => {
    const snapshot = buildSnapshot()
    const diff = diffConsentSnapshots(snapshot, buildSnapshot())
    const annotated = annotateSnapshot(snapshot, diff)

    expect(annotated.properties).toEqual([{ key: 'region', value: 'EU', kind: 'unchanged' }])
    expect(annotated.authorizations).toEqual([
      {
        userId: 'admin.reviewer@wso2.com',
        type: 'ADMIN',
        status: 'APPROVED',
        updatedTime: 1,
        kind: 'unchanged',
      },
    ])
  })

  it('tags everything as added for the initial (no "before") snapshot', () => {
    const snapshot = buildSnapshot()
    const diff = diffConsentSnapshots(undefined, snapshot)
    const annotated = annotateSnapshot(snapshot, diff)

    expect(annotated.properties).toEqual([{ key: 'region', value: 'EU', kind: 'added' }])
    expect(annotated.authorizations[0].kind).toBe('added')
  })

  it('folds a removed authorization back in with its last known state', () => {
    const before = buildSnapshot({
      authorizations: [
        { userId: 'admin.reviewer@wso2.com', type: 'ADMIN', status: 'APPROVED', updatedTime: 1 },
      ],
    })
    const after = buildSnapshot({ authorizations: [] })
    const diff = diffConsentSnapshots(before, after)
    const annotated = annotateSnapshot(after, diff)

    expect(annotated.authorizations).toEqual([
      {
        userId: 'admin.reviewer@wso2.com',
        type: 'ADMIN',
        status: 'APPROVED',
        updatedTime: 1,
        kind: 'removed',
        before: {
          userId: 'admin.reviewer@wso2.com',
          type: 'ADMIN',
          status: 'APPROVED',
          updatedTime: 1,
        },
      },
    ])
  })

  it('carries a removed property alongside its last known value', () => {
    const before = buildSnapshot({ properties: { region: 'EU', dataCategory: 'financial' } })
    const after = buildSnapshot({ properties: { region: 'EU' } })
    const diff = diffConsentSnapshots(before, after)
    const annotated = annotateSnapshot(after, diff)

    expect(annotated.properties).toEqual(
      expect.arrayContaining([
        { key: 'region', value: 'EU', kind: 'unchanged' },
        { key: 'dataCategory', before: 'financial', kind: 'removed' },
      ]),
    )
  })

  it('marks an expiryTime change and leaves the rest unchanged', () => {
    const before = buildSnapshot({ expiryTime: 1_700_000_000_000 })
    const after = buildSnapshot({ expiryTime: 1_800_000_000_000 })
    const diff = diffConsentSnapshots(before, after)
    const annotated = annotateSnapshot(after, diff)

    expect(annotated.fields).toEqual([
      {
        field: 'expiryTime',
        value: 1_800_000_000_000,
        before: 1_700_000_000_000,
        kind: 'changed',
      },
    ])
  })
})
