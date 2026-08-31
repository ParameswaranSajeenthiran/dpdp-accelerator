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

import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'
import { describe, expect, it } from 'vitest'
import {
  getConsentStateChipColor,
  getConsentStateLabelKey,
  isConsentRevokableState,
} from '../features/my-consents/utils/statusChip'
import { CONSENT_AUTHORIZATION_STATES, CONSENT_STATES, isConsentState } from '../types/consent'

// Translations moved off a compiled TS resource onto runtime-fetched JSON;
// reading the file directly keeps this test independent of any test-only
// i18n wiring.
const commonEn: { consentRegistry: { status: Record<string, string> } } = JSON.parse(
  readFileSync(resolve(import.meta.dirname, '../../public/i18n/en/common.json'), 'utf8'),
)

describe('consent state presentation', () => {
  it('maps consent and authorization states to the expected chip colors', () => {
    expect(getConsentStateChipColor('ACTIVE')).toBe('success')
    expect(getConsentStateChipColor('APPROVED')).toBe('success')
    expect(getConsentStateChipColor('PENDING')).toBe('warning')
    expect(getConsentStateChipColor('REJECTED')).toBe('error')
    expect(getConsentStateChipColor('REVOKED')).toBe('error')
    expect(getConsentStateChipColor('EXPIRED')).toBe('default')
  })

  it('maps states to translation keys that exist in the English resources', () => {
    expect(getConsentStateLabelKey('ACTIVE')).toBe('active')
    expect(getConsentStateLabelKey('PENDING')).toBe('pending')
    expect(getConsentStateLabelKey('REJECTED')).toBe('rejected')
    expect(getConsentStateLabelKey('REVOKED')).toBe('revoked')
    expect(getConsentStateLabelKey('EXPIRED')).toBe('expired')
    expect(getConsentStateLabelKey('APPROVED', 'authorization')).toBe('approved')

    CONSENT_STATES.forEach((state) => {
      expect(commonEn.consentRegistry.status).toHaveProperty(getConsentStateLabelKey(state))
    })

    CONSENT_AUTHORIZATION_STATES.forEach((state) => {
      expect(commonEn.consentRegistry.status).toHaveProperty(
        getConsentStateLabelKey(state, 'authorization'),
      )
    })
  })

  it('limits revocation to active consents', () => {
    expect(isConsentRevokableState('ACTIVE')).toBe(true)
    expect(isConsentRevokableState('PENDING')).toBe(false)
  })

  it('no longer recognises the removed CREATED state', () => {
    expect(CONSENT_STATES).toEqual(['PENDING', 'ACTIVE', 'REJECTED', 'REVOKED', 'EXPIRED'])
    expect(isConsentState('CREATED')).toBe(false)
  })
})
