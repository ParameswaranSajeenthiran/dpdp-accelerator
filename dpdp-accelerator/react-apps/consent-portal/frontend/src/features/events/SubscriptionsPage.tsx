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
import type {
  DeliveryMode,
  SubscriptionFilters as SubscriptionFiltersModel,
  SubscriptionRecord,
  SubscriptionStatus,
} from '../../types/subscription'
import {
  isDeliveryMode,
  isSubscriptionStatus,
} from '../../types/subscription'
import { PORTAL_SCOPES } from '../../utils/portalScopes'
import useAuthorization from '../auth/useAuthorization'
import SubscriptionDeleteDialog from './components/SubscriptionDeleteDialog'
import SubscriptionFilters from './components/SubscriptionFilters'
import SubscriptionRegisterDialog from './components/SubscriptionRegisterDialog'
import SubscriptionTable from './components/SubscriptionTable'
import {
  useCreateSubscriptionMutation,
  useDeleteSubscriptionMutation,
  useSubscriptionsQuery,
  useVerifySubscriptionMutation,
} from './hooks/useSubscriptionQueries'

const DEFAULT_FILTERS: SubscriptionFiltersModel = {
  status: 'All',
  deliveryMode: 'All',
  search: '',
}

const DEFAULT_PAGE = 0
const DEFAULT_ROWS_PER_PAGE = 10

function getFiltersFromSearchParams(searchParams: URLSearchParams): SubscriptionFiltersModel {
  const statusParam = searchParams.get('status') ?? ''
  const modeParam = searchParams.get('deliveryMode') ?? ''

  return {
    status: isSubscriptionStatus(statusParam)
      ? (statusParam.toUpperCase() as SubscriptionStatus)
      : DEFAULT_FILTERS.status,
    deliveryMode: isDeliveryMode(modeParam)
      ? (modeParam.toLowerCase() as DeliveryMode)
      : DEFAULT_FILTERS.deliveryMode,
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
  filters: SubscriptionFiltersModel,
  page = DEFAULT_PAGE,
  rowsPerPage = DEFAULT_ROWS_PER_PAGE,
): URLSearchParams {
  const params = new URLSearchParams()

  if (filters.status !== DEFAULT_FILTERS.status) {
    params.set('status', filters.status)
  }

  if (filters.deliveryMode !== DEFAULT_FILTERS.deliveryMode) {
    params.set('deliveryMode', filters.deliveryMode)
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

export default function SubscriptionsPage(): React.JSX.Element {
  const { t } = useTranslation('common')
  const navigate = useNavigate()
  const [searchParams, setSearchParams] = useSearchParams()

  const [isRegisterOpen, setIsRegisterOpen] = useState(false)
  const [selectedDeleteSubscription, setSelectedDeleteSubscription] = useState<
    SubscriptionRecord | undefined
  >()
  const [snackbarMessage, setSnackbarMessage] = useState<string | null>(null)

  const filters = useMemo(() => getFiltersFromSearchParams(searchParams), [searchParams])
  const page = useMemo(() => getPageFromSearchParams(searchParams), [searchParams])
  const rowsPerPage = useMemo(
    () => getRowsPerPageFromSearchParams(searchParams),
    [searchParams],
  )

  const subscriptionsQuery = useSubscriptionsQuery(filters, page, rowsPerPage)
  const createMutation = useCreateSubscriptionMutation()
  const deleteMutation = useDeleteSubscriptionMutation()
  const verifyMutation = useVerifySubscriptionMutation()

  const { hasScope } = useAuthorization()
  const canWrite = hasScope(PORTAL_SCOPES.EVENT_SUBSCRIPTIONS_WRITE)
  const isTableLoading = subscriptionsQuery.isPending || subscriptionsQuery.isPlaceholderData

  const updateParams = (
    nextFilters: SubscriptionFiltersModel,
    nextPage = DEFAULT_PAGE,
    nextRowsPerPage = rowsPerPage,
  ): void => {
    setSearchParams(toSearchParams(nextFilters, nextPage, nextRowsPerPage), { replace: true })
  }

  const handleVerify = (sub: SubscriptionRecord): void => {
    verifyMutation.mutate(sub.subscriptionId, {
      onSuccess: () => {
        setSnackbarMessage(t('subscriptions.verification.success', 'Verification triggered successfully.'))
      },
      onError: (err) => {
        setSnackbarMessage(err.message || t('subscriptions.verification.failed', 'Verification failed.'))
      },
    })
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
              {t('subscriptions.title')}
            </Typography>
            <Typography variant="body2" color="text.secondary">
              {t('subscriptions.subtitle')}
            </Typography>
          </Stack>

          {canWrite ? (
            <Button
              variant="contained"
              color="primary"
              startIcon={<Plus size={16} />}
              onClick={() => setIsRegisterOpen(true)}
            >
              {t('subscriptions.actions.register')}
            </Button>
          ) : null}
        </Stack>

        <SubscriptionFilters
          key={searchParams.toString()}
          filters={filters}
          onFilterChange={(nextFilters) => updateParams(nextFilters)}
          onClear={() => updateParams(DEFAULT_FILTERS)}
        />

        <SubscriptionTable
          rows={subscriptionsQuery.data?.rows ?? []}
          isLoading={isTableLoading}
          isError={subscriptionsQuery.isError}
          rowsPerPage={rowsPerPage}
          hasPreviousPage={page > DEFAULT_PAGE}
          hasNextPage={subscriptionsQuery.data?.hasNextPage ?? false}
          canWrite={canWrite}
          isMutating={
            createMutation.isPending || deleteMutation.isPending || verifyMutation.isPending
          }
          onPreviousPage={() => updateParams(filters, page - 1)}
          onNextPage={() => updateParams(filters, page + 1)}
          onRowsPerPageChange={(nextRowsPerPage) =>
            updateParams(filters, DEFAULT_PAGE, nextRowsPerPage)
          }
          onRetry={() => subscriptionsQuery.refetch()}
          onViewDetails={(sub) => navigate(`/events/subscriptions/${encodeURIComponent(sub.subscriptionId)}`)}
          onVerify={handleVerify}
          onDelete={setSelectedDeleteSubscription}
        />

        {isRegisterOpen ? (
          <SubscriptionRegisterDialog
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

        {selectedDeleteSubscription ? (
          <SubscriptionDeleteDialog
            open
            subscription={selectedDeleteSubscription}
            loading={deleteMutation.isPending}
            error={deleteMutation.error?.message}
            onClose={() => {
              setSelectedDeleteSubscription(undefined)
              deleteMutation.reset()
            }}
            onConfirm={() => {
              deleteMutation.mutate(selectedDeleteSubscription.subscriptionId, {
                onSuccess: () => setSelectedDeleteSubscription(undefined),
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
            severity={verifyMutation.isError ? 'error' : 'success'}
            sx={{ width: '100%' }}
          >
            {snackbarMessage}
          </Alert>
        </Snackbar>
      </Stack>
    </Box>
  )
}
