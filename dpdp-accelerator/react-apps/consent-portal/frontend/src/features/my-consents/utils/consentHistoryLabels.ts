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

import { SYSTEM_ACTOR } from '../../../types/consentHistory'

interface ConsentHistoryActionPresentation {
  labelKey: string
}

const ACTION_PRESENTATION: Record<string, ConsentHistoryActionPresentation> = {
  CREATE: { labelKey: 'created' },
  UPDATE: { labelKey: 'updated' },
  AUTHORIZE_APPROVE: { labelKey: 'approved' },
  AUTHORIZE_REJECT: { labelKey: 'rejected' },
  AUTHORIZE_REVOKE: { labelKey: 'authorizeRevoked' },
  REVOKE: { labelKey: 'revoked' },
  DELETE: { labelKey: 'deleted' },
  EXPIRE: { labelKey: 'expired' },
}

const DEFAULT_PRESENTATION: ConsentHistoryActionPresentation = {
  labelKey: 'unknown',
}

/** The i18n key under `consentRegistry.history.actions.<key>` for an action type. */
export function getConsentHistoryActionPresentation(
  actionType: string,
): ConsentHistoryActionPresentation {
  return ACTION_PRESENTATION[actionType] ?? DEFAULT_PRESENTATION
}

export function isSystemActor(actionBy: string): boolean {
  return actionBy === SYSTEM_ACTOR
}

/** SYSTEM never has an initial to render as an avatar letter. */
export function actorInitial(actionBy: string): string {
  return isSystemActor(actionBy) ? '' : actionBy.charAt(0).toUpperCase()
}
