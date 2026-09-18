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

import { describe, expect, it } from 'vitest'
import { pageCountFromCursor, pageCountFromOverfetch } from '../utils/cursorPagination'

const NEXT_LINK = [{ rel: 'next', href: 'https://x?after=Mg==' }]

describe('pageCountFromCursor', () => {
  it('is exact when the page came back short of the limit', () => {
    expect(pageCountFromCursor(42, NEXT_LINK, 100)).toEqual({ count: 42, isAtLeast: false })
    expect(pageCountFromCursor(42, undefined, 100)).toEqual({ count: 42, isAtLeast: false })
  })

  it('is exact when the page is full but there is no next link', () => {
    expect(pageCountFromCursor(100, [], 100)).toEqual({ count: 100, isAtLeast: false })
  })

  it('is a floor ("100+") only when the page is full AND a next link remains', () => {
    expect(pageCountFromCursor(100, NEXT_LINK, 100)).toEqual({ count: 100, isAtLeast: true })
  })
})

describe('pageCountFromOverfetch', () => {
  it('is exact when fewer than limit+1 items came back', () => {
    expect(pageCountFromOverfetch(42, 100)).toEqual({ count: 42, isAtLeast: false })
    expect(pageCountFromOverfetch(100, 100)).toEqual({ count: 100, isAtLeast: false })
  })

  it('is a floor at the limit when more than limit items came back', () => {
    expect(pageCountFromOverfetch(101, 100)).toEqual({ count: 100, isAtLeast: true })
  })
})
