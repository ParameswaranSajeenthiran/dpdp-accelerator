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

import '@testing-library/jest-dom/vitest'
import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'
import { beforeEach, vi } from 'vitest'
import type { BackendModule, ReadCallback } from 'i18next'

/**
 * Real translations are fetched over HTTP in the browser, but there is no
 * server behind that fetch in a jsdom test run. Reading the same JSON files
 * straight off disk keeps every test that renders translated text working
 * exactly as it did before this app moved off compiled-in resources -
 * without changing a single test's assertions - while still exercising the
 * real translation content instead of a hand-maintained mock copy of it.
 */
vi.mock('i18next-http-backend', () => ({
  default: class FileSystemI18nBackend implements BackendModule {
    static type = 'backend'

    type = 'backend' as const

    // i18next's BackendModule interface requires instance methods here; this
    // implementation genuinely needs no instance state.
    // eslint-disable-next-line class-methods-use-this
    init = (): void => {}

    // eslint-disable-next-line class-methods-use-this
    read = (language: string, namespace: string, callback: ReadCallback): void => {
      try {
        const path = resolve(import.meta.dirname, 'public', 'i18n', language, `${namespace}.json`)
        callback(null, JSON.parse(readFileSync(path, 'utf8')))
      } catch (error) {
        callback(error as Error, false)
      }
    }
  },
}))

beforeEach(() => {
  vi.stubEnv('VITE_API_BASE_URL', 'http://localhost:8080')
})
