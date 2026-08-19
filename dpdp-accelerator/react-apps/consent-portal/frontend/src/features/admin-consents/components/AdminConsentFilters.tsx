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
  FormControl,
  InputLabel,
  MenuItem,
  Popover,
  SearchBar,
  Select,
  Stack,
  TextField,
  Tooltip,
  Typography,
} from '@wso2/oxygen-ui'
import { ListFilter } from '@wso2/oxygen-ui-icons-react'
import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import type { AdminConsentRegistryFilters } from '../../../types/consent'
import { CONSENT_STATES } from '../../../types/consent'
import { getConsentStateLabelKey } from '../../my-consents/utils/statusChip'
import {
  EMPTY_ADMIN_CONSENT_FILTERS,
  normalizeAdminConsentFilters,
} from '../utils/adminConsentFilters'

interface AdminConsentFiltersProps {
  filters: AdminConsentRegistryFilters
  onFilterChange: (filters: AdminConsentRegistryFilters) => void
  onClear: () => void
}

const MAIN_FILTER_HEIGHT = 40

export default function AdminConsentFilters({
  filters,
  onFilterChange,
  onClear,
}: AdminConsentFiltersProps): React.JSX.Element {
  const { t } = useTranslation('common')
  const [draft, setDraft] = useState(filters)
  const [filtersAnchor, setFiltersAnchor] = useState<HTMLElement | null>(null)
  const filtersOpen = Boolean(filtersAnchor)
  const advancedFilterCount = [
    filters.serviceId,
    filters.purposeId,
    filters.propertyKey && filters.propertyValue ? 'set' : '',
  ].filter(Boolean).length

  const applyFilters = (next: AdminConsentRegistryFilters): void => {
    const normalized = normalizeAdminConsentFilters(next)
    setDraft(normalized)
    onFilterChange(normalized)
  }

  const cancelAdvancedChanges = (): void => {
    setDraft(filters)
    setFiltersAnchor(null)
  }

  return (
    <Box component="section" aria-label={t('adminConsents.filters.sectionAriaLabel')}>
      <Stack direction={{ xs: 'column', sm: 'row' }} spacing={1}>
        <Box sx={{ flex: 1, height: MAIN_FILTER_HEIGHT }}>
          <SearchBar
            size="small"
            fullWidth
            value={draft.consentId}
            placeholder={t('adminConsents.filters.consentIdSearchPlaceholder')}
            onChange={(event) => setDraft({ ...draft, consentId: event.target.value })}
            onKeyDown={(event) => {
              if (event.key === 'Enter') applyFilters(draft)
            }}
            sx={{ '& .MuiInputBase-root': { height: MAIN_FILTER_HEIGHT } }}
          />
        </Box>

        <Tooltip
          title={filters.consentId ? t('adminConsents.filters.removeConsentIdForAdvanced') : ''}
        >
          <Box component="span" sx={{ position: 'relative', flexShrink: 0 }}>
            <Button
              variant={advancedFilterCount > 0 ? 'contained' : 'outlined'}
              color={filtersOpen || advancedFilterCount > 0 ? 'primary' : 'inherit'}
              startIcon={<ListFilter size={16} />}
              disabled={Boolean(filters.consentId)}
              aria-haspopup="dialog"
              aria-expanded={filtersOpen}
              sx={{
                height: MAIN_FILTER_HEIGHT,
                width: { xs: '100%', sm: 'auto' },
                whiteSpace: 'nowrap',
              }}
              onClick={(event) => {
                setDraft(filters)
                setFiltersAnchor(event.currentTarget)
              }}
            >
              {t('consentRegistry.filters.advanced')}
            </Button>
            {advancedFilterCount > 0 ? (
              <Box
                component="span"
                sx={{
                  position: 'absolute',
                  top: -6,
                  right: -6,
                  minWidth: 18,
                  height: 18,
                  px: 0.4,
                  borderRadius: 9,
                  bgcolor: 'error.main',
                  color: 'error.contrastText',
                  fontSize: '0.625rem',
                  fontWeight: 700,
                  lineHeight: '18px',
                  textAlign: 'center',
                  pointerEvents: 'none',
                }}
              >
                {advancedFilterCount}
              </Box>
            ) : null}
          </Box>
        </Tooltip>

        <Tooltip
          title={filters.consentId ? t('adminConsents.filters.removeConsentIdForSubject') : ''}
        >
          <Box component="span" sx={{ width: { xs: '100%', sm: 200 }, flexShrink: 0 }}>
            <TextField
              size="small"
              fullWidth
              label={t('consentRegistry.details.table.user')}
              value={draft.subjectId}
              disabled={Boolean(filters.consentId)}
              sx={{ '& .MuiInputBase-root': { height: MAIN_FILTER_HEIGHT } }}
              onChange={(event) => setDraft({ ...draft, subjectId: event.target.value })}
              onKeyDown={(event) => {
                if (event.key === 'Enter') applyFilters(draft)
              }}
            />
          </Box>
        </Tooltip>

        <Tooltip
          title={filters.consentId ? t('adminConsents.filters.removeConsentIdForState') : ''}
        >
          <Box component="span" sx={{ width: { xs: '100%', sm: 220 }, flexShrink: 0 }}>
            <FormControl size="small" fullWidth disabled={Boolean(filters.consentId)}>
              <InputLabel id="admin-consent-state-label">
                {t('consentRegistry.filters.state')}
              </InputLabel>
              <Select
                labelId="admin-consent-state-label"
                value={filters.state}
                label={t('consentRegistry.filters.state')}
                sx={{ height: MAIN_FILTER_HEIGHT }}
                onChange={(event) =>
                  applyFilters({
                    ...filters,
                    ...draft,
                    state: event.target.value as AdminConsentRegistryFilters['state'],
                  })
                }
              >
                <MenuItem value="All">{t('consentRegistry.status.all')}</MenuItem>
                {CONSENT_STATES.map((state) => (
                  <MenuItem key={state} value={state}>
                    {t(`consentRegistry.status.${getConsentStateLabelKey(state)}`)}
                  </MenuItem>
                ))}
              </Select>
            </FormControl>
          </Box>
        </Tooltip>
      </Stack>

      <Popover
        open={filtersOpen}
        anchorEl={filtersAnchor}
        onClose={cancelAdvancedChanges}
        anchorOrigin={{ vertical: 'bottom', horizontal: 'right' }}
        transformOrigin={{ vertical: 'top', horizontal: 'right' }}
        slotProps={{
          paper: {
            sx: {
              width: { xs: 'calc(100vw - 32px)', sm: 560 },
              maxWidth: 'calc(100vw - 32px)',
              mt: 1,
              p: 2.5,
            },
          },
        }}
      >
        <Stack spacing={2.5}>
          <Typography variant="subtitle2" fontWeight={600}>
            {t('consentRegistry.filters.advanced')}
          </Typography>
          <Stack direction={{ xs: 'column', sm: 'row' }} spacing={2}>
            <TextField
              size="small"
              fullWidth
              label={t('adminConsents.filters.serviceId')}
              value={draft.serviceId}
              onChange={(event) => setDraft({ ...draft, serviceId: event.target.value })}
            />
            <TextField
              size="small"
              fullWidth
              label={t('catalog.fields.purpose')}
              helperText={t('catalog.fields.purposeId')}
              value={draft.purposeId}
              onChange={(event) => setDraft({ ...draft, purposeId: event.target.value })}
            />
          </Stack>

          <Stack spacing={1}>
            <Typography variant="caption" color="text.secondary">
              {t('adminConsents.filters.propertyFilterLabel')}
            </Typography>
            <Stack direction={{ xs: 'column', sm: 'row' }} spacing={2}>
              <TextField
                size="small"
                fullWidth
                label={t('catalog.fields.propertyKey')}
                value={draft.propertyKey}
                onChange={(event) => setDraft({ ...draft, propertyKey: event.target.value })}
              />
              <TextField
                size="small"
                fullWidth
                label={t('catalog.fields.propertyValue')}
                value={draft.propertyValue}
                onChange={(event) => setDraft({ ...draft, propertyValue: event.target.value })}
              />
            </Stack>
          </Stack>

          <Stack
            direction="row"
            justifyContent="space-between"
            sx={{ pt: 2, borderTop: 1, borderColor: 'divider' }}
          >
            <Button
              variant="text"
              onClick={() => {
                setDraft(EMPTY_ADMIN_CONSENT_FILTERS)
                setFiltersAnchor(null)
                onClear()
              }}
            >
              {t('consentRegistry.filters.clear')}
            </Button>
            <Stack direction="row" spacing={1}>
              <Button onClick={cancelAdvancedChanges}>{t('consentRegistry.filters.cancel')}</Button>
              <Button
                variant="contained"
                onClick={() => {
                  applyFilters(draft)
                  setFiltersAnchor(null)
                }}
              >
                {t('consentRegistry.filters.apply')}
              </Button>
            </Stack>
          </Stack>
        </Stack>
      </Popover>
    </Box>
  )
}
