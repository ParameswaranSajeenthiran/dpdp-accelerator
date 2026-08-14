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

import type { APIRequestContext, APIResponse } from '@playwright/test'
import {
  adminConsentsApiUrl,
  consentElementsApiUrl,
  consentPurposesApiUrl,
  myConsentsApiUrl,
} from '../utils/env'
import type { AuthHeaders } from '../utils/authStorage'

export interface PurposeElementBinding {
  id: string
  mandatory?: boolean
}

export interface CreatePurposeBody {
  name: string
  type: string
  version: string
  description?: string
  elements?: PurposeElementBinding[]
  properties?: Record<string, string>
}

export interface CreateElementBody {
  name: string
  displayName?: string
  description?: string
  properties?: Record<string, string>
}

export interface ConsentPurposeBinding {
  id: string
  elements: Array<{ id: string }>
}

export interface AuthorizationEntry {
  userId: string
  type?: string
}

export interface CreateConsentBody {
  subjectId: string
  serviceId: string
  purposes: ConsentPurposeBinding[]
  language?: string
  /** Omit when `authorizations` is set - the server forces PENDING and rejects an explicit state. */
  state?: 'ACTIVE' | 'REJECTED'
  expiryTime?: number
  authorizations?: AuthorizationEntry[]
  properties?: Record<string, string>
}

export interface AdminConsentListParams {
  limit?: number
  after?: string
  before?: string
  subjectId?: string
  serviceId?: string
  state?: string
}

export interface MyConsentListParams {
  consentStatuses?: string
  serviceId?: string
  limit?: number
  offset?: number
}

/**
 * Wraps the BFF's consent-management contract - both the self-service surface
 * (`/me/consents/*`, backed by MyConsentsServlet, granted to any authenticated user via
 * `internal_login`) and the administrative surface (`/api/consents`, `/api/consent-purposes`,
 * `/api/consent-elements`, backed by AdminApiServlet, requiring the `dpdp-consent-admin` role).
 * Both surfaces are proxied 1:1 to WSO2 IS's own consent-mgt v2 API - see
 * consent-management-v2.yaml (bundled in
 * org.wso2.carbon.identity.api.server.consent.management.v2-*.jar) for the authoritative schema.
 *
 * A single class serves both surfaces because the auth is just a header pair resolved from
 * whichever persona's storageState the caller passes in (see utils/authStorage.ts) - tests that
 * need to prove a Data Principal's token is rejected by the admin surface construct this client
 * with the data-principal's headers and call an admin method directly.
 */
export class ConsentApiClient {
  constructor(
    private readonly request: APIRequestContext,
    private readonly auth: AuthHeaders,
  ) {}

  private headers(extra?: Record<string, string>): Record<string, string> {
    return { ...this.auth, ...extra }
  }

  // --------------------------------------------------------------------- self-service surface

  async listMyConsents(params: MyConsentListParams = {}): Promise<APIResponse> {
    return this.request.get(myConsentsApiUrl(''), {
      headers: this.headers(),
      params: Object.fromEntries(
        Object.entries(params)
          .filter(([, value]) => value !== undefined)
          .map(([key, value]) => [key, String(value)]),
      ),
    })
  }

  async getMyConsent(consentId: string): Promise<APIResponse> {
    return this.request.get(myConsentsApiUrl(`/${consentId}`), { headers: this.headers() })
  }

  async approveMyConsent(consentId: string): Promise<APIResponse> {
    return this.request.post(myConsentsApiUrl(`/${consentId}/approve`), {
      headers: this.headers({ 'Content-Type': 'application/json' }),
      data: {},
    })
  }

  async rejectMyConsent(consentId: string): Promise<APIResponse> {
    return this.request.post(myConsentsApiUrl(`/${consentId}/reject`), {
      headers: this.headers({ 'Content-Type': 'application/json' }),
      data: {},
    })
  }

  async revokeMyConsent(consentId: string): Promise<APIResponse> {
    return this.request.post(myConsentsApiUrl(`/${consentId}/revoke`), {
      headers: this.headers({ 'Content-Type': 'application/json' }),
      data: {},
    })
  }

  // ---------------------------------------------------------------------------- admin surface

  async createPurpose(body: CreatePurposeBody): Promise<APIResponse> {
    return this.request.post(consentPurposesApiUrl(''), {
      headers: this.headers({ 'Content-Type': 'application/json' }),
      data: body,
    })
  }

  async createElement(body: CreateElementBody): Promise<APIResponse> {
    return this.request.post(consentElementsApiUrl(''), {
      headers: this.headers({ 'Content-Type': 'application/json' }),
      data: body,
    })
  }

  /** Exact match on `name` - elements have no other unique-ish field to search by. */
  async findElementByName(name: string): Promise<APIResponse> {
    return this.request.get(consentElementsApiUrl(''), {
      headers: this.headers(),
      params: { filter: `name eq "${name}"`, limit: 1 },
    })
  }

  /** Exact match on `name` - a purpose can have several versions but the name alone identifies it here. */
  async findPurposeByName(name: string): Promise<APIResponse> {
    return this.request.get(consentPurposesApiUrl(''), {
      headers: this.headers(),
      params: { filter: `name eq "${name}"`, limit: 1 },
    })
  }

  /** WSO2 IS caps this at 100 regardless of the requested `limit` - page via `after` to see more. */
  async listElements(params: { limit?: number; after?: string } = {}): Promise<APIResponse> {
    return this.request.get(consentElementsApiUrl(''), {
      headers: this.headers(),
      params: Object.fromEntries(
        Object.entries(params)
          .filter(([, value]) => value !== undefined)
          .map(([key, value]) => [key, String(value)]),
      ),
    })
  }

  /** Same 100-record server cap as listElements. */
  async listPurposes(params: { limit?: number; after?: string } = {}): Promise<APIResponse> {
    return this.request.get(consentPurposesApiUrl(''), {
      headers: this.headers(),
      params: Object.fromEntries(
        Object.entries(params)
          .filter(([, value]) => value !== undefined)
          .map(([key, value]) => [key, String(value)]),
      ),
    })
  }

  /** 409 when still referenced by a Purpose - callers sweeping in bulk should tolerate that. */
  async deleteElement(elementId: string): Promise<APIResponse> {
    return this.request.delete(consentElementsApiUrl(`/${elementId}`), { headers: this.headers() })
  }

  /** 409 when still referenced by a Consent - callers sweeping in bulk should tolerate that. */
  async deletePurpose(purposeId: string): Promise<APIResponse> {
    return this.request.delete(consentPurposesApiUrl(`/${purposeId}`), { headers: this.headers() })
  }

  async createConsent(body: CreateConsentBody): Promise<APIResponse> {
    return this.request.post(adminConsentsApiUrl(''), {
      headers: this.headers({ 'Content-Type': 'application/json' }),
      data: body,
    })
  }

  async listAdminConsents(params: AdminConsentListParams = {}): Promise<APIResponse> {
    return this.request.get(adminConsentsApiUrl(''), {
      headers: this.headers(),
      params: Object.fromEntries(
        Object.entries(params)
          .filter(([, value]) => value !== undefined)
          .map(([key, value]) => [key, String(value)]),
      ),
    })
  }

  async revokeAdminConsent(consentId: string): Promise<APIResponse> {
    return this.request.post(adminConsentsApiUrl(`/${consentId}/revoke`), {
      headers: this.headers({ 'Content-Type': 'application/json' }),
      data: {},
    })
  }
}
