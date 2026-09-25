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
  Autocomplete,
  Box,
  Button,
  CircularProgress,
  InputLabel,
  Paper,
  Stack,
  TextField,
  Typography,
} from '@wso2/oxygen-ui'
import { forwardRef, useEffect, useMemo, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { buildPurposeFilter } from '../../catalog/api/catalogApi'
import { usePurposesQuery } from '../../catalog/hooks/useCatalogQueries'

interface SubscriptionPurposePickerProps {
  selected: string[]
  disabled?: boolean
  error?: string
  onChange: (purposes: string[]) => void
}

/**
 * The Identity Server caps paginated results at 100 regardless of the limit
 * requested, so a static fetch can never surface every purpose once the catalog
 * grows past that. Typing into the picker searches server-side via
 * {@code buildPurposeFilter}, the same filter the Purposes list page uses.
 */
const PURPOSE_PICKER_PAGE_SIZE = 100
const PURPOSE_SEARCH_DEBOUNCE_MS = 300
export const PAGE_SIZE = 5

/** Multi-select against the tenant's Purpose catalog. */
function SubscriptionPurposePicker({
  selected,
  disabled,
  error,
  onChange,
}: SubscriptionPurposePickerProps): React.JSX.Element {
  const { t } = useTranslation('common')
  const [inputValue, setInputValue] = useState('')
  const [searchTerm, setSearchTerm] = useState('')
  const [page, setPage] = useState(1)

  useEffect(() => {
    const timer = setTimeout(() => setSearchTerm(inputValue), PURPOSE_SEARCH_DEBOUNCE_MS)
    return () => clearTimeout(timer)
  }, [inputValue])

  useEffect(() => {
    setPage(1)
  }, [searchTerm])

  const purposesQuery = usePurposesQuery({
    limit: PURPOSE_PICKER_PAGE_SIZE,
    filter: buildPurposeFilter(searchTerm, ''),
  })
  const options = purposesQuery.data?.Purposes ?? []

  const totalPages = Math.max(1, Math.ceil(options.length / PAGE_SIZE))

  const pagedOptions = useMemo(() => {
    const start = (page - 1) * PAGE_SIZE
    return options.slice(start, start + PAGE_SIZE)
  }, [options, page])

  // A selection may not be on this page of options (the query is still pending,
  // or the catalog exceeds the page size), so fall back to metadata carried on
  // `selected` rather than silently dropping it.
  //
  // Memoized so this array keeps the same reference across renders that don't
  // actually change the selection — otherwise Autocomplete sees a "new" value
  // on every keystroke and resets its typed input text back to empty.
  const selectedOptions = useMemo(
    () =>
      selected.map((name) => {
        const found = options.find((option) => option.name === name)
        return (
          found ?? {
            id: `__fallback-${name}`,
            name,
            type: '',
          }
        )
      }),
    // eslint-disable-next-line react-hooks/exhaustive-deps -- re-deriving on every `options` page change would recreate this on every keystroke, defeating the memoization
    [selected],
  )

  const handleSelectAll = (): void => {
    const matchingNames = options.map((item) => item.name)
    const combined = Array.from(new Set([...selected, ...matchingNames]))
    onChange(combined)
  }

  const isAllMatchingSelected =
    options.length > 0 && options.every((item) => selected.includes(item.name))

  const PaperComponent = useMemo(() => {
    return forwardRef<HTMLDivElement, React.HTMLAttributes<HTMLDivElement>>(function PagedPaper(
      { children, ...paperProps },
      ref,
    ) {
      return (
        // eslint-disable-next-line react/jsx-props-no-spreading -- forward Paper props
        <Paper ref={ref} {...paperProps}>
          {children}
          {options.length > PAGE_SIZE ? (
            <Box
              onMouseDown={(e) => e.preventDefault()}
              sx={{
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'space-between',
                px: 1.5,
                py: 1,
                borderTop: 1,
                borderColor: 'divider',
                position: 'sticky',
                bottom: 0,
                bgcolor: 'background.paper',
                zIndex: 1,
              }}
            >
              <Typography variant="caption" color="text.secondary">
                {t('subscriptions.topicUi.showingRange', 'Showing {{from}}–{{to}} of {{total}}', {
                  from: (page - 1) * PAGE_SIZE + 1,
                  to: Math.min(page * PAGE_SIZE, options.length),
                  total: options.length,
                })}
              </Typography>
              <Stack direction="row" spacing={1}>
                <Button
                  size="small"
                  variant="outlined"
                  disabled={page <= 1}
                  onMouseDown={(e) => e.preventDefault()}
                  onClick={() => setPage((p) => Math.max(1, p - 1))}
                >
                  {t('subscriptions.topicUi.back', 'Back')}
                </Button>
                <Button
                  size="small"
                  variant="outlined"
                  disabled={page >= totalPages}
                  onMouseDown={(e) => e.preventDefault()}
                  onClick={() => setPage((p) => Math.min(totalPages, p + 1))}
                >
                  {t('subscriptions.topicUi.next', 'Next')}
                </Button>
              </Stack>
            </Box>
          ) : null}
        </Paper>
      )
    })
  }, [options.length, page, t, totalPages])

  return (
    <Box>
      <Box
        sx={{
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'space-between',
          mb: 0.75,
        }}
      >
        <InputLabel
          htmlFor="subscription-purposes-input"
          shrink={false}
          sx={{
            position: 'static',
            transform: 'none',
            fontWeight: 500,
            fontSize: '0.875rem',
            color: 'text.primary',
            '&.Mui-focused': { color: 'text.primary' },
          }}
        >
          {t('subscriptions.dialog.purposesLabel', 'Consent Purposes')}
          <Box component="span" sx={{ color: 'error.main', ml: 0.5 }}>
            *
          </Box>
        </InputLabel>
        <Stack direction="row" spacing={0.5} alignItems="center">
          <Button
            size="small"
            variant="text"
            disabled={disabled || options.length === 0 || isAllMatchingSelected}
            onClick={handleSelectAll}
            sx={{
              minWidth: 'auto',
              p: 0,
              fontSize: '0.75rem',
              fontWeight: 500,
              color: '#ff7300',
              textTransform: 'none',
              '&:hover': {
                bgcolor: 'transparent',
                textDecoration: 'underline',
              },
              '&.Mui-disabled': {
                color: 'text.disabled',
              },
            }}
          >
            {t('subscriptions.topicUi.selectAll', 'Select all')}
          </Button>
          <Typography variant="caption" sx={{ color: 'text.secondary', px: 0.25 }}>
            |
          </Typography>
          <Button
            size="small"
            variant="text"
            disabled={disabled || selected.length === 0}
            onClick={() => onChange([])}
            sx={{
              minWidth: 'auto',
              p: 0,
              fontSize: '0.75rem',
              fontWeight: 500,
              color: '#ff7300',
              textTransform: 'none',
              '&:hover': {
                bgcolor: 'transparent',
                textDecoration: 'underline',
              },
              '&.Mui-disabled': {
                color: 'text.disabled',
              },
            }}
          >
            {t('subscriptions.topicUi.clear', 'Clear')}
          </Button>
        </Stack>
      </Box>
      <Autocomplete
        multiple
        disabled={disabled}
        loading={purposesQuery.isPending}
        options={pagedOptions}
        value={selectedOptions}
        inputValue={inputValue}
        onInputChange={(_event, newInputValue) => setInputValue(newInputValue)}
        // The options list is already name-filtered server-side (see
        // usePurposesQuery above); re-filtering client-side here would hide
        // results whose display text doesn't share the typed substring.
        filterOptions={(currentOptions) => currentOptions}
        getOptionLabel={(option) => (option.type ? `${option.name} — ${option.type}` : option.name)}
        isOptionEqualToValue={(option, value) => option.id === value.id}
        onChange={(_event, newValue) => {
          onChange(newValue.map((option) => option.name))
        }}
        PaperComponent={PaperComponent}
        renderInput={(params) => (
          <TextField
            // eslint-disable-next-line react/jsx-props-no-spreading -- MUI's Autocomplete requires forwarding all of `params`
            {...params}
            id="subscription-purposes-input"
            size="small"
            placeholder={t('subscriptions.dialog.purposesPlaceholder')}
            error={Boolean(error)}
            helperText={error}
            inputProps={{
              ...params.inputProps,
              'aria-label': t('subscriptions.dialog.purposesLabel', 'Consent Purposes'),
            }}
            InputProps={{
              ...params.InputProps,
              endAdornment: (
                <>
                  {purposesQuery.isPending ? <CircularProgress color="inherit" size={16} /> : null}
                  {params.InputProps.endAdornment}
                </>
              ),
            }}
          />
        )}
      />
      {purposesQuery.isError ? (
        <Alert
          severity="error"
          sx={{ mt: 1 }}
          action={
            <Button size="small" onClick={() => purposesQuery.refetch()}>
              {t('catalog.actions.retry')}
            </Button>
          }
        >
          {t('subscriptions.dialog.purposesLoadFailed')}
        </Alert>
      ) : null}
      {!purposesQuery.isPending && !purposesQuery.isError && options.length === 0 ? (
        <Typography variant="body2" color="text.secondary" sx={{ mt: 0.5 }}>
          {searchTerm.trim()
            ? t('subscriptions.dialog.purposesNoMatches')
            : t('subscriptions.dialog.purposesEmpty')}
        </Typography>
      ) : null}
    </Box>
  )
}

export default SubscriptionPurposePicker
