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

/**
 * Where the portal is served from, detected at runtime.
 *
 * The Identity Server serves this webapp both unqualified
 * ("/consent-portal") and tenant-qualified ("/t/<tenant>/consent-portal"), so
 * the base cannot be baked in at build time. Everything that has to address
 * the server - OAuth endpoints, consent APIs, the router basename - derives
 * from these helpers.
 */

const CONTEXT_SEGMENT = '/consent-portal'

/** "/t/<tenant>/consent-portal" under a tenant, "/consent-portal" otherwise. */
export function runtimeBasePath(pathname: string = window.location.pathname): string {
  const match = pathname.match(/^(\/t\/[^/]+)?\/consent-portal(?:\/|$)/)
  return match ? `${match[1] ?? ''}${CONTEXT_SEGMENT}` : CONTEXT_SEGMENT
}

/** The tenant domain from a tenant-qualified URL, or undefined for the super tenant. */
export function tenantFromPath(pathname: string = window.location.pathname): string | undefined {
  return pathname.match(/^\/t\/([^/]+)\/consent-portal(?:\/|$)/)?.[1]
}

/**
 * The Identity Server URL prefix addressing this request's tenant: "" for the
 * super tenant (the server rejects /t/carbon.super by default), "/t/<tenant>"
 * otherwise. Prefixed to every OAuth and consent API path.
 */
export function tenantPathSegment(pathname: string = window.location.pathname): string {
  const tenant = tenantFromPath(pathname)
  return tenant ? `/t/${tenant}` : ''
}

/** Absolute Identity Server base for this tenant, e.g. https://host:9443/t/acme.com */
export function serverBaseUrl(): string {
  return `${window.location.origin}${tenantPathSegment()}`
}
