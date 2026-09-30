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

import { mkdir, open, rm, stat } from 'node:fs/promises'
import path from 'node:path'

// Under .auth/ so global-teardown.ts clears any lock a killed run left behind.
const LOCK_DIR = path.resolve(import.meta.dirname, '..', '.auth', 'locks')
const POLL_MS = 250
const ACQUIRE_TIMEOUT_MS = 120_000

/**
 * Runs `fn` while holding a lock that every Playwright worker process shares, so at most one
 * worker is inside it at a time. `open(path, 'wx')` is an atomic exclusive create - the same
 * primitive as auth.fixtures.ts's login lock - and fails with EEXIST while another holder has it.
 *
 * Keep `fn` short: a lock older than `staleAfterMs` is taken to belong to a worker that died
 * holding it, and is removed so the run doesn't wait on it for good.
 */
export async function withCrossProcessLock<T>(
  name: string,
  fn: () => Promise<T>,
  { staleAfterMs = 120_000 }: { staleAfterMs?: number } = {},
): Promise<T> {
  await mkdir(LOCK_DIR, { recursive: true })
  const lockPath = path.join(LOCK_DIR, `${name}.lock`)
  const deadline = Date.now() + ACQUIRE_TIMEOUT_MS

  for (;;) {
    try {
      await (await open(lockPath, 'wx')).close()
      break
    } catch (error) {
      if ((error as NodeJS.ErrnoException).code !== 'EEXIST') {
        throw error
      }
    }
    const heldFor = await stat(lockPath).then(
      (lock) => Date.now() - lock.mtimeMs,
      () => 0,
    )
    if (heldFor > staleAfterMs) {
      await rm(lockPath, { force: true })
      continue
    }
    if (Date.now() > deadline) {
      throw new Error(
        `Timed out after ${String(ACQUIRE_TIMEOUT_MS / 1000)}s waiting for the "${name}" lock - another ` +
          'worker is still holding it. Check the run for a stalled test in that worker.',
      )
    }
    await new Promise((resolve) => {
      setTimeout(resolve, POLL_MS)
    })
  }

  try {
    return await fn()
  } finally {
    await rm(lockPath, { force: true })
  }
}
