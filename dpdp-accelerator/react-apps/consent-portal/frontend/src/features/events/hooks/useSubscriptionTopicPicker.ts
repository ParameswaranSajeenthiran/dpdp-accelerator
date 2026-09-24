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

import { useQuery, type UseQueryResult } from '@tanstack/react-query'
import { useEffect, useState } from 'react'
import { fetchTopics } from '../api/topicsApi'
import { MAX_SUBSCRIPTION_TOPICS } from '../constants'
import type { TopicListResponse } from '../../../types/topic'

export async function collectMatchingTopics(search: string, selected: string[]): Promise<string[]> {
  const names = new Set(selected)
  const page = await fetchTopics({
    status: 'ACTIVE',
    search: search.trim() || undefined,
    limit: 100,
    offset: 0,
  })
  page.items.forEach((topic) => names.add(topic.name))
  if (page.total > MAX_SUBSCRIPTION_TOPICS || names.size > MAX_SUBSCRIPTION_TOPICS) {
    throw new RangeError('Topic selection exceeds the subscription limit')
  }
  return [...names]
}

export default function useSubscriptionTopicPicker(
  search: string,
): UseQueryResult<TopicListResponse> {
  const [debouncedSearch, setDebouncedSearch] = useState(search)
  useEffect(() => {
    const timer = window.setTimeout(() => setDebouncedSearch(search), 300)
    return () => window.clearTimeout(timer)
  }, [search])
  return useQuery({
    queryKey: ['subscription-topic-picker', debouncedSearch],
    queryFn: () =>
      fetchTopics({
        status: 'ACTIVE',
        search: debouncedSearch.trim() || undefined,
        limit: 100,
        offset: 0,
      }),
  })
}
