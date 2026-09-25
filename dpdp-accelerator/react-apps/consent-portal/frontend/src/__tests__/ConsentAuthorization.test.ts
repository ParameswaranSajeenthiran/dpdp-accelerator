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
  getAdminConsentStatusMessageKey,
  getSelfConsentActionView,
  isApprovableByCurrentUser,
  isRejectableByCurrentUser,
  isRevokableByAdmin,
} from '../features/my-consents/utils/consentAuthorization'
import type { ConsentAuthorization } from '../types/consent'

const CO_AUTHORIZERS: ConsentAuthorization[] = [
  { userId: 'bob', state: 'APPROVED', updatedTime: 1 },
  { userId: 'carol', state: 'REJECTED', updatedTime: 2 },
]

describe('getSelfConsentActionView', () => {
  describe('Direct Consent (no authorizations)', () => {
    it('lets the subject revoke an active consent', () => {
      expect(getSelfConsentActionView('alice', 'ACTIVE', [], 'alice').canRevoke).toBe(true)
    })

    it('does not let an uninvolved caller revoke it, even while active', () => {
      // Only reachable through the admin surface's fallback to this same
      // view - the self-service endpoint never returns another user's consent.
      expect(getSelfConsentActionView('alice', 'ACTIVE', [], 'mallory').canRevoke).toBe(false)
    })

    it('shows a rejected message even without an authorizations entry - a consent can be created REJECTED directly', () => {
      // Not reachable through the normal UI flow (a Direct consent is created ACTIVE), but the
      // admin API accepts an explicit `state` at creation regardless of `authorizations` - this
      // must not silently show nothing.
      const view = getSelfConsentActionView('alice', 'REJECTED', [], 'alice')
      expect(view.canApprove).toBe(false)
      expect(view.canReject).toBe(false)
      expect(view.canRevoke).toBe(false)
      expect(view.statusMessageKey).toBe('youRejected')
    })

    it('offers no approve or reject, since a Direct consent never passes through PENDING', () => {
      const view = getSelfConsentActionView('alice', 'ACTIVE', [], 'alice')
      expect(view.canApprove).toBe(false)
      expect(view.canReject).toBe(false)
    })
  })

  describe('Delegated Consent, pure observer (no entry of their own)', () => {
    it('offers no actions, only a status message, at every reachable state', () => {
      expect(getSelfConsentActionView('alice', 'PENDING', CO_AUTHORIZERS, 'alice')).toEqual({
        canApprove: false,
        canReject: false,
        canRevoke: false,
        statusMessageKey: 'awaitingApproval',
      })
      expect(
        getSelfConsentActionView('alice', 'ACTIVE', CO_AUTHORIZERS, 'alice').statusMessageKey,
      ).toBe('approved')
      expect(
        getSelfConsentActionView('alice', 'REJECTED', CO_AUTHORIZERS, 'alice').statusMessageKey,
      ).toBe('rejected')
    })
  })

  describe('Anyone with their own authorizations entry', () => {
    it('offers approve and reject while their own decision is pending', () => {
      const pending: ConsentAuthorization[] = [{ userId: 'bob', state: 'PENDING', updatedTime: 1 }]
      expect(getSelfConsentActionView('alice', 'PENDING', pending, 'bob')).toEqual({
        canApprove: true,
        canReject: true,
        canRevoke: false,
        statusMessageKey: null,
      })
    })

    it('shows a waiting message once decided, even while the aggregate is still pending on someone else', () => {
      // The aggregate is still PENDING (waiting on carol), but bob already
      // decided and should not be offered to act again.
      const view = getSelfConsentActionView('alice', 'PENDING', CO_AUTHORIZERS, 'bob')
      expect(view.canApprove).toBe(false)
      expect(view.canReject).toBe(false)
      expect(view.statusMessageKey).toBe('decisionRecordedWaiting')
    })

    it('lets anyone with an entry revoke once active, not only whoever approved it', () => {
      expect(getSelfConsentActionView('alice', 'ACTIVE', CO_AUTHORIZERS, 'bob').canRevoke).toBe(
        true,
      )
      expect(getSelfConsentActionView('alice', 'ACTIVE', CO_AUTHORIZERS, 'carol').canRevoke).toBe(
        true,
      )
    })

    it("distinguishes the rejecter's own message from a co-authoriser's rejection", () => {
      expect(
        getSelfConsentActionView('alice', 'REJECTED', CO_AUTHORIZERS, 'carol').statusMessageKey,
      ).toBe('youRejected')
      // bob never decided (still PENDING on his own entry) when carol's
      // rejection ended the consent for everyone.
      expect(
        getSelfConsentActionView('alice', 'REJECTED', CO_AUTHORIZERS, 'bob').statusMessageKey,
      ).toBe('rejected')
    })
  })

  describe('terminal states win over a stale per-authorizer entry', () => {
    it.each(['REVOKED', 'EXPIRED'] as const)(
      "blocks every action once %s, regardless of the caller's own last decision",
      (state) => {
        const view = getSelfConsentActionView('alice', state, CO_AUTHORIZERS, 'bob')
        expect(view.canApprove).toBe(false)
        expect(view.canReject).toBe(false)
        expect(view.canRevoke).toBe(false)
        expect(view.statusMessageKey).toBe(state === 'REVOKED' ? 'revoked' : 'expired')
      },
    )
  })
})

describe('isApprovableByCurrentUser / isRejectableByCurrentUser', () => {
  it('never approve or reject a Direct Consent (no authorizations) - it only ever offers revoke', () => {
    expect(isApprovableByCurrentUser('PENDING', undefined, 'alice')).toBe(false)
    expect(isRejectableByCurrentUser('PENDING', undefined, 'alice')).toBe(false)
    expect(isApprovableByCurrentUser('ACTIVE', [], 'alice')).toBe(false)
    expect(isRejectableByCurrentUser('ACTIVE', [], 'alice')).toBe(false)
  })

  it('a decision, once made, blocks both actions - no reconsidering by acting again', () => {
    expect(isApprovableByCurrentUser('ACTIVE', CO_AUTHORIZERS, 'bob')).toBe(false)
    expect(isRejectableByCurrentUser('ACTIVE', CO_AUTHORIZERS, 'bob')).toBe(false)
    expect(isApprovableByCurrentUser('REJECTED', CO_AUTHORIZERS, 'carol')).toBe(false)
    expect(isRejectableByCurrentUser('REJECTED', CO_AUTHORIZERS, 'carol')).toBe(false)
  })

  it('a revoked or expired consent blocks both actions - a withdrawal stays final', () => {
    ;['REVOKED', 'EXPIRED'].forEach((state) => {
      expect(isApprovableByCurrentUser(state, undefined, 'alice')).toBe(false)
      expect(isRejectableByCurrentUser(state, undefined, 'alice')).toBe(false)
    })
  })

  it('gates on the specific authorizer asking, not on the aggregate consent state', () => {
    expect(isApprovableByCurrentUser('PENDING', CO_AUTHORIZERS, 'bob')).toBe(false)
    expect(isRejectableByCurrentUser('PENDING', CO_AUTHORIZERS, 'bob')).toBe(false)
  })

  it('never approvable or rejectable for a pure observer, even while the aggregate is pending', () => {
    expect(isApprovableByCurrentUser('PENDING', CO_AUTHORIZERS, 'alice')).toBe(false)
    expect(isRejectableByCurrentUser('PENDING', CO_AUTHORIZERS, 'alice')).toBe(false)
  })
})

describe('admin oversight gating', () => {
  it('offers revoke while pending or active, and only then', () => {
    expect(isRevokableByAdmin('PENDING')).toBe(true)
    expect(isRevokableByAdmin('ACTIVE')).toBe(true)
    expect(isRevokableByAdmin('REJECTED')).toBe(false)
    expect(isRevokableByAdmin('REVOKED')).toBe(false)
    expect(isRevokableByAdmin('EXPIRED')).toBe(false)
  })

  it('has a status message for every state that offers no admin action', () => {
    expect(getAdminConsentStatusMessageKey('PENDING')).toBeNull()
    expect(getAdminConsentStatusMessageKey('ACTIVE')).toBeNull()
    expect(getAdminConsentStatusMessageKey('REJECTED')).toBe('rejected')
    expect(getAdminConsentStatusMessageKey('REVOKED')).toBe('revoked')
    expect(getAdminConsentStatusMessageKey('EXPIRED')).toBe('expired')
  })
})
