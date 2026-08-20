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

import { readFileSync, readdirSync, statSync } from 'node:fs'
import { join, resolve } from 'node:path'

const root = resolve(import.meta.dirname, '..')
const i18nDir = join(root, 'public', 'i18n')
const baseLanguage = 'en'

/*
 * Only the `common` namespace is checked here. `catalog` holds wording for
 * purposes and elements that administrators create at run time, so an
 * incomplete `catalog.json` is the expected steady state -- a translator's
 * work list, not a build defect -- and would make this check noise rather
 * than signal.
 */
const namespace = 'common'

function keyPaths(value, prefix = '') {
  if (value === null || typeof value !== 'object' || Array.isArray(value)) {
    return prefix ? [prefix] : []
  }
  return Object.entries(value).flatMap(([key, child]) =>
    keyPaths(child, prefix ? `${prefix}.${key}` : key),
  )
}

function readNamespace(language) {
  const path = join(i18nDir, language, `${namespace}.json`)
  return JSON.parse(readFileSync(path, 'utf8'))
}

const languages = readdirSync(i18nDir).filter((name) => statSync(join(i18nDir, name)).isDirectory())

if (!languages.includes(baseLanguage)) {
  throw new Error(
    `i18n verification failed: no ${baseLanguage}/${namespace}.json to compare against.`,
  )
}

const baseKeys = new Set(keyPaths(readNamespace(baseLanguage)))
const failures = []

for (const language of languages.filter((name) => name !== baseLanguage)) {
  const languageKeys = new Set(keyPaths(readNamespace(language)))
  const missing = [...baseKeys].filter((key) => !languageKeys.has(key))
  const orphaned = [...languageKeys].filter((key) => !baseKeys.has(key))

  if (missing.length > 0) {
    failures.push(`${language}/${namespace}.json is missing: ${missing.join(', ')}`)
  }
  if (orphaned.length > 0) {
    failures.push(
      `${language}/${namespace}.json has keys ${baseLanguage} does not: ${orphaned.join(', ')}`,
    )
  }
}

if (failures.length > 0) {
  throw new Error(`i18n completeness verification failed:\n- ${failures.join('\n- ')}`)
}

console.log(`i18n completeness verification passed for ${languages.length} languages.`)
