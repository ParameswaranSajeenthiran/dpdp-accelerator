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

import { rm } from 'node:fs/promises'
import path from 'node:path'

/**
 * This suite runs against a real, persistent environment: consents/purposes/elements created by
 * tests stay in the real database (there is no per-run reset - see utils/testData.ts for why
 * tests are written to tolerate that instead) - deliberately not cleaned up here.
 *
 * `.auth/` is different: fixtures/auth.fixtures.ts's getPersonaState caches each persona's login
 * there so every test/worker can reuse it, and it's removed here so the next run always starts
 * with a fresh login rather than silently reusing a session left over from this one.
 */
export default async function globalTeardown(): Promise<void> {
  await rm(path.resolve(import.meta.dirname, '.auth'), { recursive: true, force: true })
  console.log('DPDP integration test suite run finished.')
}
