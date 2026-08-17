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
import { getSubscriptionStatusChipColor } from '../features/events/utils/subscriptionStatusChip'
import commonEn from '../i18n/resources/en/common'
import {
  DELIVERY_MODES,
  PURPOSE_FILTER_MODES,
  SUBSCRIPTION_STATUSES,
  isDeliveryMode,
  isPurposeFilterMode,
  isSubscriptionStatus,
} from '../types/subscription'

describe('Subscription domain helpers & status chips', () => {
  it('maps subscription statuses to appropriate chip colors', () => {
    expect(getSubscriptionStatusChipColor('ACTIVE')).toBe('success')
    expect(getSubscriptionStatusChipColor('DELIVERED')).toBe('success')
    expect(getSubscriptionStatusChipColor('COMPLETED')).toBe('success')
    expect(getSubscriptionStatusChipColor('ACKNOWLEDGED')).toBe('success')
    expect(getSubscriptionStatusChipColor('PENDING')).toBe('warning')
    expect(getSubscriptionStatusChipColor('IN_FLIGHT')).toBe('warning')
    expect(getSubscriptionStatusChipColor('STALE')).toBe('error')
    expect(getSubscriptionStatusChipColor('FAILED')).toBe('error')
    expect(getSubscriptionStatusChipColor('EXPIRED')).toBe('error')
    expect(getSubscriptionStatusChipColor('DELETED')).toBe('default')
    expect(getSubscriptionStatusChipColor('UNKNOWN')).toBe('default')
  })

  it('validates subscription statuses correctly', () => {
    expect(isSubscriptionStatus('ACTIVE')).toBe(true)
    expect(isSubscriptionStatus('PENDING')).toBe(true)
    expect(isSubscriptionStatus('STALE')).toBe(true)
    expect(isSubscriptionStatus('DELETED')).toBe(true)
    expect(isSubscriptionStatus('active')).toBe(true)
    expect(isSubscriptionStatus('INVALID')).toBe(false)
  })

  it('validates delivery modes correctly', () => {
    expect(isDeliveryMode('webhook')).toBe(true)
    expect(isDeliveryMode('poll')).toBe(true)
    expect(isDeliveryMode('WEBHOOK')).toBe(true)
    expect(isDeliveryMode('invalid')).toBe(false)
  })

  it('validates purpose filter modes correctly', () => {
    expect(isPurposeFilterMode('all')).toBe(true)
    expect(isPurposeFilterMode('specific')).toBe(true)
    expect(isPurposeFilterMode('all_except')).toBe(true)
    expect(isPurposeFilterMode('invalid')).toBe(false)
  })

  it('has corresponding English i18n translation keys', () => {
    SUBSCRIPTION_STATUSES.forEach((status) => {
      expect(commonEn.subscriptions.status).toHaveProperty(status.toLowerCase())
    })

    DELIVERY_MODES.forEach((mode) => {
      expect(commonEn.subscriptions.deliveryMode).toHaveProperty(mode.toLowerCase())
    })

    PURPOSE_FILTER_MODES.forEach((filterMode) => {
      expect(commonEn.subscriptions.filterType).toHaveProperty(filterMode)
    })
  })
})
