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
import path from 'node:path'
import { describe, expect, it } from 'vitest'
import {
  ACCOUNT_SCOPES,
  COMPLAINT_SCOPES,
  CONSENT_HISTORY_SCOPES,
  EVENT_SCOPES,
  IS_SCOPES,
  REQUIRED_SCOPES,
  parseScopes,
} from '../utils/scopes'

describe('scope requirements', () => {
  it('only ever asks for real Identity Server and portal scopes', () => {
    const known = new Set<string>([
      ...Object.values(IS_SCOPES),
      ...Object.values(EVENT_SCOPES),
      ...Object.values(CONSENT_HISTORY_SCOPES),
      ...Object.values(COMPLAINT_SCOPES),
      ...Object.values(ACCOUNT_SCOPES),
    ])
    Object.values(REQUIRED_SCOPES)
      .flat()
      .forEach((scope) => {
        expect(known).toContain(scope)
      })
  })

  it('never invents an Identity Server scope with nothing on the server to back it', () => {
    Object.values(IS_SCOPES).forEach((scope) => {
      expect(scope.startsWith('internal_')).toBe(true)
    })
  })

  it('never invents a consent-history scope with nothing on the server to back it', () => {
    // The accelerator itself registers these server-side via
    // DPDPApiResourceProvisioningUtil - never a frontend-only fiction.
    Object.values(CONSENT_HISTORY_SCOPES).forEach((scope) => {
      expect(scope.startsWith('consent:')).toBe(true)
    })
  })

  it('never invents a complaint scope with nothing on the server to back it', () => {
    Object.values(COMPLAINT_SCOPES).forEach((scope) => {
      expect(scope.startsWith('complaints:')).toBe(true)
    })
  })

  it('treats managing your own consents as needing only a login', () => {
    expect(REQUIRED_SCOPES.CONSENTS_READ_SELF).toEqual([IS_SCOPES.LOGIN])
    expect(REQUIRED_SCOPES.CONSENTS_WRITE_SELF).toEqual([IS_SCOPES.LOGIN])
  })

  it('accepts any of the write scopes for an area', () => {
    expect(REQUIRED_SCOPES.PURPOSES_WRITE).toContain(IS_SCOPES.PURPOSE_CREATE)
    expect(REQUIRED_SCOPES.PURPOSES_WRITE).toContain(IS_SCOPES.PURPOSE_UPDATE)
    expect(REQUIRED_SCOPES.PURPOSES_WRITE).toContain(IS_SCOPES.PURPOSE_DELETE)
    expect(REQUIRED_SCOPES.ELEMENTS_WRITE).toContain(IS_SCOPES.ELEMENT_DELETE)
    expect(REQUIRED_SCOPES.CONSENTS_WRITE_ANY).toContain(IS_SCOPES.CONSENT_UPDATE)
  })

  /**
   * The application the accelerator registers must be able to obtain every
   * scope the UI gates on, otherwise an area would be permanently invisible.
   */
  it('is covered by the scopes the deployed application requests', () => {
    const config = JSON.parse(
      readFileSync(path.resolve(__dirname, '../../public/deployment.config.json'), 'utf8'),
    ) as { scope: string[] }
    const requested = new Set(config.scope)

    Object.values(IS_SCOPES).forEach((scope) => {
      expect(requested).toContain(scope)
    })
    Object.values(EVENT_SCOPES).forEach((scope) => {
      expect(requested).toContain(scope)
    })
    Object.values(CONSENT_HISTORY_SCOPES).forEach((scope) => {
      expect(requested).toContain(scope)
    })
    Object.values(COMPLAINT_SCOPES).forEach((scope) => {
      expect(requested).toContain(scope)
    })
    Object.values(ACCOUNT_SCOPES).forEach((scope) => {
      expect(requested).toContain(scope)
    })
  })
})

describe('parseScopes', () => {
  it('splits the space separated scope string a token carries', () => {
    expect(parseScopes('openid internal_login  internal_consent_mgt_consent_view')).toEqual([
      'openid',
      'internal_login',
      'internal_consent_mgt_consent_view',
    ])
  })

  it('accepts an array unchanged and tolerates nothing at all', () => {
    expect(parseScopes(['openid', ' internal_login '])).toEqual(['openid', 'internal_login'])
    expect(parseScopes(undefined)).toEqual([])
    expect(parseScopes('')).toEqual([])
  })
})
