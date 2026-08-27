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
import type { TopicFilters as TopicFiltersModel, TopicStatus } from '../../../types/topic'
import { TOPIC_STATUSES } from '../../../types/topic'

interface TopicFiltersProps {
  filters: TopicFiltersModel
  onFilterChange: (filters: TopicFiltersModel) => void
  onClear: () => void
}

export default function TopicFilters({
  filters,
  onFilterChange,
  onClear,
}: TopicFiltersProps): React.JSX.Element {
  const { t } = useTranslation('common')
  const [search, setSearch] = useState(filters.search)
  const [status, setStatus] = useState<'All' | TopicStatus>(filters.status)

  const isFiltered = filters.status !== 'All' || Boolean(filters.search)

  const handleSearchSubmit = (event: React.FormEvent): void => {
    event.preventDefault()
    onFilterChange({ status, search: search.trim() })
  }

  const handleStatusChange = (nextStatus: 'All' | TopicStatus): void => {
    setStatus(nextStatus)
    onFilterChange({ status: nextStatus, search: search.trim() })
  }

  const handleClear = (): void => {
    setSearch('')
    setStatus('All')
    onClear()
  }

  return (
    <Stack
      component="form"
      onSubmit={handleSearchSubmit}
      direction={{ xs: 'column', sm: 'row' }}
      spacing={2}
      alignItems="center"
    >
      <TextField
        size="small"
        placeholder={t('topics.filters.searchPlaceholder')}
        value={search}
        onChange={(event) => setSearch(event.target.value)}
        InputProps={{
          startAdornment: <Search size={16} style={{ marginRight: 8, opacity: 0.6 }} />,
        }}
        sx={{ minWidth: 260, flex: 1 }}
      />

      <TextField
        select
        size="small"
        label={t('topics.filters.status')}
        value={status}
        onChange={(event) => handleStatusChange(event.target.value as 'All' | TopicStatus)}
        sx={{ minWidth: 160 }}
      >
        <MenuItem value="All">{t('topics.filters.allStatuses')}</MenuItem>
        {TOPIC_STATUSES.map((option) => (
          <MenuItem key={option} value={option}>
            {t(`topics.status.${option.toLowerCase()}`)}
          </MenuItem>
        ))}
      </TextField>

      <Button size="small" type="submit" variant="outlined">
        {t('topics.filters.search')}
      </Button>

      {isFiltered ? (
        <Button size="small" startIcon={<X size={15} />} onClick={handleClear}>
          {t('topics.filters.clear')}
        </Button>
      ) : null}
    </Stack>
  )
}
