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
import { runtimeBasePath, tenantFromPath, tenantPathSegment } from '../utils/basePath'

describe('runtimeBasePath', () => {
  it('returns the plain context path for unqualified URLs', () => {
    expect(runtimeBasePath('/consent-portal')).toBe('/consent-portal')
    expect(runtimeBasePath('/consent-portal/')).toBe('/consent-portal')
    expect(runtimeBasePath('/consent-portal/consents')).toBe('/consent-portal')
  })

  it('keeps the tenant prefix for tenant qualified URLs', () => {
    expect(runtimeBasePath('/t/wso2.com/consent-portal')).toBe('/t/wso2.com/consent-portal')
    expect(runtimeBasePath('/t/wso2.com/consent-portal/admin/consents')).toBe(
      '/t/wso2.com/consent-portal',
    )
  })

  it('falls back to the context path when the URL has neither form', () => {
    expect(runtimeBasePath('/')).toBe('/consent-portal')
    expect(runtimeBasePath('/somewhere/else')).toBe('/consent-portal')
  })

  it('does not treat a longer context name as a match', () => {
    expect(runtimeBasePath('/consent-portal-other/x')).toBe('/consent-portal')
    expect(runtimeBasePath('/t/acme/consent-portal-other')).toBe('/consent-portal')
  })
})

describe('tenantFromPath', () => {
  it('extracts the tenant domain', () => {
    expect(tenantFromPath('/t/wso2.com/consent-portal/consents')).toBe('wso2.com')
  })

  it('is undefined for the super tenant', () => {
    expect(tenantFromPath('/consent-portal/consents')).toBeUndefined()
    expect(tenantFromPath('/')).toBeUndefined()
  })

  it('rejects anything that is not shaped like a domain', () => {
    // The value is spliced into API and OAuth URLs, so a dot segment or an
    // encoded separator must not survive to redirect them elsewhere.
    expect(tenantFromPath('/t/%2e%2e/consent-portal/')).toBeUndefined()
    expect(tenantFromPath('/t/..%2f/consent-portal/')).toBeUndefined()
    expect(tenantFromPath('/t/-acme.com/consent-portal/')).toBeUndefined()
    expect(tenantFromPath('/t/acme.com-/consent-portal/')).toBeUndefined()
    expect(tenantFromPath('/t/a b.com/consent-portal/')).toBeUndefined()
  })

  it('accepts the domain forms a tenant really takes', () => {
    expect(tenantFromPath('/t/acme.com/consent-portal/')).toBe('acme.com')
    expect(tenantFromPath('/t/sub.acme-corp.co.uk/consent-portal/')).toBe('sub.acme-corp.co.uk')
    expect(tenantFromPath('/t/a/consent-portal/')).toBe('a')
  })

  it('ignores a malformed tenant when deriving the base path', () => {
    expect(runtimeBasePath('/t/%2e%2e/consent-portal/')).toBe('/consent-portal')
  })
})

describe('tenantPathSegment', () => {
  it('is empty for the super tenant, since /t/carbon.super is rejected', () => {
    expect(tenantPathSegment('/consent-portal/')).toBe('')
  })

  it('addresses the tenant otherwise', () => {
    expect(tenantPathSegment('/t/acme.com/consent-portal/')).toBe('/t/acme.com')
  })
})
