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

import { Button, MenuItem, Stack, TextField } from '@wso2/oxygen-ui'
import { Search, X } from '@wso2/oxygen-ui-icons-react'
import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import type { EventFilters as EventFiltersModel } from '../../../types/event'
import { useTopicsQuery } from '../hooks/useTopicQueries'

const EVENT_STATUS_OPTIONS = [
  'PENDING',
  'DELIVERED',
  'FAILED',
  'COMPLETED',
  'ACKNOWLEDGED',
] as const

interface EventFiltersProps {
  filters: EventFiltersModel
  onFilterChange: (filters: EventFiltersModel) => void
  onClear: () => void
}

export default function EventFilters({
  filters,
  onFilterChange,
  onClear,
}: EventFiltersProps): React.JSX.Element {
  const { t } = useTranslation('common')
  const [search, setSearch] = useState(filters.search)
  const [status, setStatus] = useState(filters.status || 'All')
  const [topic, setTopic] = useState(filters.topic || 'All')
  const [subscriptionId, setSubscriptionId] = useState(filters.subscriptionId || '')

  const topicsQuery = useTopicsQuery({ status: 'ACTIVE', search: '' }, 0, 100)
  const topicRows = topicsQuery.data?.rows ?? []

  const isFiltered =
    (filters.status && filters.status !== 'All') ||
    (filters.topic && filters.topic !== 'All') ||
    Boolean(filters.groupId) ||
    Boolean(filters.subscriptionId) ||
    Boolean(filters.search)

  const handleSearchSubmit = (event: React.FormEvent): void => {
    event.preventDefault()
    onFilterChange({
      status,
      topic,
      groupId: filters.groupId,
      subscriptionId: subscriptionId.trim(),
      search: search.trim(),
    })
  }

  const handleStatusChange = (nextStatus: string): void => {
    setStatus(nextStatus)
    onFilterChange({
      status: nextStatus,
      topic,
      groupId: filters.groupId,
      subscriptionId: subscriptionId.trim(),
      search: search.trim(),
    })
  }

  const handleTopicChange = (nextTopic: string): void => {
    setTopic(nextTopic)
    onFilterChange({
      status,
      topic: nextTopic,
      groupId: filters.groupId,
      subscriptionId: subscriptionId.trim(),
      search: search.trim(),
    })
  }

  const handleClear = (): void => {
    setSearch('')
    setStatus('All')
    setTopic('All')
    setSubscriptionId('')
    onClear()
  }

  return (
    <Stack
      component="form"
      onSubmit={handleSearchSubmit}
      direction={{ xs: 'column', sm: 'row' }}
      flexWrap="wrap"
      useFlexGap
      spacing={2}
      alignItems="center"
    >
      <TextField
        size="small"
        placeholder={t('events.filters.searchPlaceholder')}
        value={search}
        onChange={(event) => setSearch(event.target.value)}
        InputProps={{
          startAdornment: <Search size={16} style={{ marginRight: 8, opacity: 0.6 }} />,
        }}
        sx={{ minWidth: 240, flex: 1 }}
      />

      <TextField
        size="small"
        placeholder={t('events.filters.subscriptionIdPlaceholder', 'Filter by Subscription ID...')}
        label={t('events.filters.subscriptionId', 'Subscription ID')}
        value={subscriptionId}
        onChange={(event) => setSubscriptionId(event.target.value)}
        sx={{ minWidth: 190 }}
      />

      <TextField
        select
        size="small"
        label={t('events.filters.status')}
        value={status}
        onChange={(event) => handleStatusChange(event.target.value)}
        sx={{ minWidth: 140 }}
      >
        <MenuItem value="All">{t('events.filters.allStatuses')}</MenuItem>
        {EVENT_STATUS_OPTIONS.map((option) => (
          <MenuItem key={option} value={option}>
            {t(`events.status.${option.toLowerCase()}`, option)}
          </MenuItem>
        ))}
      </TextField>

      <TextField
        select
        size="small"
        label={t('events.filters.topic')}
        value={topic}
        onChange={(event) => handleTopicChange(event.target.value)}
        sx={{ minWidth: 160 }}
      >
        <MenuItem value="All">{t('events.filters.allTopics')}</MenuItem>
        {topicRows.map((topicItem) => (
          <MenuItem key={topicItem.topicId} value={topicItem.name}>
            {topicItem.name}
          </MenuItem>
        ))}
      </TextField>

      <Button size="small" type="submit" variant="outlined">
        {t('events.filters.search')}
      </Button>

      {isFiltered ? (
        <Button size="small" startIcon={<X size={15} />} onClick={handleClear}>
          {t('events.filters.clear')}
        </Button>
      ) : null}
    </Stack>
  )
}
