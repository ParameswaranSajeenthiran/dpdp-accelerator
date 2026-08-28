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

import i18n from '../i18n/i18n'

const EMPTY_DATE_PLACEHOLDER = '-'
// Values below this threshold are treated as epoch seconds, not milliseconds.
// The cutoff is before all expected consent timestamps.
const EPOCH_MILLISECONDS_CUTOFF = 100_000_000_000

export const DEFAULT_DATE_TIME_FORMAT_OPTIONS: Intl.DateTimeFormatOptions = {
  year: 'numeric',
  month: '2-digit',
  day: '2-digit',
  hour: '2-digit',
  minute: '2-digit',
}

export function toEpochMilliseconds(
  epochTimestamp: number | string | null | undefined,
): number | null {
  if (epochTimestamp == null) {
    return null
  }

  if (typeof epochTimestamp === 'string') {
    const trimmed = epochTimestamp.trim()
    if (trimmed === '') {
      return null
    }

    const numeric = Number(trimmed)
    if (Number.isFinite(numeric)) {
      return numeric < EPOCH_MILLISECONDS_CUTOFF ? numeric * 1000 : numeric
    }

    const parsedDate = new Date(trimmed)
    const time = parsedDate.getTime()
    return Number.isNaN(time) ? null : time
  }

  if (!Number.isFinite(epochTimestamp)) {
    return null
  }

  return epochTimestamp < EPOCH_MILLISECONDS_CUTOFF ? epochTimestamp * 1000 : epochTimestamp
}

export function formatEpochTimestamp(
  epochTimestamp: number | string | null | undefined,
  options?: Intl.DateTimeFormatOptions,
  locales?: Intl.LocalesArgument,
): string {
  const epochMilliseconds = toEpochMilliseconds(epochTimestamp)

  if (epochMilliseconds == null) {
    return EMPTY_DATE_PLACEHOLDER
  }

  // Defaults to the active portal language rather than the browser's own
  // locale, so a reader who switched the UI to Hindi also gets Hindi-
  // formatted dates and times, not just translated labels around them.
  return new Date(epochMilliseconds).toLocaleString(
    locales ?? i18n.language,
    options ?? DEFAULT_DATE_TIME_FORMAT_OPTIONS,
  )
}

/**
 * Parses a `YYYY-MM-DD` date-only string (as produced by a date picker) into
 * a local `Date`. Deliberately does not use `new Date(value)`, which parses
 * a bare date as UTC midnight and can read back as the previous day in a
 * negative UTC-offset timezone.
 */
export function parseDateOnly(value: string): Date | undefined {
  if (!value) {
    return undefined
  }

  const match = /^(\d{4})-(\d{2})-(\d{2})$/.exec(value)
  if (!match) {
    return undefined
  }

  const [, year, month, day] = match
  return new Date(Number(year), Number(month) - 1, Number(day))
}

/** Midnight at the start of the given date's local day, as epoch milliseconds. */
export function startOfDayMillis(date: Date): number {
  return new Date(date.getFullYear(), date.getMonth(), date.getDate()).getTime()
}

/** The last millisecond of the given date's local day, as epoch milliseconds. */
export function endOfDayMillis(date: Date): number {
  return new Date(date.getFullYear(), date.getMonth(), date.getDate(), 23, 59, 59, 999).getTime()
}

export function formatIsoDateTime(
  dateTimeText: string | null | undefined,
  options?: Intl.DateTimeFormatOptions,
  locales?: Intl.LocalesArgument,
): string {
  if (!dateTimeText) {
    return EMPTY_DATE_PLACEHOLDER
  }

  const parsedDate = new Date(dateTimeText)

  if (Number.isNaN(parsedDate.getTime())) {
    return EMPTY_DATE_PLACEHOLDER
  }

  return parsedDate.toLocaleString(
    locales ?? i18n.language,
    options ?? DEFAULT_DATE_TIME_FORMAT_OPTIONS,
  )
}
