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

import { Ban, Bot, CircleCheckBig, Clock3, Pencil, Plus, Trash2 } from '@wso2/oxygen-ui-icons-react'
import type { ComponentType } from 'react'
import { SYSTEM_ACTOR } from '../../../types/consentHistory'

type ConsentHistoryChipColor = 'success' | 'warning' | 'error' | 'default'

interface ConsentHistoryActionPresentation {
  labelKey: string
  icon: ComponentType<{ size?: number }>
  color: ConsentHistoryChipColor
}

const ACTION_PRESENTATION: Record<string, ConsentHistoryActionPresentation> = {
  CREATE: { labelKey: 'created', icon: Plus, color: 'default' },
  UPDATE: { labelKey: 'updated', icon: Pencil, color: 'default' },
  AUTHORIZE_APPROVE: { labelKey: 'approved', icon: CircleCheckBig, color: 'success' },
  AUTHORIZE_REJECT: { labelKey: 'rejected', icon: Ban, color: 'error' },
  AUTHORIZE_REVOKE: { labelKey: 'authorizeRevoked', icon: Ban, color: 'error' },
  REVOKE: { labelKey: 'revoked', icon: Ban, color: 'error' },
  DELETE: { labelKey: 'deleted', icon: Trash2, color: 'error' },
  EXPIRE: { labelKey: 'expired', icon: Clock3, color: 'default' },
}

const DEFAULT_PRESENTATION: ConsentHistoryActionPresentation = {
  labelKey: 'unknown',
  icon: Pencil,
  color: 'default',
}

/** The i18n key under `consentRegistry.history.actions.<key>`, icon and chip color for an action. */
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

export { Bot as SystemActorIcon }
