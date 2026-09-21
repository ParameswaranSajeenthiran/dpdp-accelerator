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

import type { CursorLink } from '../types/catalog'

const CURSOR_PARAMETERS = ['after', 'before'] as const

/**
 * Reads the opaque cursor out of a pagination link.
 *
 * The Identity Server returns absolute upstream URLs in `links`, so only the
 * cursor query parameter is reusable by the portal.
 */
export function getCursorFromLinks(
  links: CursorLink[] | undefined,
  rel: 'next' | 'previous',
): string | undefined {
  const href = links?.find((link) => link.rel === rel)?.href

  if (!href) {
    return undefined
  }

  const queryStart = href.indexOf('?')

  if (queryStart === -1) {
    return undefined
  }

  const searchParams = new URLSearchParams(href.slice(queryStart + 1))

  return (
    CURSOR_PARAMETERS.map((parameter) => searchParams.get(parameter)).find(Boolean) ?? undefined
  )
}

export function getNextCursor(links: CursorLink[] | undefined): string | undefined {
  return getCursorFromLinks(links, 'next')
}

export function getPreviousCursor(links: CursorLink[] | undefined): string | undefined {
  return getCursorFromLinks(links, 'previous')
}

/** A count that may be exact, or a floor when the underlying page came up against a cap. */
export interface PageCount {
  count: number
  /** True when `count` is a floor (display as e.g. "100+"), not the exact total. */
  isAtLeast: boolean
}

/**
 * A count derived from one page of a cursor-paginated endpoint.
 *
 * These endpoints (WSO2 IS's consent-mgt v2.0 family: admin consents, purposes, elements)
 * report a `totalResults`-style field that turns out to just equal the page size, not a real
 * grand total. The only signal that's actually trustworthy is
 * whether a `next` link is offered: if the page came back short of `limit`, that's the exact
 * count; if the page is full AND there's a `next` link, there are more than `limit` matches.
 */
export function pageCountFromCursor(
  itemCount: number,
  links: CursorLink[] | undefined,
  limit: number,
): PageCount {
  const hasNext = Boolean(getNextCursor(links))
  return { count: itemCount, isAtLeast: itemCount >= limit && hasNext }
}

/**
 * A count derived from over-fetching a plain (non-cursor) array endpoint by one.
 *
 * Some endpoints (the self-service consent list) return a bare array with no pagination
 * metadata at all - not even an unreliable one. Ask for `limit + 1` and pass the array's actual
 * length here: getting back more than `limit` items is the only signal that more exist.
 */
export function pageCountFromOverfetch(itemCount: number, limit: number): PageCount {
  return itemCount > limit
    ? { count: limit, isAtLeast: true }
    : { count: itemCount, isAtLeast: false }
}
