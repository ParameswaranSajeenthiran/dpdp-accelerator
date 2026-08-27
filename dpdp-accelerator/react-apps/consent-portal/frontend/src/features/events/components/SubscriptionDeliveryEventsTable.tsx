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
  Chip,
  IconButton,
  LinearProgress,
  Paper,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableRow,
  Tooltip,
  Typography,
} from '@wso2/oxygen-ui'
import { History } from '@wso2/oxygen-ui-icons-react'
import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import CopyableText from '../../../components/CopyableText'
import CursorPaginationFooter from '../../../components/CursorPaginationFooter'
import { formatEpochTimestamp } from '../../../utils/dateTime'
import { useSubscriptionEventsQuery } from '../hooks/useSubscriptionQueries'
import { getSubscriptionStatusChipColor } from '../utils/subscriptionStatusChip'
import SubscriptionDeliveryHistoryModal from './SubscriptionDeliveryHistoryModal'

const ROWS_PER_PAGE_OPTIONS = [5, 10, 20] as const

interface SubscriptionDeliveryEventsTableProps {
  subscriptionId: string
}

export default function SubscriptionDeliveryEventsTable({
  subscriptionId,
}: SubscriptionDeliveryEventsTableProps): React.JSX.Element {
  const { t } = useTranslation('common')
  const [page, setPage] = useState(0)
  const [rowsPerPage, setRowsPerPage] = useState(10)
  const [selectedDeliveryId, setSelectedDeliveryId] = useState<string | undefined>()

  const eventsQuery = useSubscriptionEventsQuery(subscriptionId, page, rowsPerPage)
  const rows = eventsQuery.data?.rows ?? []
  const hasNextPage = eventsQuery.data?.hasNextPage ?? false

  if (eventsQuery.isError) {
    return (
      <Alert severity="error" sx={{ my: 2 }}>
        {t('subscriptions.deliveryHistory.loadFailed')}
      </Alert>
    )
  }

  return (
    <Paper sx={{ width: '100%', overflow: 'hidden', border: 1, borderColor: 'divider' }}>
      {eventsQuery.isPending || eventsQuery.isPlaceholderData ? <LinearProgress /> : null}
      <TableContainer>
        <Table size="small" aria-label={t('subscriptions.deliveryEvents.ariaLabel')}>
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
              <TableCell>{t('subscriptions.table.deliveryId')}</TableCell>
              <TableCell>{t('subscriptions.table.eventId')}</TableCell>
              <TableCell>{t('subscriptions.table.topic')}</TableCell>
              <TableCell>{t('subscriptions.table.deliveryMode')}</TableCell>
              <TableCell>{t('subscriptions.table.status')}</TableCell>
              <TableCell>{t('subscriptions.table.occurredAt')}</TableCell>
              <TableCell align="right">{t('subscriptions.deliveryEvents.auditAction')}</TableCell>
            </TableRow>
          </TableHead>
          <TableBody>
            {rows.length === 0 && !eventsQuery.isPending ? (
              <TableRow>
                <TableCell colSpan={7} align="center" sx={{ py: 4 }}>
                  <Typography color="text.secondary">
                    {t('subscriptions.deliveryEvents.empty')}
                  </Typography>
                </TableCell>
              </TableRow>
            ) : null}
            {rows.map((event) => (
              <TableRow key={event.deliveryId} hover>
                <TableCell>
                  <CopyableText value={event.deliveryId} truncateAt={14} monospace />
                </TableCell>
                <TableCell>
                  <CopyableText value={event.eventId} truncateAt={14} monospace />
                </TableCell>
                <TableCell>
                  <Typography variant="body2" fontWeight={600}>
                    {event.topic}
                  </Typography>
                </TableCell>
                <TableCell>
                  <Chip size="small" variant="outlined" label={event.deliveryMode} />
                </TableCell>
                <TableCell>
                  <Chip
                    size="small"
                    color={getSubscriptionStatusChipColor(event.currentStatus)}
                    label={event.currentStatus}
                  />
                </TableCell>
                <TableCell>{formatEpochTimestamp(event.occurredAt)}</TableCell>
                <TableCell align="right">
                  <Tooltip title={t('subscriptions.deliveryEvents.viewHistory')}>
                    <span>
                      <IconButton
                        size="small"
                        color="primary"
                        onClick={() => setSelectedDeliveryId(event.deliveryId)}
                        aria-label={t('subscriptions.deliveryEvents.viewHistory')}
                      >
                        <History size={16} />
                      </IconButton>
                    </span>
                  </Tooltip>
                </TableCell>
              </TableRow>
            ))}
          </TableBody>
        </Table>
      </TableContainer>

      <CursorPaginationFooter
        rowsPerPage={rowsPerPage}
        rowsPerPageOptions={ROWS_PER_PAGE_OPTIONS}
        hasPreviousPage={page > 0}
        hasNextPage={hasNextPage}
        disabled={eventsQuery.isPending}
        onRowsPerPageChange={(nextRpp) => {
          setRowsPerPage(nextRpp)
          setPage(0)
        }}
        onPreviousPage={() => setPage((prev) => Math.max(prev - 1, 0))}
        onNextPage={() => setPage((prev) => prev + 1)}
      />

      {selectedDeliveryId ? (
        <SubscriptionDeliveryHistoryModal
          open
          subscriptionId={subscriptionId}
          deliveryId={selectedDeliveryId}
          onClose={() => setSelectedDeliveryId(undefined)}
        />
      ) : null}
    </Paper>
  )
}
