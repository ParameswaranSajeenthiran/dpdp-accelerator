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

import { Alert, Box, Button, Stack, Typography } from '@wso2/oxygen-ui'
import { Plus } from '@wso2/oxygen-ui-icons-react'
import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import HeaderBreadcrumbs from '../../components/layout/main-layout/HeaderBreadcrumbs'
import ComplaintListFilters, {
  type ComplaintListStatusFilter,
} from './components/ComplaintListFilters'
import ComplaintListTable from './components/ComplaintListTable'
import ComplaintSubmitDialog from './components/ComplaintSubmitDialog'
import { useMyComplaintListQuery } from './hooks/useComplaintQueries'

const DEFAULT_PAGE = 0
const DEFAULT_ROWS_PER_PAGE = 10

function ComplaintListPage(): React.JSX.Element {
  const { t } = useTranslation('common')
  const [isSubmitDialogOpen, setIsSubmitDialogOpen] = useState<boolean>(false)
  const [submittedComplaint, setSubmittedComplaint] = useState<{
    referenceId: string
    attachmentUploadFailed: boolean
  } | null>(null)
  const [statusFilter, setStatusFilter] = useState<ComplaintListStatusFilter>('All')
  const [page, setPage] = useState(DEFAULT_PAGE)
  const [rowsPerPage, setRowsPerPage] = useState(DEFAULT_ROWS_PER_PAGE)

  const listQuery = useMyComplaintListQuery({
    status: statusFilter === 'All' ? undefined : statusFilter,
    limit: rowsPerPage,
    offset: page * rowsPerPage,
  })
  const rows = listQuery.data?.rows ?? []
  const total = listQuery.data?.total ?? 0

  return (
    <Box component="main" sx={{ p: { xs: 2, md: 4 } }}>
      <Stack spacing={3}>
        <Stack
          direction={{ xs: 'column', sm: 'row' }}
          spacing={2}
          alignItems={{ sm: 'center' }}
          justifyContent="space-between"
        >
          <Stack spacing={1}>
            <HeaderBreadcrumbs currentLabel="" />
            <Typography variant="h4" fontWeight={700}>
              {t('complaints.list.title')}
            </Typography>
            <Typography variant="body2" color="text.secondary">
              {t('complaints.list.subtitle')}
            </Typography>
          </Stack>
          <Button
            variant="contained"
            startIcon={<Plus size={16} />}
            onClick={() => {
              setIsSubmitDialogOpen(true)
            }}
          >
            {t('complaints.list.submitNew')}
          </Button>
        </Stack>

        {submittedComplaint ? (
          <Alert
            severity={submittedComplaint.attachmentUploadFailed ? 'warning' : 'success'}
            onClose={() => setSubmittedComplaint(null)}
          >
            {submittedComplaint.attachmentUploadFailed
              ? t('complaints.submit.success.attachmentFailedMessage', {
                  referenceId: submittedComplaint.referenceId,
                })
              : t('complaints.submit.success.message', {
                  referenceId: submittedComplaint.referenceId,
                })}
          </Alert>
        ) : null}

        <ComplaintListFilters
          status={statusFilter}
          onStatusChange={(nextStatus) => {
            setStatusFilter(nextStatus)
            setPage(DEFAULT_PAGE)
          }}
          onClear={() => {
            setStatusFilter('All')
            setPage(DEFAULT_PAGE)
          }}
        />

        <ComplaintListTable
          rows={rows}
          isLoading={listQuery.isPending || listQuery.isPlaceholderData}
          isError={listQuery.isError}
          isEmptyFiltered={statusFilter !== 'All'}
          rowsPerPage={rowsPerPage}
          hasPreviousPage={page > DEFAULT_PAGE}
          hasNextPage={(page + 1) * rowsPerPage < total}
          onPreviousPage={() => setPage((previousPage) => previousPage - 1)}
          onNextPage={() => setPage((previousPage) => previousPage + 1)}
          onRowsPerPageChange={(nextRowsPerPage) => {
            setRowsPerPage(nextRowsPerPage)
            setPage(DEFAULT_PAGE)
          }}
          onRetry={() => listQuery.refetch()}
        />
      </Stack>

      <ComplaintSubmitDialog
        open={isSubmitDialogOpen}
        onClose={() => setIsSubmitDialogOpen(false)}
        onSubmitted={(referenceId, attachmentUploadFailed) => {
          setIsSubmitDialogOpen(false)
          setSubmittedComplaint({ referenceId, attachmentUploadFailed })
        }}
      />
    </Box>
  )
}

export default ComplaintListPage
