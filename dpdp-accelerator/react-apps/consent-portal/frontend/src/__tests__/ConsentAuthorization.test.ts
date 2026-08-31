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
import {
  effectiveActionState,
  isApprovableByCurrentUser,
  isCurrentUserInvolved,
  isRejectableByCurrentUser,
} from '../features/my-consents/utils/consentAuthorization'
import type { ConsentAuthorization } from '../types/consent'

const AUTHORIZERS: ConsentAuthorization[] = [
  { userId: 'bob', state: 'APPROVED', updatedTime: 1 },
  { userId: 'carol', state: 'REJECTED', updatedTime: 2 },
]

describe('isCurrentUserInvolved', () => {
  it('is true for the subject', () => {
    expect(isCurrentUserInvolved('alice', AUTHORIZERS, 'alice')).toBe(true)
  })

  it('is true for a listed authorizer', () => {
    expect(isCurrentUserInvolved('alice', AUTHORIZERS, 'bob')).toBe(true)
  })

  it('is false for someone who is neither', () => {
    expect(isCurrentUserInvolved('alice', AUTHORIZERS, 'dave')).toBe(false)
  })
})

describe('effectiveActionState', () => {
  it("falls back to the consent's own state for the subject, who has no authorization entry", () => {
    expect(effectiveActionState('PENDING', AUTHORIZERS, 'alice')).toBe('PENDING')
  })

  it("uses the caller's own authorization entry when they are a listed authorizer", () => {
    expect(effectiveActionState('PENDING', AUTHORIZERS, 'bob')).toBe('APPROVED')
    expect(effectiveActionState('PENDING', AUTHORIZERS, 'carol')).toBe('REJECTED')
  })
})

describe('isApprovableByCurrentUser / isRejectableByCurrentUser', () => {
  it('allows both actions while pending', () => {
    expect(isApprovableByCurrentUser('PENDING', undefined, 'alice')).toBe(true)
    expect(isRejectableByCurrentUser('PENDING', undefined, 'alice')).toBe(true)
  })

  it('an approved (or active) decision can only be reconsidered by rejecting', () => {
    expect(isApprovableByCurrentUser('ACTIVE', AUTHORIZERS, 'bob')).toBe(false)
    expect(isRejectableByCurrentUser('ACTIVE', AUTHORIZERS, 'bob')).toBe(true)
  })

  it('a rejected decision can only be reconsidered by approving', () => {
    expect(isApprovableByCurrentUser('REJECTED', AUTHORIZERS, 'carol')).toBe(true)
    expect(isRejectableByCurrentUser('REJECTED', AUTHORIZERS, 'carol')).toBe(false)
  })

  it('a revoked or expired decision blocks both actions - a withdrawal stays final', () => {
    ;['REVOKED', 'EXPIRED'].forEach((state) => {
      expect(isApprovableByCurrentUser(state, undefined, 'alice')).toBe(false)
      expect(isRejectableByCurrentUser(state, undefined, 'alice')).toBe(false)
    })
  })

  it('gates on the specific authorizer asking, not on the aggregate consent state', () => {
    // The aggregate is still PENDING (waiting on carol), but bob already
    // approved and should not be offered to approve again.
    expect(isApprovableByCurrentUser('PENDING', AUTHORIZERS, 'bob')).toBe(false)
    expect(isRejectableByCurrentUser('PENDING', AUTHORIZERS, 'bob')).toBe(true)
  })
})
