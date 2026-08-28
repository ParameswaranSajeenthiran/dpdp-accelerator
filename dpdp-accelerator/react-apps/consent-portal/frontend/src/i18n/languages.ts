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

import { runtimeBasePath } from '../utils/basePath'

/**
 * Languages offered by the portal.
 *
 * Section 5(3) of the Digital Personal Data Protection Act, 2023 requires that
 * the Data Principal be given the option to access the notice - and, by
 * extension, the rights-exercise flows it points to - in English or any of the
 * 22 languages listed in the Eighth Schedule to the Constitution of India.
 * The list below is English + those 22 scheduled languages.
 */

export interface LanguageMeta {
  /** BCP-47 / ISO code used as the i18next language key and resource folder name. */
  code: string
  /** Native-script name shown in the switcher. */
  endonym: string
  /** English name (for tooltips / accessibility). */
  english: string
}

/**
 * The languages this build knows about.
 *
 * Treated as a fallback rather than the authority: the list actually offered is
 * read from `i18n/meta.json` at startup, so a deployment can add a language by
 * dropping in its JSON and naming it there, without rebuilding. This constant
 * is what the portal falls back to when that file is missing or unreadable, so
 * a failed fetch degrades to the shipped set instead of an empty switcher.
 */
export const BUNDLED_LANGUAGES: LanguageMeta[] = [
  { code: 'en', endonym: 'English', english: 'English' },
  { code: 'hi', endonym: 'हिन्दी', english: 'Hindi' },
  { code: 'as', endonym: 'অসমীয়া', english: 'Assamese' },
  { code: 'bn', endonym: 'বাংলা', english: 'Bengali' },
  { code: 'brx', endonym: 'बड़ो', english: 'Bodo' },
  { code: 'doi', endonym: 'डोगरी', english: 'Dogri' },
  { code: 'gu', endonym: 'ગુજરાતી', english: 'Gujarati' },
  { code: 'kn', endonym: 'ಕನ್ನಡ', english: 'Kannada' },
  { code: 'ks', endonym: 'کٲشُر', english: 'Kashmiri' },
  { code: 'kok', endonym: 'कोंकणी', english: 'Konkani' },
  { code: 'mai', endonym: 'मैथिली', english: 'Maithili' },
  { code: 'ml', endonym: 'മലയാളം', english: 'Malayalam' },
  { code: 'mni', endonym: 'মৈতৈলোন্', english: 'Manipuri (Meitei)' },
  { code: 'mr', endonym: 'मराठी', english: 'Marathi' },
  { code: 'ne', endonym: 'नेपाली', english: 'Nepali' },
  { code: 'or', endonym: 'ଓଡ଼ିଆ', english: 'Odia' },
  { code: 'pa', endonym: 'ਪੰਜਾਬੀ', english: 'Punjabi' },
  { code: 'sa', endonym: 'संस्कृतम्', english: 'Sanskrit' },
  { code: 'sat', endonym: 'ᱥᱟᱱᱛᱟᱲᱤ', english: 'Santali' },
  { code: 'sd', endonym: 'سنڌي', english: 'Sindhi' },
  { code: 'ta', endonym: 'தமிழ்', english: 'Tamil' },
  { code: 'te', endonym: 'తెలుగు', english: 'Telugu' },
  { code: 'ur', endonym: 'اردو', english: 'Urdu' },
]

export const DEFAULT_LANGUAGE = 'en'

/** English, the first entry above and the fallback for every other language. */
export const DEFAULT_LANGUAGE_META: LanguageMeta = BUNDLED_LANGUAGES[0]

/** Cookie name used to remember the language choice. */
export const LANGUAGE_STORAGE_KEY = 'dpdp.lang'
/** One year, matching how long a language preference is worth remembering. */
const LANGUAGE_COOKIE_MAX_AGE_SECONDS = 60 * 60 * 24 * 365

let available: LanguageMeta[] = BUNDLED_LANGUAGES
let byCode = new Map(available.map((language) => [language.code, language]))

/** The languages currently on offer - the catalogue if it loaded, else the bundled set. */
export function getLanguages(): LanguageMeta[] {
  return available
}

/**
 * Replaces the offered languages with a catalogue read at run time.
 *
 * Entries missing a code or a name are dropped rather than shown as blanks, and
 * an empty result leaves the bundled set in place: a malformed file should cost
 * the reader nothing worse than the languages this build already had.
 */
export function setAvailableLanguages(languages: LanguageMeta[]): void {
  const usable = languages.filter(
    (language) => language && language.code.trim() && language.endonym.trim(),
  )
  if (usable.length === 0) return
  available = usable
  byCode = new Map(available.map((language) => [language.code, language]))
}

/**
 * Reads `i18n/meta.json` and adopts the languages it lists.
 *
 * Called once before i18next starts. Any failure is swallowed on purpose - a
 * portal that cannot read its language catalogue should still render in the
 * languages it shipped with, not refuse to start.
 */
export async function loadLanguageCatalogue(): Promise<void> {
  // Runtime base path, so a tenant-qualified deployment reads its own copy.
  const url = `${runtimeBasePath()}/i18n/meta.json`

  try {
    const response = await fetch(url)
    if (!response.ok) return
    const payload: unknown = await response.json()
    const languages = Array.isArray(payload)
      ? payload
      : (payload as { languages?: unknown })?.languages
    if (Array.isArray(languages)) {
      setAvailableLanguages(languages as LanguageMeta[])
    }
  } catch {
    // Keep the bundled set.
  }
}

export function getLanguageMeta(code: string): LanguageMeta | undefined {
  return byCode.get(code)
}

/**
 * Apply document-level side effects for a language: set the lang attribute, so
 * assistive technology and the browser's own text handling know which language
 * the page is in. Safe to call in any environment (guards against a missing
 * document, e.g. during SSR/tests).
 *
 * The portal renders left-to-right in every language, including those written
 * in Perso-Arabic script, so no direction is applied here.
 */
export function applyLanguageSideEffects(code: string): void {
  if (typeof document === 'undefined') return
  document.documentElement.setAttribute('lang', code)
}

/**
 * Persisted as a cookie rather than browser script-accessible storage: this
 * codebase forbids that outright (see scripts/verify-production-security.mjs)
 * since it is a standing XSS target, whereas a plain preference cookie
 * holding nothing sensitive is not.
 */
function readLanguageCookie(): string | undefined {
  if (typeof document === 'undefined') return undefined
  const prefix = `${encodeURIComponent(LANGUAGE_STORAGE_KEY)}=`
  const match = document.cookie
    .split(';')
    .map((entry) => entry.trim())
    .find((entry) => entry.startsWith(prefix))
  if (!match) return undefined
  try {
    return decodeURIComponent(match.slice(prefix.length))
  } catch {
    return undefined
  }
}

function writeLanguageCookie(code: string): void {
  if (typeof document === 'undefined') return
  document.cookie =
    `${encodeURIComponent(LANGUAGE_STORAGE_KEY)}=${encodeURIComponent(code)}; ` +
    `max-age=${LANGUAGE_COOKIE_MAX_AGE_SECONDS}; path=/; SameSite=Lax`
}

/** Read the persisted language, falling back to the default. */
export function readStoredLanguage(): string {
  const stored = readLanguageCookie()
  if (stored && byCode.has(stored)) return stored
  return DEFAULT_LANGUAGE
}

/** Persist the language choice. */
export function storeLanguage(code: string): void {
  writeLanguageCookie(code)
}
