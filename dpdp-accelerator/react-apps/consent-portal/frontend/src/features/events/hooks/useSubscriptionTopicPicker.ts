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

import {
  useInfiniteQuery,
  type UseInfiniteQueryResult,
  type InfiniteData,
} from '@tanstack/react-query'
import { useEffect, useState } from 'react'
import { fetchTopics } from '../api/topicsApi'
import { MAX_SUBSCRIPTION_TOPICS } from '../constants'
import type { TopicListResponse } from '../../../types/topic'

export async function collectMatchingTopics(search: string, selected: string[]): Promise<string[]> {
  const names = new Set(selected)
  const collect = async (offset: number): Promise<string[]> => {
    const page = await fetchTopics({
      status: 'ACTIVE',
      search: search.trim() || undefined,
      limit: 100,
      offset,
    })
    page.items.forEach((topic) => names.add(topic.name))
    if (page.total > MAX_SUBSCRIPTION_TOPICS || names.size > MAX_SUBSCRIPTION_TOPICS) {
      throw new RangeError('Topic selection exceeds the subscription limit')
    }
    const next = offset + page.items.length
    if (next < page.total) {
      if (!page.items.length) throw new Error('Incomplete topic results')
      return collect(next)
    }
    return [...names]
  }
  return collect(0)
}

export default function useSubscriptionTopicPicker(
  search: string,
): UseInfiniteQueryResult<InfiniteData<TopicListResponse>> {
  const [debouncedSearch, setDebouncedSearch] = useState(search)
  useEffect(() => {
    const timer = window.setTimeout(() => setDebouncedSearch(search), 300)
    return () => window.clearTimeout(timer)
  }, [search])
  return useInfiniteQuery({
    queryKey: ['subscription-topic-picker', debouncedSearch],
    initialPageParam: 0,
    queryFn: ({ pageParam }) =>
      fetchTopics({
        status: 'ACTIVE',
        search: debouncedSearch.trim() || undefined,
        limit: 30,
        offset: pageParam,
      }),
    getNextPageParam: (lastPage, pages) => {
      const loaded = pages.reduce((count, page) => count + page.items.length, 0)
      return lastPage.items.length > 0 && loaded < lastPage.total ? loaded : undefined
    },
  })
}
