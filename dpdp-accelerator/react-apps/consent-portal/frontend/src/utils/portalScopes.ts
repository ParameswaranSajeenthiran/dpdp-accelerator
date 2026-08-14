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

export const PORTAL_SCOPES = {
  CONSENTS_READ_SELF: 'portal:consents:read:self',
  CONSENTS_WRITE_SELF: 'portal:consents:write:self',
  CONSENTS_READ_ANY: 'portal:consents:read:any',
  CONSENTS_WRITE_ANY: 'portal:consents:write:any',
  ELEMENTS_READ: 'portal:elements:read',
  ELEMENTS_WRITE: 'portal:elements:write',
  PURPOSES_READ: 'portal:purposes:read',
  PURPOSES_WRITE: 'portal:purposes:write',
  EVENT_TOPICS_READ: 'portal:event-topics:read',
  EVENT_TOPICS_WRITE: 'portal:event-topics:write',
  EVENT_SUBSCRIPTIONS_READ: 'portal:event-subscriptions:read',
  EVENT_SUBSCRIPTIONS_WRITE: 'portal:event-subscriptions:write',
  EVENTS_READ: 'portal:events:read',
  EVENTS_WRITE: 'portal:events:write',
  EVENT_DELIVERIES_READ: 'portal:event-deliveries:read',
  EVENT_DELIVERIES_WRITE: 'portal:event-deliveries:write',
} as const

export type PortalScope = (typeof PORTAL_SCOPES)[keyof typeof PORTAL_SCOPES]

const PORTAL_SCOPE_VALUES = new Set<string>(Object.values(PORTAL_SCOPES))

export function isPortalScope(value: unknown): value is PortalScope {
  return typeof value === 'string' && PORTAL_SCOPE_VALUES.has(value)
}
