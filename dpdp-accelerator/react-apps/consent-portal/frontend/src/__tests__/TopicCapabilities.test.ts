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
import { supportsConsentPurposeFilter } from '../features/events/utils/topicCapabilities'

describe('topic capabilities', () => {
  it.each(['user.account.delete', 'user.data.change'])(
    'forces the all-purpose filter for %s',
    (topic) => {
      expect(supportsConsentPurposeFilter(topic)).toBe(false)
    },
  )

  it('allows consent-purpose filtering for other topics', () => {
    expect(supportsConsentPurposeFilter('consent.status.update')).toBe(true)
  })

  it('normalizes topic names before checking capabilities', () => {
    expect(supportsConsentPurposeFilter(' USER.ACCOUNT.DELETE ')).toBe(false)
  })
})
