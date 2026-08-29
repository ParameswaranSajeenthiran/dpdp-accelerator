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
import { annotateSnapshot, diffConsentSnapshots } from '../utils/consentSnapshotDiff'
import type { ConsentSnapshot } from '../types/consentHistory'

function buildSnapshot(overrides: Partial<ConsentSnapshot> = {}): ConsentSnapshot {
  return {
    state: 'ACTIVE',
    piiPrincipalId: 'nadia.perera@wso2.com',
    language: 'en',
    properties: { region: 'EU' },
    services: [
      {
        service: 'customer-360-portal',
        spDisplayName: 'Customer 360',
        purposes: [
          {
            purpose: 'Service Delivery',
            uuid: 'purpose-1',
            primaryPurpose: true,
            elements: [{ name: 'email', displayName: 'Email Address', consented: true }],
          },
        ],
      },
    ],
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
    expect(diff.purposes).toEqual([
      { service: 'customer-360-portal', purpose: 'Service Delivery', kind: 'added' },
    ])
    expect(diff.elements).toEqual([
      {
        service: 'customer-360-portal',
        purpose: 'Service Delivery',
        elementName: 'email',
        elementDisplayName: 'Email Address',
        kind: 'added',
        consentedAfter: true,
      },
    ])
  })

  it('detects a scalar field change', () => {
    const diff = diffConsentSnapshots(
      buildSnapshot({ state: 'PENDING' }),
      buildSnapshot({ state: 'ACTIVE' }),
    )

    expect(diff.fields).toEqual([{ field: 'state', before: 'PENDING', after: 'ACTIVE' }])
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

  it('detects a purpose added to a service, including its elements', () => {
    const before = buildSnapshot()
    const after = buildSnapshot({
      services: [
        {
          service: 'customer-360-portal',
          spDisplayName: 'Customer 360',
          purposes: [
            ...before.services![0].purposes,
            {
              purpose: 'Marketing',
              uuid: 'purpose-2',
              primaryPurpose: false,
              elements: [{ name: 'phone', displayName: 'Phone Number', consented: true }],
            },
          ],
        },
      ],
    })

    const diff = diffConsentSnapshots(before, after)

    expect(diff.purposes).toEqual([
      { service: 'customer-360-portal', purpose: 'Marketing', kind: 'added' },
    ])
    expect(diff.elements).toEqual([
      {
        service: 'customer-360-portal',
        purpose: 'Marketing',
        elementName: 'phone',
        elementDisplayName: 'Phone Number',
        kind: 'added',
        consentedAfter: true,
      },
    ])
  })

  it('detects a purpose removed from a service, including its elements', () => {
    const before = buildSnapshot()
    const after = buildSnapshot({
      services: [{ service: 'customer-360-portal', spDisplayName: 'Customer 360', purposes: [] }],
    })

    const diff = diffConsentSnapshots(before, after)

    expect(diff.purposes).toEqual([
      { service: 'customer-360-portal', purpose: 'Service Delivery', kind: 'removed' },
    ])
    expect(diff.elements).toEqual([
      {
        service: 'customer-360-portal',
        purpose: 'Service Delivery',
        elementName: 'email',
        elementDisplayName: 'Email Address',
        kind: 'removed',
        consentedBefore: true,
      },
    ])
  })

  it('detects an element added, removed and consent-toggled within the same purpose', () => {
    const before = buildSnapshot({
      services: [
        {
          service: 'customer-360-portal',
          spDisplayName: 'Customer 360',
          purposes: [
            {
              purpose: 'Service Delivery',
              uuid: 'purpose-1',
              primaryPurpose: true,
              elements: [
                { name: 'email', displayName: 'Email Address', consented: true },
                { name: 'phone', displayName: 'Phone Number', consented: true },
              ],
            },
          ],
        },
      ],
    })
    const after = buildSnapshot({
      services: [
        {
          service: 'customer-360-portal',
          spDisplayName: 'Customer 360',
          purposes: [
            {
              purpose: 'Service Delivery',
              uuid: 'purpose-1',
              primaryPurpose: true,
              elements: [
                { name: 'email', displayName: 'Email Address', consented: false },
                { name: 'address', displayName: 'Home Address', consented: true },
              ],
            },
          ],
        },
      ],
    })

    const diff = diffConsentSnapshots(before, after)

    expect(diff.purposes).toEqual([])
    expect(diff.elements).toEqual(
      expect.arrayContaining([
        {
          service: 'customer-360-portal',
          purpose: 'Service Delivery',
          elementName: 'email',
          elementDisplayName: 'Email Address',
          kind: 'changed',
          consentedBefore: true,
          consentedAfter: false,
        },
        {
          service: 'customer-360-portal',
          purpose: 'Service Delivery',
          elementName: 'phone',
          elementDisplayName: 'Phone Number',
          kind: 'removed',
          consentedBefore: true,
        },
        {
          service: 'customer-360-portal',
          purpose: 'Service Delivery',
          elementName: 'address',
          elementDisplayName: 'Home Address',
          kind: 'added',
          consentedAfter: true,
        },
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
  it('tags every current property/purpose/element/authorization as unchanged when nothing changed', () => {
    const snapshot = buildSnapshot()
    const diff = diffConsentSnapshots(snapshot, buildSnapshot())
    const annotated = annotateSnapshot(snapshot, diff)

    expect(annotated.properties).toEqual([{ key: 'region', value: 'EU', kind: 'unchanged' }])
    expect(annotated.purposes).toEqual([
      {
        service: 'customer-360-portal',
        purpose: 'Service Delivery',
        kind: 'unchanged',
        elements: [
          { name: 'email', displayName: 'Email Address', consented: true, kind: 'unchanged' },
        ],
      },
    ])
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
    expect(annotated.purposes[0].kind).toBe('added')
    expect(annotated.purposes[0].elements[0].kind).toBe('added')
    expect(annotated.authorizations[0].kind).toBe('added')
  })

  it('folds a removed purpose back in as a struck-through entry, with its elements', () => {
    const before = buildSnapshot()
    const after = buildSnapshot({
      services: [{ service: 'customer-360-portal', spDisplayName: 'Customer 360', purposes: [] }],
    })
    const diff = diffConsentSnapshots(before, after)
    const annotated = annotateSnapshot(after, diff)

    expect(annotated.purposes).toEqual([
      {
        service: 'customer-360-portal',
        purpose: 'Service Delivery',
        kind: 'removed',
        elements: [
          {
            name: 'email',
            displayName: 'Email Address',
            consented: true,
            consentedBefore: true,
            kind: 'removed',
          },
        ],
      },
    ])
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

  it('marks a scalar field change and leaves the rest unchanged', () => {
    const before = buildSnapshot({ state: 'PENDING' })
    const after = buildSnapshot({ state: 'ACTIVE' })
    const diff = diffConsentSnapshots(before, after)
    const annotated = annotateSnapshot(after, diff)

    const stateField = annotated.fields.find((field) => field.field === 'state')
    expect(stateField).toEqual({
      field: 'state',
      value: 'ACTIVE',
      before: 'PENDING',
      kind: 'changed',
    })
  })
})
