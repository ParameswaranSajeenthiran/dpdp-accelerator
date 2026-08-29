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
  Box,
  Button,
  Card,
  CardHeader,
  Divider,
  Skeleton,
  Stack,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableRow,
  Typography,
} from '@wso2/oxygen-ui'
import { ChevronDown, History } from '@wso2/oxygen-ui-icons-react'
import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import type { ConsentHistoryPage } from '../../../../hooks/useConsentHistoryPaging'
import type { ConsentStatusAuditEntry } from '../../../../types/consentHistory'
import { REQUIRED_SCOPES } from '../../../../utils/scopes'
import { useAdminConsentStatusHistoryQuery } from '../../../admin-consents/hooks/useAdminConsentHistoryQueries'
import useAuthorization from '../../../auth/useAuthorization'
import { useConsentStatusHistoryQuery } from '../../hooks/useConsentHistoryQueries'
import ConsentFullHistoryDialog from '../ConsentFullHistoryDialog'
import ConsentHistoryTableRow from './ConsentHistoryTableRow'

interface ConsentHistorySectionProps {
  consentId: string
  variant: 'self' | 'admin'
}

const HISTORY_TABLE_COLUMN_COUNT = 4

type TranslateFn = (key: string, options?: Record<string, unknown>) => string

function renderTableBody(
  timeline: ConsentHistoryPage<ConsentStatusAuditEntry>,
  t: TranslateFn,
): React.JSX.Element {
  if (timeline.isLoading) {
    return (
      <TableRow>
        <TableCell colSpan={HISTORY_TABLE_COLUMN_COUNT}>
          <Stack spacing={1.5}>
            <Skeleton variant="text" width="70%" />
            <Skeleton variant="text" width="55%" />
          </Stack>
        </TableCell>
      </TableRow>
    )
  }

  if (timeline.isError) {
    return (
      <TableRow>
        <TableCell colSpan={HISTORY_TABLE_COLUMN_COUNT}>
          <Typography variant="body2" color="error.main" align="center">
            {t('consentRegistry.messages.loadFailed')}
          </Typography>
        </TableCell>
      </TableRow>
    )
  }

  if (timeline.entries.length === 0) {
    return (
      <TableRow>
        <TableCell colSpan={HISTORY_TABLE_COLUMN_COUNT}>
          <Typography variant="body2" color="text.secondary" align="center">
            {t('consentRegistry.history.empty')}
          </Typography>
        </TableCell>
      </TableRow>
    )
  }

  return (
    <>
      {timeline.entries.map((entry) => (
        <ConsentHistoryTableRow
          key={`${entry.actionTime}-${entry.actionType}-${entry.actionBy}`}
          entry={entry}
        />
      ))}
    </>
  )
}

function ConsentHistorySection({
  consentId,
  variant,
}: ConsentHistorySectionProps): React.JSX.Element | null {
  const { t } = useTranslation('common')
  const { hasScope } = useAuthorization()
  const [snapshotDialogOpen, setSnapshotDialogOpen] = useState(false)

  const canViewTimeline = hasScope(
    variant === 'admin'
      ? REQUIRED_SCOPES.CONSENT_STATUS_HISTORY_READ_ANY
      : REQUIRED_SCOPES.CONSENT_STATUS_HISTORY_READ_SELF,
  )
  const canViewSnapshot = hasScope(
    variant === 'admin'
      ? REQUIRED_SCOPES.CONSENT_FULL_HISTORY_READ_ANY
      : REQUIRED_SCOPES.CONSENT_FULL_HISTORY_READ_SELF,
  )

  const selfTimeline = useConsentStatusHistoryQuery(
    consentId,
    variant === 'self' && canViewTimeline,
  )
  const adminTimeline = useAdminConsentStatusHistoryQuery(
    consentId,
    variant === 'admin' && canViewTimeline,
  )
  const timeline = variant === 'admin' ? adminTimeline : selfTimeline

  if (!canViewTimeline) {
    return null
  }

  return (
    <Card sx={{ boxShadow: 1 }}>
      <CardHeader
        title={
          <Typography variant="h5" fontWeight={600}>
            {t('consentRegistry.details.section.history')}
          </Typography>
        }
        action={
          canViewSnapshot ? (
            <Button
              size="small"
              variant="outlined"
              startIcon={<History size={15} />}
              onClick={() => setSnapshotDialogOpen(true)}
            >
              {t('consentRegistry.history.viewFullSnapshot')}
            </Button>
          ) : null
        }
        sx={{ pb: 1 }}
      />
      <Divider />
      <TableContainer>
        <Table
          aria-label={t('consentRegistry.details.section.history')}
          sx={{ '& tbody tr:hover': { bgcolor: 'action.hover' } }}
        >
          <TableHead>
            <TableRow sx={{ bgcolor: 'action.default' }}>
              <TableCell sx={{ fontWeight: 700 }}>
                {t('consentRegistry.history.table.timestamp')}
              </TableCell>
              <TableCell sx={{ fontWeight: 700 }}>
                {t('consentRegistry.history.table.action')}
              </TableCell>
              <TableCell sx={{ fontWeight: 700 }}>
                {t('consentRegistry.history.table.actor')}
              </TableCell>
              <TableCell sx={{ fontWeight: 700 }}>
                {t('consentRegistry.history.table.statusChange')}
              </TableCell>
            </TableRow>
          </TableHead>
          <TableBody>{renderTableBody(timeline, t)}</TableBody>
        </Table>
      </TableContainer>

      {timeline.hasMore ? (
        <Box sx={{ p: 2 }}>
          <Stack alignItems="center" spacing={0.5}>
            <Button
              size="small"
              variant="outlined"
              endIcon={<ChevronDown size={14} />}
              disabled={timeline.isFetching}
              onClick={timeline.loadMore}
            >
              {t('consentRegistry.history.loadMore')}
            </Button>
            <Typography variant="caption" color="text.disabled">
              {t('consentRegistry.history.showingCount', {
                shown: timeline.entries.length,
                total: timeline.totalCount,
              })}
            </Typography>
          </Stack>
        </Box>
      ) : null}

      {canViewSnapshot ? (
        <ConsentFullHistoryDialog
          open={snapshotDialogOpen}
          consentId={consentId}
          variant={variant}
          onClose={() => setSnapshotDialogOpen(false)}
        />
      ) : null}
    </Card>
  )
}

export default ConsentHistorySection
