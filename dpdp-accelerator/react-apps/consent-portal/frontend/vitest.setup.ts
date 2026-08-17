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
import { beforeEach, vi } from 'vitest'

/*
 * jsdom implements neither web workers nor object URLs, and the auth SDK
 * builds its worker from a blob while its module is being evaluated. Stub
 * both so importing anything that reaches the SDK does not blow up; tests
 * that care about authentication mock the SDK itself.
 */
if (typeof globalThis.Worker === 'undefined') {
  class WorkerStub implements Partial<Worker> {
    public onmessage: ((this: Worker, event: MessageEvent) => unknown) | null = null

    public onerror: ((this: AbstractWorker, event: ErrorEvent) => unknown) | null = null

    public postMessage(): void {}

    public terminate(): void {}

    public addEventListener(): void {}

    public removeEventListener(): void {}

    public dispatchEvent(): boolean {
      return false
    }
  }

  globalThis.Worker = WorkerStub as unknown as typeof Worker
}

if (typeof URL.createObjectURL !== 'function') {
  URL.createObjectURL = () => 'blob:test'
  URL.revokeObjectURL = () => {}
}

beforeEach(() => {
  vi.stubEnv('VITE_AUTH_ENABLED', 'true')
})
