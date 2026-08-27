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
  Alert,
  Button,
  Chip,
  IconButton,
  LinearProgress,
  Paper,
  Stack,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableRow,
  Tooltip,
  Typography,
} from '@wso2/oxygen-ui'
import { Eye, RefreshCw, Trash2 } from '@wso2/oxygen-ui-icons-react'
import { useTranslation } from 'react-i18next'
import CopyableText from '../../../components/CopyableText'
import CursorPaginationFooter from '../../../components/CursorPaginationFooter'
import type { SubscriptionRecord } from '../../../types/subscription'
import { getSubscriptionStatusChipColor } from '../utils/subscriptionStatusChip'

const ROWS_PER_PAGE_OPTIONS = [10, 20, 50] as const

interface SubscriptionTableProps {
  rows: SubscriptionRecord[]
  isLoading: boolean
  isError: boolean
  rowsPerPage: number
  hasPreviousPage: boolean
  hasNextPage: boolean
  canWrite: boolean
  isMutating?: boolean
  onPreviousPage: () => void
  onNextPage: () => void
  onRowsPerPageChange: (rowsPerPage: number) => void
  onRetry: () => void
  onViewDetails: (subscription: SubscriptionRecord) => void
  onVerify: (subscription: SubscriptionRecord) => void
  onDelete: (subscription: SubscriptionRecord) => void
}

export default function SubscriptionTable({
  rows,
  isLoading,
  isError,
  rowsPerPage,
  hasPreviousPage,
  hasNextPage,
  canWrite,
  isMutating = false,
  onPreviousPage,
  onNextPage,
  onRowsPerPageChange,
  onRetry,
  onViewDetails,
  onVerify,
  onDelete,
}: SubscriptionTableProps): React.JSX.Element {
  const { t } = useTranslation('common')

  if (isError) {
    return (
      <Paper sx={{ p: 3, textAlign: 'center' }}>
        <Alert severity="error" sx={{ mb: 2 }}>
          {t('subscriptions.loadFailed')}
        </Alert>
        <Button variant="outlined" onClick={onRetry}>
          {t('authorization.tryAgain')}
        </Button>
      </Paper>
    )
  }

  return (
    <Paper sx={{ width: '100%', overflow: 'hidden', boxShadow: 1 }}>
      {isLoading ? <LinearProgress /> : null}
      <TableContainer>
        <Table aria-label={t('subscriptions.table.ariaLabel')}>
          <TableHead
            sx={(theme) => ({
              '& .MuiTableCell-head': {
                fontWeight: 600,
                ...theme.applyStyles('light', { backgroundColor: theme.palette.grey[50] }),
                ...theme.applyStyles('dark', { backgroundColor: 'rgba(255, 255, 255, 0.04)' }),
              },
            })}
          >
            <TableRow>
              <TableCell>{t('subscriptions.table.subscriptionId')}</TableCell>
              <TableCell>{t('subscriptions.table.topic')}</TableCell>
              <TableCell>{t('subscriptions.table.groupId')}</TableCell>
              <TableCell>{t('subscriptions.table.filter')}</TableCell>
              <TableCell>{t('subscriptions.table.deliveryMode')}</TableCell>
              <TableCell>{t('subscriptions.table.status')}</TableCell>
              <TableCell align="right">{t('subscriptions.table.actions')}</TableCell>
            </TableRow>
          </TableHead>
          <TableBody>
            {rows.length === 0 && !isLoading ? (
              <TableRow>
                <TableCell colSpan={7} align="center" sx={{ py: 4 }}>
                  <Typography color="text.secondary">{t('subscriptions.table.empty')}</Typography>
                </TableCell>
              </TableRow>
            ) : null}
            {rows.map((sub) => {
              const statusStr = (sub.status || 'ACTIVE').toUpperCase()
              const isDeleted = statusStr === 'DELETED'
              const deliveryMode = (sub.delivery?.mode || 'webhook').toLowerCase()
              const isWebhook = deliveryMode === 'webhook'
              const filterType = sub.filter?.type || 'all'
              const purposeCount = sub.filter?.purposes?.length ?? 0

              let filterLabel = t(`subscriptions.filterType.${filterType}`, filterType)
              if (filterType !== 'all' && purposeCount > 0) {
                filterLabel += ` (${purposeCount})`
              }

              return (
                <TableRow key={sub.subscriptionId} hover>
                  <TableCell>
                    <CopyableText value={sub.subscriptionId} truncateAt={14} monospace />
                  </TableCell>
                  <TableCell>
                    <Typography variant="body2" fontWeight={600}>
                      {sub.topic}
                    </Typography>
                  </TableCell>
                  <TableCell>
                    {sub.groupId ? (
                      <CopyableText value={sub.groupId} truncateAt={12} monospace />
                    ) : (
                      <Typography variant="body2" color="text.secondary">
                        -
                      </Typography>
                    )}
                  </TableCell>
                  <TableCell>
                    <Tooltip
                      title={
                        sub.filter?.purposes?.length
                          ? sub.filter.purposes.join(', ')
                          : t('subscriptions.filterType.allDescription')
                      }
                    >
                      <Chip size="small" variant="outlined" label={filterLabel} />
                    </Tooltip>
                  </TableCell>
                  <TableCell>
                    <Tooltip
                      title={
                        isWebhook && sub.delivery?.callbackUrl
                          ? sub.delivery.callbackUrl
                          : t(`subscriptions.deliveryMode.${deliveryMode}`)
                      }
                    >
                      <Chip
                        size="small"
                        color={isWebhook ? 'primary' : 'default'}
                        variant={isWebhook ? 'filled' : 'outlined'}
                        label={t(`subscriptions.deliveryMode.${deliveryMode}`, deliveryMode)}
                      />
                    </Tooltip>
                  </TableCell>
                  <TableCell>
                    <Chip
                      size="small"
                      color={getSubscriptionStatusChipColor(statusStr)}
                      label={t(`subscriptions.status.${statusStr.toLowerCase()}`, statusStr)}
                    />
                  </TableCell>
                  <TableCell align="right">
                    <Stack direction="row" spacing={0.5} justifyContent="flex-end">
                      <Tooltip title={t('subscriptions.actions.view')}>
                        <span>
                          <IconButton
                            size="small"
                            color="primary"
                            onClick={() => onViewDetails(sub)}
                            aria-label={t('subscriptions.actions.view')}
                          >
                            <Eye size={16} />
                          </IconButton>
                        </span>
                      </Tooltip>

                      {canWrite && isWebhook && !isDeleted ? (
                        <Tooltip title={t('subscriptions.actions.verify')}>
                          <span>
                            <IconButton
                              size="small"
                              color="secondary"
                              disabled={isMutating}
                              onClick={() => onVerify(sub)}
                              aria-label={t('subscriptions.actions.verify')}
                            >
                              <RefreshCw size={16} />
                            </IconButton>
                          </span>
                        </Tooltip>
                      ) : null}

                      {canWrite ? (
                        <Tooltip
                          title={
                            isDeleted
                              ? t('subscriptions.actions.alreadyDeleted')
                              : t('subscriptions.actions.delete')
                          }
                        >
                          <span>
                            <IconButton
                              size="small"
                              color="error"
                              disabled={isDeleted || isMutating}
                              onClick={() => onDelete(sub)}
                              aria-label={t('subscriptions.actions.delete')}
                            >
                              <Trash2 size={16} />
                            </IconButton>
                          </span>
                        </Tooltip>
                      ) : null}
                    </Stack>
                  </TableCell>
                </TableRow>
              )
            })}
          </TableBody>
        </Table>
      </TableContainer>
      <CursorPaginationFooter
        rowsPerPage={rowsPerPage}
        rowsPerPageOptions={ROWS_PER_PAGE_OPTIONS}
        hasPreviousPage={hasPreviousPage}
        hasNextPage={hasNextPage}
        disabled={isLoading || isMutating}
        onRowsPerPageChange={onRowsPerPageChange}
        onPreviousPage={onPreviousPage}
        onNextPage={onNextPage}
      />
    </Paper>
  )
}
