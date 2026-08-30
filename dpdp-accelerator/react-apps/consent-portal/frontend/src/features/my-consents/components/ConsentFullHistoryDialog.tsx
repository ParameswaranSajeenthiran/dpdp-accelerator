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
  Accordion,
  AccordionDetails,
  AccordionSummary,
  Alert,
  Box,
  Button,
  Chip,
  CircularProgress,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  Stack,
  Typography,
} from '@wso2/oxygen-ui'
import { ChevronRight, History } from '@wso2/oxygen-ui-icons-react'
import { useTranslation } from 'react-i18next'
import type { ConsentHistoryEntry } from '../../../types/consentHistory'
import { annotateSnapshot, diffConsentSnapshots } from '../../../utils/consentSnapshotDiff'
import { formatEpochTimestamp } from '../../../utils/dateTime'
import { useAdminConsentFullHistoryQuery } from '../../admin-consents/hooks/useAdminConsentHistoryQueries'
import type { ConsentHistoryQueryResult } from '../hooks/useConsentHistoryQueries'
import { useConsentFullHistoryQuery } from '../hooks/useConsentHistoryQueries'
import { getConsentHistoryActionPresentation, isSystemActor } from '../utils/consentHistoryLabels'
import ConsentSnapshotView from './ConsentSnapshotView'

interface ConsentFullHistoryDialogProps {
  open: boolean
  consentId: string
  variant: 'self' | 'admin'
  onClose: () => void
}

type TranslateFn = (key: string, options?: Record<string, unknown>) => string

interface HistoryEntryAccordionProps {
  entry: ConsentHistoryEntry
  previousEntry: ConsentHistoryEntry | undefined
  t: TranslateFn
}

function HistoryEntryAccordion({
  entry,
  previousEntry,
  t,
}: HistoryEntryAccordionProps): React.JSX.Element {
  const presentation = getConsentHistoryActionPresentation(entry.actionType)
  const system = isSystemActor(entry.actionBy)
  const isCreate = entry.actionType === 'CREATE'
  const canDiff = isCreate || previousEntry !== undefined
  const diff = canDiff ? diffConsentSnapshots(previousEntry?.snapshot, entry.snapshot) : undefined

  return (
    <Accordion
      disableGutters
      elevation={0}
      sx={{
        border: 1,
        borderColor: 'divider',
        borderRadius: 1,
        overflow: 'hidden',
        bgcolor: 'background.paper',
        '&:before': { display: 'none' },
      }}
    >
      <AccordionSummary
        expandIcon={<ChevronRight />}
        sx={{ '&:hover': { bgcolor: 'action.hover' } }}
      >
        <Stack
          direction="row"
          alignItems="center"
          spacing={1.5}
          sx={{ width: '100%', minWidth: 0 }}
        >
          <Typography variant="body2" sx={{ minWidth: 0, flex: 1 }} noWrap>
            <Typography component="span" variant="body2" fontWeight={700}>
              {t(`consentRegistry.history.actions.${presentation.labelKey}`)}
            </Typography>
            {' · '}
            {system ? t('consentRegistry.history.systemActor') : entry.actionBy}
          </Typography>
          {isCreate ? (
            <Chip
              size="small"
              variant="outlined"
              label={t('consentRegistry.history.snapshot.initial')}
            />
          ) : null}
          <Typography variant="caption" color="text.secondary" sx={{ whiteSpace: 'nowrap' }}>
            {formatEpochTimestamp(entry.actionTime)}
          </Typography>
        </Stack>
      </AccordionSummary>
      <AccordionDetails sx={{ borderTop: 1, borderColor: 'divider', p: 2.5 }}>
        {diff ? (
          <Stack spacing={1.5}>
            {diff.hasChanges ? null : (
              <Typography variant="caption" color="text.disabled" fontStyle="italic">
                {t('consentRegistry.history.snapshot.noChanges')}
              </Typography>
            )}
            <ConsentSnapshotView snapshot={annotateSnapshot(entry.snapshot, diff)} />
          </Stack>
        ) : (
          <Typography variant="body2" color="text.disabled" fontStyle="italic">
            {t('consentRegistry.history.snapshot.needsMoreHistory')}
          </Typography>
        )}
      </AccordionDetails>
    </Accordion>
  )
}

function renderHistoryBody(
  history: ConsentHistoryQueryResult<ConsentHistoryEntry>,
  t: TranslateFn,
): React.JSX.Element {
  if (history.isLoading) {
    return (
      <Box sx={{ display: 'grid', placeItems: 'center', py: 6 }}>
        <CircularProgress size={32} />
      </Box>
    )
  }

  if (history.isError) {
    return <Alert severity="error">{t('consentRegistry.messages.loadFailed')}</Alert>
  }

  if (history.entries.length === 0) {
    return (
      <Typography variant="body2" color="text.secondary">
        {t('consentRegistry.history.empty')}
      </Typography>
    )
  }

  return (
    <Stack spacing={1}>
      {history.entries.map((entry, index) => (
        <HistoryEntryAccordion
          key={`${entry.actionTime}-${entry.actionType}-${entry.actionBy}`}
          entry={entry}
          previousEntry={history.entries[index + 1]}
          t={t}
        />
      ))}
    </Stack>
  )
}

/**
 * Shows every history entry's snapshot diff, restricted (via `ConsentSnapshotView`) to
 * `expiryTime`/`properties`/`authorizations` - the only fields the Identity Server's own
 * consent-update path can change. Entries arrive newest-first, so `previousEntry` is the next
 * array element (a later index), not the one before it.
 */
function ConsentFullHistoryDialog({
  open,
  consentId,
  variant,
  onClose,
}: ConsentFullHistoryDialogProps): React.JSX.Element {
  const { t } = useTranslation('common')

  const selfHistory = useConsentFullHistoryQuery(consentId, open && variant === 'self')
  const adminHistory = useAdminConsentFullHistoryQuery(consentId, open && variant === 'admin')
  const history = variant === 'admin' ? adminHistory : selfHistory

  return (
    <Dialog
      open={open}
      onClose={onClose}
      maxWidth="md"
      fullWidth
      PaperProps={{
        sx: (theme) => ({
          borderRadius: 1,
          ...theme.applyStyles('light', { bgcolor: theme.palette.grey[50] }),
          ...theme.applyStyles('dark', { bgcolor: 'rgba(255, 255, 255, 0.06)' }),
        }),
      }}
    >
      <DialogTitle sx={{ p: 3, borderBottom: 1, borderColor: 'divider' }}>
        <Stack direction="row" alignItems="center" spacing={1.5}>
          <History size={22} />
          <Typography variant="h6" fontWeight={700}>
            {t('consentRegistry.history.viewFullSnapshot')}
          </Typography>
        </Stack>
      </DialogTitle>

      <DialogContent sx={{ p: 3 }}>{renderHistoryBody(history, t)}</DialogContent>

      <DialogActions
        sx={{ p: 2.5, borderTop: 1, borderColor: 'divider', bgcolor: 'background.default' }}
      >
        <Button variant="outlined" onClick={onClose}>
          {t('consentRegistry.history.close')}
        </Button>
      </DialogActions>
    </Dialog>
  )
}

export default ConsentFullHistoryDialog
