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

import { Box, Stack, Typography } from '@wso2/oxygen-ui'
import { useTranslation } from 'react-i18next'
import type { ComplaintStatus } from '../../../types/complaint'
import { formatEpochTimestamp } from '../../../utils/dateTime'
import { getComplaintSlaState, getComplaintStatutoryPeriodDays } from '../utils/complaintDisplay'
import ComplaintSlaIndicator from './ComplaintSlaIndicator'

interface ComplaintDeadlineProps {
  submittedAt: number
  statutoryDueDate: number
  status: ComplaintStatus
  audience: 'dataPrincipal' | 'officer'
}

const DATE_FORMAT_OPTIONS: Intl.DateTimeFormatOptions = {
  month: 'short',
  day: '2-digit',
  year: 'numeric',
}

const LABEL_KEYS = {
  dataPrincipal: 'complaints.sla.deadlineLabelDataPrincipal',
  officer: 'complaints.sla.deadlineLabelOfficer',
} as const

// The data principal always sees what the deadline means, since that is what they came to find
// out. An officer already knows the rule, so it stays in the badge tooltip unless the case is
// overdue.
function ComplaintDeadline({
  submittedAt,
  statutoryDueDate,
  status,
  audience,
}: ComplaintDeadlineProps): React.JSX.Element {
  const { t } = useTranslation('common')
  const slaState = getComplaintSlaState(statutoryDueDate, status)
  const periodDays = getComplaintStatutoryPeriodDays(submittedAt, statutoryDueDate)
  const isBreached = slaState === 'breached'

  let helperText: string | null = null
  if (audience === 'dataPrincipal' && slaState !== 'met') {
    helperText = isBreached
      ? t('complaints.sla.breachedDataPrincipal')
      : t('complaints.sla.periodDataPrincipal', { count: periodDays })
  } else if (audience === 'officer' && isBreached) {
    helperText = t('complaints.sla.breachedOfficer')
  }

  return (
    <Box>
      <Typography
        variant="caption"
        color="text.secondary"
        fontWeight={600}
        sx={{ display: 'block', textTransform: 'uppercase' }}
      >
        {t(LABEL_KEYS[audience])}
      </Typography>
      <Stack direction="row" spacing={0.5} alignItems="center" flexWrap="wrap" useFlexGap>
        <Typography variant="body2" sx={{ fontSize: '0.75rem' }}>
          {formatEpochTimestamp(statutoryDueDate, DATE_FORMAT_OPTIONS)}
        </Typography>
        {/* The status chip beside the title already says "Resolved". */}
        {slaState !== 'met' ? (
          <ComplaintSlaIndicator
            statutoryDueDate={statutoryDueDate}
            status={status}
            tooltip={
              audience === 'officer'
                ? t('complaints.sla.periodOfficer', { count: periodDays })
                : null
            }
          />
        ) : null}
      </Stack>
      {helperText ? (
        <Typography
          variant="caption"
          color={isBreached ? 'error.main' : 'text.secondary'}
          sx={{ display: 'block', mt: 0.5 }}
        >
          {helperText}
        </Typography>
      ) : null}
    </Box>
  )
}

export default ComplaintDeadline
