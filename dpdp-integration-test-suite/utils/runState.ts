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

import { chmodSync, existsSync, readFileSync, writeFileSync } from 'node:fs'
import path from 'node:path'
import { deepMerge } from './deepMerge'

export interface PersonaCredential {
  username: string
  password: string
}

export interface TenantRunState {
  domain: string
  owner: PersonaCredential
  personas?: {
    consentAdmin?: PersonaCredential
    user?: PersonaCredential
    user2?: PersonaCredential
    dpo?: PersonaCredential
  }
}

export interface RunState {
  tenant?: TenantRunState
}

const suiteRoot = path.resolve(import.meta.dirname, '..')
export const RUN_STATE_PATH = path.join(suiteRoot, '.e2e-run-state.json')

/**
 * The per-run tenant this describes is never deleted (see the design spec, "No cleanup,
 * anywhere"), so this file is not deleted between runs either - global-teardown.ts leaves it
 * alone. tenant-creation/user-provisioning re-read it every run and skip whatever it already
 * has, which is what makes both resumable and what gives a local run its escape hatch: delete
 * this file by hand for a genuinely fresh tenant next time.
 */
export function readRunState(): RunState {
  if (!existsSync(RUN_STATE_PATH)) {
    return {}
  }
  try {
    return JSON.parse(readFileSync(RUN_STATE_PATH, 'utf8')) as RunState
  } catch (error) {
    throw new Error(`Could not read ${path.basename(RUN_STATE_PATH)}: ${(error as Error).message}`)
  }
}

/** Merges `patch` into the persisted state one level deep, so a caller can set e.g.
 * `tenant.personas.dpo` without restating `tenant.domain`/`tenant.owner`. */
export function writeRunState(patch: RunState): void {
  const merged = deepMerge(readRunState() as Record<string, unknown>, patch as unknown as Record<string, unknown>)
  writeFileSync(RUN_STATE_PATH, `${JSON.stringify(merged, null, 2)}\n`)
  chmodSync(RUN_STATE_PATH, 0o600)
}
