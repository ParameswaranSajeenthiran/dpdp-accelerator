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

import { Box, Button, Stack, Typography } from '@wso2/oxygen-ui'
import { Plus } from '@wso2/oxygen-ui-icons-react'
import { useMemo, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { useSearchParams } from 'react-router-dom'
import HeaderBreadcrumbs from '../../components/layout/main-layout/HeaderBreadcrumbs'
import type { TopicFilters as TopicFiltersModel, TopicRecord, TopicStatus } from '../../types/topic'
import { isTopicStatus } from '../../types/topic'
import { REQUIRED_SCOPES } from '../../utils/scopes'
import useAuthorization from '../auth/useAuthorization'
import TopicDeleteDialog from './components/TopicDeleteDialog'
import TopicFilters from './components/TopicFilters'
import TopicRegisterDialog from './components/TopicRegisterDialog'
import TopicTable from './components/TopicTable'
import {
  useCreateTopicMutation,
  useDeleteTopicMutation,
  useTopicsQuery,
} from './hooks/useTopicQueries'

const DEFAULT_FILTERS: TopicFiltersModel = {
  status: 'All',
  search: '',
}

const DEFAULT_PAGE = 0
const DEFAULT_ROWS_PER_PAGE = 10

function getFiltersFromSearchParams(searchParams: URLSearchParams): TopicFiltersModel {
  const statusParam = searchParams.get('status') ?? ''

  return {
    status: isTopicStatus(statusParam) ? (statusParam as TopicStatus) : DEFAULT_FILTERS.status,
    search: searchParams.get('search') ?? DEFAULT_FILTERS.search,
  }
}

function getPageFromSearchParams(searchParams: URLSearchParams): number {
  const pageNumber = Number(searchParams.get('page') ?? '1')

  return Number.isInteger(pageNumber) && pageNumber > 0 ? pageNumber - 1 : DEFAULT_PAGE
}

function getRowsPerPageFromSearchParams(searchParams: URLSearchParams): number {
  const rowsPerPage = Number(searchParams.get('rowsPerPage') ?? String(DEFAULT_ROWS_PER_PAGE))

  return [10, 20, 50].includes(rowsPerPage) ? rowsPerPage : DEFAULT_ROWS_PER_PAGE
}

function toSearchParams(
  filters: TopicFiltersModel,
  page = DEFAULT_PAGE,
  rowsPerPage = DEFAULT_ROWS_PER_PAGE,
): URLSearchParams {
  const params = new URLSearchParams()

  if (filters.status !== DEFAULT_FILTERS.status) {
    params.set('status', filters.status)
  }

  if (filters.search.trim()) {
    params.set('search', filters.search.trim())
  }

  if (page !== DEFAULT_PAGE) {
    params.set('page', String(page + 1))
  }

  if (rowsPerPage !== DEFAULT_ROWS_PER_PAGE) {
    params.set('rowsPerPage', String(rowsPerPage))
  }

  return params
}

export default function TopicsPage(): React.JSX.Element {
  const { t } = useTranslation('common')
  const [searchParams, setSearchParams] = useSearchParams()
  const [isRegisterOpen, setIsRegisterOpen] = useState(false)
  const [selectedDeleteTopic, setSelectedDeleteTopic] = useState<TopicRecord | undefined>()

  const filters = useMemo(() => getFiltersFromSearchParams(searchParams), [searchParams])
  const page = useMemo(() => getPageFromSearchParams(searchParams), [searchParams])
  const rowsPerPage = useMemo(() => getRowsPerPageFromSearchParams(searchParams), [searchParams])

  const topicsQuery = useTopicsQuery(filters, page, rowsPerPage)
  const createMutation = useCreateTopicMutation()
  const deleteMutation = useDeleteTopicMutation()

  const { hasScope } = useAuthorization()
  const canWrite = hasScope(REQUIRED_SCOPES.EVENT_TOPICS_WRITE)
  const isTableLoading = topicsQuery.isPending || topicsQuery.isPlaceholderData

  const updateParams = (
    nextFilters: TopicFiltersModel,
    nextPage = DEFAULT_PAGE,
    nextRowsPerPage = rowsPerPage,
  ): void => {
    setSearchParams(toSearchParams(nextFilters, nextPage, nextRowsPerPage), { replace: true })
  }

  return (
    <Box component="main" sx={{ p: { xs: 2, md: 4 } }}>
      <Stack spacing={3}>
        <Stack
          direction={{ xs: 'column', sm: 'row' }}
          justifyContent="space-between"
          alignItems={{ xs: 'flex-start', sm: 'center' }}
          spacing={2}
        >
          <Stack spacing={1}>
            <HeaderBreadcrumbs />
            <Typography variant="h4" fontWeight={700}>
              {t('topics.title')}
            </Typography>
            <Typography variant="body2" color="text.secondary">
              {t('topics.subtitle')}
            </Typography>
          </Stack>

          {canWrite ? (
            <Button
              variant="contained"
              color="primary"
              startIcon={<Plus size={16} />}
              onClick={() => setIsRegisterOpen(true)}
            >
              {t('topics.actions.register')}
            </Button>
          ) : null}
        </Stack>

        <TopicFilters
          key={searchParams.toString()}
          filters={filters}
          onFilterChange={(nextFilters) => updateParams(nextFilters)}
          onClear={() => updateParams(DEFAULT_FILTERS)}
        />

        <TopicTable
          rows={topicsQuery.data?.rows ?? []}
          isLoading={isTableLoading}
          isError={topicsQuery.isError}
          rowsPerPage={rowsPerPage}
          hasPreviousPage={page > DEFAULT_PAGE}
          hasNextPage={topicsQuery.data?.hasNextPage ?? false}
          canWrite={canWrite}
          isMutating={createMutation.isPending || deleteMutation.isPending}
          onPreviousPage={() => updateParams(filters, page - 1)}
          onNextPage={() => updateParams(filters, page + 1)}
          onRowsPerPageChange={(nextRowsPerPage) =>
            updateParams(filters, DEFAULT_PAGE, nextRowsPerPage)
          }
          onRetry={() => topicsQuery.refetch()}
          onDelete={setSelectedDeleteTopic}
        />

        {isRegisterOpen ? (
          <TopicRegisterDialog
            open
            loading={createMutation.isPending}
            error={createMutation.error?.message}
            onClose={() => {
              setIsRegisterOpen(false)
              createMutation.reset()
            }}
            onSubmit={(payload) => {
              createMutation.mutate(payload, {
                onSuccess: () => setIsRegisterOpen(false),
              })
            }}
          />
        ) : null}

        {selectedDeleteTopic ? (
          <TopicDeleteDialog
            open
            topic={selectedDeleteTopic}
            loading={deleteMutation.isPending}
            error={deleteMutation.error?.message}
            onClose={() => {
              setSelectedDeleteTopic(undefined)
              deleteMutation.reset()
            }}
            onConfirm={() => {
              deleteMutation.mutate(selectedDeleteTopic.topicId, {
                onSuccess: () => setSelectedDeleteTopic(undefined),
              })
            }}
          />
        ) : null}
      </Stack>
    </Box>
  )
}
