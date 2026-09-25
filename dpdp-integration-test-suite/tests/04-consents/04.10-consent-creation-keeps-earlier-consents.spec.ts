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
import type { ConsentApiClient } from '../../clients/ConsentApiClient'
import { seedConsentViaApi, type SeededConsent } from '../../utils/consentSetup'

/**
 * With the product default (`[consent_mgt] revoke_active_consents_on_create = true`), creating a
 * v2 consent revokes every ACTIVE or PENDING consent of the same subject, service and purpose -
 * without firing ConsentManagementListener's revoke hooks (product-is#28405), so the accelerator's
 * status audit and history never record it. The accelerator ships the switch `false`; these tests
 * pin that shipped default. A failure here most likely means the deployed deployment.toml has the
 * switch `true`, or the Identity Server is below U2 update level 17, which ignores the key.
 */
test.describe('Consent creation keeps earlier consents (API)', () => {
  async function createSameConsentAgain(
    adminApi: ConsentApiClient,
    subjectId: string,
    earlier: SeededConsent,
  ): Promise<string> {
    const response = await adminApi.createConsent({
      subjectId,
      serviceId: earlier.serviceId,
      language: 'en',
      purposes: [{ id: earlier.purposeId, elements: [{ id: earlier.elementId }] }],
      state: 'ACTIVE',
    })
    expect(response.status()).toBe(201)
    return ((await response.json()) as { id: string }).id
  }

  /** Filtered server-side by service and purpose, so finding a consent here proves it carries that purpose. */
  async function expectStates(
    adminApi: ConsentApiClient,
    earlier: SeededConsent,
    expected: Record<string, string>,
  ): Promise<void> {
    const response = await adminApi.listAdminConsents({ serviceId: earlier.serviceId, purposeId: earlier.purposeId })
    expect(response.status()).toBe(200)
    const { Consents } = (await response.json()) as { Consents: Array<{ id: string; state: string }> }
    for (const [consentId, state] of Object.entries(expected)) {
      expect(Consents.find((consent) => consent.id === consentId)?.state).toBe(state)
    }
  }

  async function expectNoRevokeInStatusHistory(adminApi: ConsentApiClient, consentId: string): Promise<void> {
    const response = await adminApi.getConsentStatusHistory(consentId)
    expect(response.status()).toBe(200)
    const { statusHistory } = (await response.json()) as { statusHistory: Array<{ currentStatus: string }> }
    expect(statusHistory.some((entry) => entry.currentStatus === 'REVOKED')).toBe(false)
  }

  test('04.10.01 - Creating a consent for the same subject, service and purpose leaves the earlier ACTIVE consent ACTIVE', async ({
    target,
    consentAdminConsentApi,
  }) => {
    const subjectId = target.personas.user.username
    const earlier = await seedConsentViaApi(consentAdminConsentApi, subjectId, 'ACTIVE')

    const laterId = await createSameConsentAgain(consentAdminConsentApi, subjectId, earlier)

    await expectStates(consentAdminConsentApi, earlier, {
      [laterId]: 'ACTIVE',
      [earlier.consentId]: 'ACTIVE',
    })
    await expectNoRevokeInStatusHistory(consentAdminConsentApi, earlier.consentId)
  })

  test('04.10.02 - Creating a consent for the same subject, service and purpose leaves the earlier PENDING consent PENDING', async ({
    target,
    consentAdminConsentApi,
  }) => {
    const subjectId = target.personas.user.username
    const earlier = await seedConsentViaApi(consentAdminConsentApi, subjectId, 'PENDING')

    const laterId = await createSameConsentAgain(consentAdminConsentApi, subjectId, earlier)

    await expectStates(consentAdminConsentApi, earlier, {
      [laterId]: 'ACTIVE',
      [earlier.consentId]: 'PENDING',
    })
    await expectNoRevokeInStatusHistory(consentAdminConsentApi, earlier.consentId)
  })
})
