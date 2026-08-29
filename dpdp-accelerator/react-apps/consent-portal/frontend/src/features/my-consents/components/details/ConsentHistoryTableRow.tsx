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

import { Chip, Stack, TableCell, TableRow, Typography } from '@wso2/oxygen-ui'
import { ArrowRight } from '@wso2/oxygen-ui-icons-react'
import { useTranslation } from 'react-i18next'
import type { ConsentStatusAuditEntry } from '../../../../types/consentHistory'
import { formatEpochTimestamp } from '../../../../utils/dateTime'
import {
  getConsentHistoryActionPresentation,
  isSystemActor,
} from '../../utils/consentHistoryLabels'
import { getConsentStateChipColor, getConsentStateLabelKey } from '../../utils/statusChip'

interface ConsentHistoryTableRowProps {
  entry: ConsentStatusAuditEntry
}

function ConsentHistoryTableRow({ entry }: ConsentHistoryTableRowProps): React.JSX.Element {
  const { t } = useTranslation('common')
  const presentation = getConsentHistoryActionPresentation(entry.actionType)
  const system = isSystemActor(entry.actionBy)
  const noStatusChange =
    entry.previousStatus !== undefined && entry.previousStatus === entry.currentStatus

  return (
    <TableRow hover>
      <TableCell sx={{ whiteSpace: 'nowrap' }}>
        <Typography variant="body2" color="text.secondary">
          {formatEpochTimestamp(entry.actionTime)}
        </Typography>
      </TableCell>
      <TableCell>
        <Typography variant="body2">
          {t(`consentRegistry.history.actions.${presentation.labelKey}`)}
        </Typography>
      </TableCell>
      <TableCell>
        <Typography variant="body2">
          {system ? t('consentRegistry.history.systemActor') : entry.actionBy}
        </Typography>
      </TableCell>
      <TableCell>
        <Stack direction="row" alignItems="center" spacing={0.75}>
          {entry.previousStatus !== undefined && !noStatusChange ? (
            <Chip
              size="small"
              variant="outlined"
              color={getConsentStateChipColor(entry.previousStatus)}
              label={t(`consentRegistry.status.${getConsentStateLabelKey(entry.previousStatus)}`)}
            />
          ) : null}
          {noStatusChange ? null : <ArrowRight size={13} style={{ opacity: 0.5, flexShrink: 0 }} />}
          <Chip
            size="small"
            variant="outlined"
            color={getConsentStateChipColor(entry.currentStatus)}
            label={t(`consentRegistry.status.${getConsentStateLabelKey(entry.currentStatus)}`)}
          />
          {noStatusChange ? (
            <Typography variant="caption" color="text.disabled" fontStyle="italic">
              {t('consentRegistry.history.noStatusChange')}
            </Typography>
          ) : null}
        </Stack>
      </TableCell>
    </TableRow>
  )
}

export default ConsentHistoryTableRow
