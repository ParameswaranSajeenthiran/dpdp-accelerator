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

import type { HttpRequestConfig } from '@asgardeo/auth-spa'

import { httpRequest, isAuthEnabled, login } from './authClient'
import { serverBaseUrl } from './basePath'

/**
 * Calls the Identity Server REST APIs on behalf of the signed-in user.
 *
 * Requests go through the auth SDK so the web worker can attach the access
 * token; the page itself never holds one. Identity Server error bodies
 * ({@code {code, message, description}}) are normalised into {@link APIError}.
 */

export interface APIErrorPayload {
  code?: string
  message?: string
  description?: string
}

export class APIError extends Error {
  public readonly status: number

  public readonly code: string

  public constructor(status: number, code: string, message: string) {
    super(message)
    this.name = 'APIError'
    this.status = status
    this.code = code
  }
}

export interface RequestOptions {
  method?: string
  headers?: Record<string, string>
  body?: string
  query?: Record<string, string | number | boolean | undefined>
}

const DEFAULT_ERROR_CODE = 'API_REQUEST_FAILED'

/**
 * Where the Identity Server APIs live. In WAR production this is empty — the IS is
 * same-origin and tenant-qualified. Set VITE_IS_BASE_URL during local dev
 * to point at a remote IS instance.
 */
function apiBaseUrl(): string {
  const configured = (import.meta.env.VITE_IS_BASE_URL as string | undefined) ?? ''
  if (/^https?:\/\//i.test(configured)) {
    return configured.endsWith('/') ? configured.slice(0, -1) : configured
  }
  return serverBaseUrl()
}

function buildURL(path: string, query?: RequestOptions['query']): string {
  if (/^https?:\/\//i.test(path)) {
    throw new Error(`apiClient path must be relative, received: "${path}"`)
  }

  const normalizedPath = path.startsWith('/') ? path : `/${path}`
  const url = new URL(`${apiBaseUrl()}${normalizedPath}`, window.location.origin)

  if (query) {
    Object.entries(query).forEach(([key, value]) => {
      if (value === undefined) {
        return
      }
      url.searchParams.set(key, String(value))
    })
  }

  return url.toString()
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null
}

/**
 * Identity Server errors carry a terse {@code message} (often just the HTTP
 * reason) and the real detail in {@code description}; prefer the latter.
 */
function payloadToError(status: number, payload: unknown): APIError {
  let code = DEFAULT_ERROR_CODE
  let message = `request failed with status ${status}`

  if (isRecord(payload)) {
    const body = payload as APIErrorPayload
    if (typeof body.code === 'string' && body.code) {
      code = body.code
    }
    if (typeof body.message === 'string' && body.message) {
      message = body.message
    }
    if (typeof body.description === 'string' && body.description.trim()) {
      message = body.description
    }
  }

  return new APIError(status, code, message)
}

interface RawResponse {
  status: number
  data: unknown
}

/** Normalises an SDK/axios rejection into a status plus parsed body. */
function toRawResponse(error: unknown): RawResponse | undefined {
  if (!isRecord(error)) {
    return undefined
  }
  const response = (error as { response?: unknown }).response
  if (!isRecord(response) || typeof (response as { status?: unknown }).status !== 'number') {
    return undefined
  }
  return {
    status: (response as { status: number }).status,
    data: (response as { data?: unknown }).data,
  }
}

async function send(path: string, options: RequestOptions): Promise<RawResponse> {
  const url = buildURL(path, options.query)
  const method = options.method ?? 'GET'
  const headers: Record<string, string> = { Accept: 'application/json', ...options.headers }

  if (!isAuthEnabled()) {
    // Development affordance: no SDK session, plain same-origin call.
    const response = await fetch(url, { method, headers, body: options.body })
    const text = await response.text()
    let data: unknown
    try {
      data = text ? JSON.parse(text) : undefined
    } catch {
      data = undefined
    }
    return { status: response.status, data }
  }

  try {
    const response = await httpRequest({
      data: options.body,
      headers,
      method: method as HttpRequestConfig['method'],
      url,
    })
    if (!response) {
      throw new APIError(502, DEFAULT_ERROR_CODE, 'the consent service did not respond')
    }
    return { status: response.status, data: response.data }
  } catch (error) {
    if (error instanceof APIError) {
      throw error
    }
    const raw = toRawResponse(error)
    if (!raw) {
      throw new APIError(502, DEFAULT_ERROR_CODE, 'the consent service is unavailable')
    }
    return raw
  }
}

async function requestRaw(path: string, options: RequestOptions): Promise<RawResponse> {
  const raw = await send(path, options)

  if (raw.status === 401 && isAuthEnabled()) {
    // The SDK refreshes silently, so a 401 means the session is really gone.
    void login()
    throw payloadToError(raw.status, raw.data)
  }

  if (raw.status < 200 || raw.status >= 300) {
    throw payloadToError(raw.status, raw.data)
  }

  return raw
}

export async function apiRequest<T>(path: string, options: RequestOptions = {}): Promise<T> {
  const raw = await requestRaw(path, options)
  if (raw.data === undefined || raw.data === '') {
    throw new APIError(
      raw.status,
      DEFAULT_ERROR_CODE,
      'expected a response body; use apiRequestNoContent for empty responses',
    )
  }
  return raw.data as T
}

export async function apiRequestNoContent(
  path: string,
  options: RequestOptions = {},
): Promise<void> {
  await requestRaw(path, options)
}

/**
 * For endpoints the Identity Server answers with an empty body on success
 * (revoke, authorize) where the caller still wants a value back.
 */
export async function apiRequestOptionalContent<T>(
  path: string,
  options: RequestOptions = {},
): Promise<T | undefined> {
  const raw = await requestRaw(path, options)
  return raw.data === undefined || raw.data === '' ? undefined : (raw.data as T)
}
