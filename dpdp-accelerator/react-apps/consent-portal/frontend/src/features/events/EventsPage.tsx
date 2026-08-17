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

import { Alert, Box, Button, Snackbar, Stack, Typography } from '@wso2/oxygen-ui'
import { Plus } from '@wso2/oxygen-ui-icons-react'
import { useMemo, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { useNavigate, useSearchParams } from 'react-router-dom'
import HeaderBreadcrumbs from '../../components/layout/main-layout/HeaderBreadcrumbs'
import type { EventFilters as EventFiltersModel } from '../../types/event'
import { PORTAL_SCOPES } from '../../utils/portalScopes'
import useAuthorization from '../auth/useAuthorization'
import EventFilters from './components/EventFilters'
import EventPublishDialog from './components/EventPublishDialog'
import EventTable from './components/EventTable'
import { useEventsQuery, usePublishEventMutation } from './hooks/useEventQueries'

const DEFAULT_FILTERS: EventFiltersModel = {
  search: '',
  status: 'All',
  topic: 'All',
  groupId: '',
}

const DEFAULT_PAGE = 0
const DEFAULT_ROWS_PER_PAGE = 10

function getFiltersFromSearchParams(searchParams: URLSearchParams): EventFiltersModel {
  return {
    search: searchParams.get('search') ?? DEFAULT_FILTERS.search,
    status: searchParams.get('status') ?? DEFAULT_FILTERS.status,
    topic: searchParams.get('topic') ?? DEFAULT_FILTERS.topic,
    groupId: searchParams.get('groupId') ?? DEFAULT_FILTERS.groupId,
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
  filters: EventFiltersModel,
  page = DEFAULT_PAGE,
  rowsPerPage = DEFAULT_ROWS_PER_PAGE,
): URLSearchParams {
  const params = new URLSearchParams()

  if (filters.search.trim()) {
    params.set('search', filters.search.trim())
  }

  if (filters.status && filters.status !== 'All') {
    params.set('status', filters.status)
  }

  if (filters.topic && filters.topic !== 'All') {
    params.set('topic', filters.topic)
  }

  if (filters.groupId.trim()) {
    params.set('groupId', filters.groupId.trim())
  }

  if (page !== DEFAULT_PAGE) {
    params.set('page', String(page + 1))
  }

  if (rowsPerPage !== DEFAULT_ROWS_PER_PAGE) {
    params.set('rowsPerPage', String(rowsPerPage))
  }

  return params
}

export default function EventsPage(): React.JSX.Element {
  const { t } = useTranslation('common')
  const navigate = useNavigate()
  const [searchParams, setSearchParams] = useSearchParams()
  const [isPublishOpen, setIsPublishOpen] = useState(false)
  const [snackbarMessage, setSnackbarMessage] = useState<string | null>(null)

  const filters = useMemo(() => getFiltersFromSearchParams(searchParams), [searchParams])
  const page = useMemo(() => getPageFromSearchParams(searchParams), [searchParams])
  const rowsPerPage = useMemo(() => getRowsPerPageFromSearchParams(searchParams), [searchParams])

  const eventsQuery = useEventsQuery(filters, page, rowsPerPage)
  const publishMutation = usePublishEventMutation()

  const { hasScope } = useAuthorization()
  const canWrite = hasScope(PORTAL_SCOPES.EVENTS_WRITE)
  const isTableLoading = eventsQuery.isPending || eventsQuery.isPlaceholderData

  const updateParams = (
    nextFilters: EventFiltersModel,
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
              {t('events.title')}
            </Typography>
            <Typography variant="body2" color="text.secondary">
              {t('events.subtitle')}
            </Typography>
          </Stack>

          {canWrite ? (
            <Button
              variant="contained"
              color="primary"
              startIcon={<Plus size={16} />}
              onClick={() => setIsPublishOpen(true)}
            >
              {t('events.actions.publish')}
            </Button>
          ) : null}
        </Stack>

        <EventFilters
          key={searchParams.toString()}
          filters={filters}
          onFilterChange={(nextFilters) => updateParams(nextFilters)}
          onClear={() => updateParams(DEFAULT_FILTERS)}
        />

        <EventTable
          rows={eventsQuery.data?.rows ?? []}
          isLoading={isTableLoading}
          isError={eventsQuery.isError}
          rowsPerPage={rowsPerPage}
          hasPreviousPage={page > DEFAULT_PAGE}
          hasNextPage={eventsQuery.data?.hasNextPage ?? false}
          onPreviousPage={() => updateParams(filters, page - 1)}
          onNextPage={() => updateParams(filters, page + 1)}
          onRowsPerPageChange={(nextRowsPerPage) =>
            updateParams(filters, DEFAULT_PAGE, nextRowsPerPage)
          }
          onRetry={() => eventsQuery.refetch()}
          onViewDetails={(event) => {
            navigate(`/events/${encodeURIComponent(event.eventId)}`)
          }}
        />

        {isPublishOpen ? (
          <EventPublishDialog
            open
            loading={publishMutation.isPending}
            error={publishMutation.error?.message}
            onClose={() => {
              setIsPublishOpen(false)
              publishMutation.reset()
            }}
            onSubmit={(payload) => {
              publishMutation.mutate(payload, {
                onSuccess: () => {
                  setIsPublishOpen(false)
                  setSnackbarMessage(t('events.dialog.publishSuccess'))
                },
              })
            }}
          />
        ) : null}

        <Snackbar
          open={Boolean(snackbarMessage)}
          autoHideDuration={4000}
          onClose={() => setSnackbarMessage(null)}
          anchorOrigin={{ vertical: 'bottom', horizontal: 'right' }}
        >
          <Alert
            onClose={() => setSnackbarMessage(null)}
            severity="success"
            sx={{ width: '100%' }}
          >
            {snackbarMessage}
          </Alert>
        </Snackbar>
      </Stack>
    </Box>
  )
}
