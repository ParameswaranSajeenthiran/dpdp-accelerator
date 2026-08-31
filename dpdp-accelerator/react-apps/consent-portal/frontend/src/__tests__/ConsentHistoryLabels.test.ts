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
import { resolve } from 'node:path'
import { describe, expect, it } from 'vitest'
import {
  actorInitial,
  getConsentHistoryActionPresentation,
  isSystemActor,
} from '../features/my-consents/utils/consentHistoryLabels'
import { CONSENT_HISTORY_ACTION_TYPES, SYSTEM_ACTOR } from '../types/consentHistory'

const commonEn: { consentRegistry: { history: { actions: Record<string, string> } } } = JSON.parse(
  readFileSync(resolve(import.meta.dirname, '../../public/i18n/en/common.json'), 'utf8'),
)

describe('consent history action presentation', () => {
  it('maps every known action type to a translation key that exists in the English resources', () => {
    CONSENT_HISTORY_ACTION_TYPES.forEach((actionType) => {
      const presentation = getConsentHistoryActionPresentation(actionType)
      expect(commonEn.consentRegistry.history.actions).toHaveProperty(presentation.labelKey)
    })
  })

  it('falls back to a default presentation for an unrecognised action type', () => {
    const presentation = getConsentHistoryActionPresentation('SOMETHING_NEW')
    expect(commonEn.consentRegistry.history.actions).toHaveProperty(presentation.labelKey)
  })

  it('recognises the SYSTEM actor and never derives an initial for it', () => {
    expect(isSystemActor(SYSTEM_ACTOR)).toBe(true)
    expect(isSystemActor('nadia.perera@wso2.com')).toBe(false)
    expect(actorInitial(SYSTEM_ACTOR)).toBe('')
    expect(actorInitial('nadia.perera@wso2.com')).toBe('N')
  })
})
