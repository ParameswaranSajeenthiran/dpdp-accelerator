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

import { test, expect } from '../../fixtures/auth.fixtures'
import type { ComplaintCategory } from '../../clients/ComplaintApiClient'
import { uniqueMarker } from '../../utils/testData'

/**
 * A Data Principal creating, listing, and reading their own complaints via
 * /me/complaints/* - MeComplaintEndpoint.java, backed by ComplaintServiceImpl#createComplaint.
 * Real API calls against a real deployment; see tests/06-complaints-api/README.md for the
 * persona/scope prerequisites and the spec-vs-implementation drift this file's assertions follow.
 */
test.describe('Data Principal complaint lifecycle (API)', () => {
  test('06.01.01 - Creating a complaint returns the created record with a server-derived priority', async ({
    userComplaintApi,
  }) => {
    const categories = await userComplaintApi.getMyCategories()
    expect(categories.ok()).toBe(true)
    const { data: categoryPriorities } = (await categories.json()) as {
      data: { category: string; priority: string }[]
    }
    const dataBreach = categoryPriorities.find((entry) => entry.category === 'DATA_BREACH')
    if (!dataBreach) {
      throw new Error('Expected DATA_BREACH in GET /me/complaints/categories - got: ' + JSON.stringify(categoryPriorities))
    }

    const description = `Automated regression test: ${uniqueMarker('data-breach')}`
    const response = await userComplaintApi.createMyComplaint({ subjectCategory: 'DATA_BREACH', description })
    expect(response.status()).toBe(201)
    const body = await response.json()

    expect(body.subjectCategory).toBe('DATA_BREACH')
    expect(body.description).toBe(description)
    expect(body.status).toBe('OPEN')
    // Priority is server-derived, never client-supplied (complaint-server-API.yaml) - compare
    // against the live mapping rather than hardcoding PriorityMapper's default, since
    // [categoryPriority] in deployment.toml can override it per deployment.
    expect(body.priority).toBe(dataBreach.priority)
    expect(typeof body.id).toBe('string')
    expect(body.id.length).toBeGreaterThan(0)
    // Drift #2 (see README): referenceId is returned even though undocumented in the yaml schema.
    expect(typeof body.referenceId).toBe('string')
    expect(body.referenceId.length).toBeGreaterThan(0)
    expect(body.updatedAt).toBeGreaterThanOrEqual(body.submittedAt)
    expect(body.statutoryDueDate).toBeGreaterThan(body.submittedAt)
  })

  test('06.01.02 - Getting a complaint by id returns the same record, with an empty attachments array', async ({
    userComplaintApi,
  }) => {
    const description = `Automated regression test: ${uniqueMarker('get-by-id')}`
    const created = await userComplaintApi.createMyComplaint({ subjectCategory: 'OTHER', description })
    const { id } = await created.json()

    const response = await userComplaintApi.getMyComplaint(id)
    expect(response.ok()).toBe(true)
    const body = await response.json()
    expect(body.id).toBe(id)
    expect(body.description).toBe(description)
    // Drift #3 (see README): the yaml says ComplaintRecord "does not include attachments", but
    // ComplaintRecordBean always sets the field - empty here since nothing was uploaded.
    expect(body.attachments).toEqual([])
  })

  test('06.01.03 - Getting an unknown complaint id returns 404 CO-4040', async ({ userComplaintApi }) => {
    const response = await userComplaintApi.getMyComplaint('00000000-0000-0000-0000-000000000000')
    expect(response.status()).toBe(404)
    const body = await response.json()
    expect(body.code).toBe('CO-4040')
    expect(typeof body.traceId).toBe('string')
  })

  test('06.01.04 - A newly created complaint appears in the caller\'s own list, sorted newest-first', async ({
    userComplaintApi,
  }) => {
    const description = `Automated regression test: ${uniqueMarker('appears-in-list')}`
    const created = await userComplaintApi.createMyComplaint({ subjectCategory: 'OTHER', description })
    const { id } = await created.json()

    // This environment never resets (see the suite README's Operating principles) and every
    // spec file in this run shares the same "user" persona, so this can never assert "the list
    // has exactly one item" - only that the fresh complaint is findable. sort=-submittedTime
    // guarantees it's at or near the very top regardless of how much history this persona has
    // accumulated across every prior run.
    const listed = await userComplaintApi.listMyComplaints({ limit: 100, sort: '-submittedTime' })
    expect(listed.ok()).toBe(true)
    const { data, metadata } = await listed.json()
    expect(metadata.total).toBeGreaterThan(0)
    expect(data.some((complaint: { id: string }) => complaint.id === id)).toBe(true)
  })

  test('06.01.05 - Categories cover every ComplaintCategory value with a valid priority', async ({
    userComplaintApi,
  }) => {
    const expectedCategories: ComplaintCategory[] = [
      'DATA_BREACH',
      'UNAUTHORIZED_DATA_SHARING',
      'CONSENT_WITHDRAWN_DATA_STILL_USED',
      'PURPOSE_VIOLATION',
      'DATA_ERASURE_NOT_COMPLETED',
      'DATA_CORRECTION_NOT_COMPLETED',
      'CONSENT_LIFECYCLE_ISSUE',
      'DATA_ACCESS_DENIED',
      'EXCESSIVE_DATA_COLLECTION',
      'OTHER',
    ]
    const response = await userComplaintApi.getMyCategories()
    expect(response.ok()).toBe(true)
    const { data } = (await response.json()) as { data: { category: string; priority: string }[] }

    expect(data.map((entry) => entry.category).sort()).toEqual([...expectedCategories].sort())
    for (const entry of data) {
      expect(['CRITICAL', 'HIGH', 'MEDIUM', 'LOW']).toContain(entry.priority)
    }
  })

  test.describe('Validation', () => {
    test('06.01.06 - Missing subjectCategory returns 422 CO-4002', async ({ userComplaintApi }) => {
      const response = await userComplaintApi.createMyComplaint({
        subjectCategory: undefined as unknown as ComplaintCategory,
        description: 'Missing a category on purpose.',
      })
      expect(response.status()).toBe(422)
      expect((await response.json()).code).toBe('CO-4002')
    })

    test('06.01.07 - An unrecognized subjectCategory value returns 422 CO-4002', async ({ userComplaintApi }) => {
      const response = await userComplaintApi.createMyComplaint({
        subjectCategory: 'NOT_A_REAL_CATEGORY' as ComplaintCategory,
        description: 'Category is not one of the enum values.',
      })
      expect(response.status()).toBe(422)
      expect((await response.json()).code).toBe('CO-4002')
    })

    test('06.01.08 - A blank description returns 422 CO-4002', async ({ userComplaintApi }) => {
      const response = await userComplaintApi.createMyComplaint({ subjectCategory: 'OTHER', description: '   ' })
      expect(response.status()).toBe(422)
      expect((await response.json()).code).toBe('CO-4002')
    })

    test('06.01.09 - A description over 5000 characters returns 422 CO-4002', async ({ userComplaintApi }) => {
      const response = await userComplaintApi.createMyComplaint({
        subjectCategory: 'OTHER',
        description: 'x'.repeat(5001),
      })
      expect(response.status()).toBe(422)
      const body = await response.json()
      expect(body.code).toBe('CO-4002')
      expect(body.description).toMatch(/5000/)
    })

    test('06.01.10 - A description of exactly 5000 characters is accepted', async ({ userComplaintApi }) => {
      const description = `${uniqueMarker('max-length')}-${'x'.repeat(4970)}`.slice(0, 5000)
      const response = await userComplaintApi.createMyComplaint({ subjectCategory: 'OTHER', description })
      expect(response.status()).toBe(201)
      expect((await response.json()).description).toBe(description)
    })
  })
})
