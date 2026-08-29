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

import { keepPreviousData, useQuery } from '@tanstack/react-query'
import { useState } from 'react'

export const CONSENT_HISTORY_PAGE_SIZE = 5

export interface ConsentHistoryPage<T> {
  entries: T[]
  totalCount: number
  hasMore: boolean
  isLoading: boolean
  isFetching: boolean
  isError: boolean
  errorMessage?: string
  loadMore: () => void
}

interface ConsentHistoryPageResult<T> {
  entries: T[]
  totalCount: number
}

/**
 * Cumulative "load more" over a `limit`/`offset` paginated history endpoint.
 *
 * Grows `limit` and always refetches from `offset: 0` rather than appending pages, trading a
 * refetch of already-seen rows for not having to merge pages by hand - history lists here are
 * small and read rarely enough that the simplicity is worth it.
 */
export function useConsentHistoryPaging<T>(
  queryKey: readonly unknown[],
  fetchPage: (params: { limit: number; offset: number }) => Promise<ConsentHistoryPageResult<T>>,
  enabled: boolean,
): ConsentHistoryPage<T> {
  const [visibleLimit, setVisibleLimit] = useState(CONSENT_HISTORY_PAGE_SIZE)

  const query = useQuery({
    queryKey: [...queryKey, visibleLimit],
    queryFn: () => fetchPage({ limit: visibleLimit, offset: 0 }),
    enabled,
    placeholderData: keepPreviousData,
  })

  const entries = query.data?.entries ?? []
  const totalCount = query.data?.totalCount ?? 0

  return {
    entries,
    totalCount,
    hasMore: entries.length < totalCount,
    isLoading: query.isLoading,
    isFetching: query.isFetching,
    isError: query.isError,
    errorMessage: query.error instanceof Error ? query.error.message : undefined,
    loadMore: () => setVisibleLimit((current) => current + CONSENT_HISTORY_PAGE_SIZE),
  }
}
