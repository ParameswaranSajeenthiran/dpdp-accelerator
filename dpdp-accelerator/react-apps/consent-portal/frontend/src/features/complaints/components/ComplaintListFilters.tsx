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

import { Box, Button, FormControl, InputLabel, MenuItem, Select, Stack } from '@wso2/oxygen-ui'
import { useTranslation } from 'react-i18next'
import { COMPLAINT_STATUSES, type ComplaintStatus } from '../../../types/complaint'
import { getComplaintStatusLabelKey } from '../utils/complaintDisplay'

export type ComplaintListStatusFilter = ComplaintStatus | 'All'

interface ComplaintListFiltersProps {
  status: ComplaintListStatusFilter
  onStatusChange: (status: ComplaintListStatusFilter) => void
  onClear: () => void
}

function ComplaintListFilters({
  status,
  onStatusChange,
  onClear,
}: ComplaintListFiltersProps): React.JSX.Element {
  const { t } = useTranslation('common')

  return (
    <Box component="section" aria-label={t('complaints.list.filters.sectionAriaLabel')}>
      <Stack direction={{ xs: 'column', sm: 'row' }} spacing={1} alignItems={{ sm: 'center' }}>
        <FormControl size="small" sx={{ width: { xs: '100%', sm: 'auto' }, minWidth: { sm: 200 } }}>
          <InputLabel id="complaint-list-status-label">
            {t('complaints.list.filters.status')}
          </InputLabel>
          <Select
            labelId="complaint-list-status-label"
            id="complaint-list-status"
            value={status}
            label={t('complaints.list.filters.status')}
            onChange={(event) => {
              onStatusChange(event.target.value as ComplaintListStatusFilter)
            }}
          >
            <MenuItem value="All">{t('complaints.list.filters.all')}</MenuItem>
            {COMPLAINT_STATUSES.map((complaintStatus) => (
              <MenuItem key={complaintStatus} value={complaintStatus}>
                {t(`complaints.status.${getComplaintStatusLabelKey(complaintStatus)}`)}
              </MenuItem>
            ))}
          </Select>
        </FormControl>

        <Button
          variant="text"
          aria-label={t('complaints.list.filters.clearAriaLabel')}
          onClick={onClear}
        >
          {t('complaints.list.filters.clear')}
        </Button>
      </Stack>
    </Box>
  )
}

export default ComplaintListFilters
