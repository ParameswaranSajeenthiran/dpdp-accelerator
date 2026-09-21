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
  Button,
  CircularProgress,
  TextField,
  Typography,
} from '@wso2/oxygen-ui'
import { useEffect, useMemo, useState } from 'react'
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

  useEffect(() => {
    const timer = setTimeout(() => setSearchTerm(inputValue), PURPOSE_SEARCH_DEBOUNCE_MS)
    return () => clearTimeout(timer)
  }, [inputValue])

  const purposesQuery = usePurposesQuery({
    limit: PURPOSE_PICKER_PAGE_SIZE,
    filter: buildPurposeFilter(searchTerm, ''),
  })
  const options = purposesQuery.data?.Purposes ?? []

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

  return (
    <>
      <Autocomplete
        multiple
        disabled={disabled}
        loading={purposesQuery.isPending}
        options={options}
        value={selectedOptions}
        inputValue={inputValue}
        onInputChange={(_event, newInputValue) => setInputValue(newInputValue)}
        // The options list is already name-filtered server-side (see
        // usePurposesQuery above); re-filtering client-side here would hide
        // results whose display text doesn't share the typed substring.
        filterOptions={(currentOptions) => currentOptions}
        getOptionLabel={(option) =>
          option.type ? `${option.name} — ${option.type}` : option.name
        }
        isOptionEqualToValue={(option, value) => option.id === value.id}
        onChange={(_event, newValue) => {
          onChange(newValue.map((option) => option.name))
        }}
        renderInput={(params) => (
          <TextField
            // eslint-disable-next-line react/jsx-props-no-spreading -- MUI's Autocomplete requires forwarding all of `params`
            {...params}
            label={t('subscriptions.dialog.purposesLabel')}
            placeholder={t('subscriptions.dialog.purposesPlaceholder')}
            error={Boolean(error)}
            helperText={error || t('subscriptions.dialog.purposesHelper')}
            InputProps={{
              ...params.InputProps,
              endAdornment: (
                <>
                  {purposesQuery.isPending ? (
                    <CircularProgress color="inherit" size={16} />
                  ) : null}
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
        <Typography variant="body2" color="text.secondary">
          {searchTerm.trim()
            ? t('subscriptions.dialog.purposesNoMatches')
            : t('subscriptions.dialog.purposesEmpty')}
        </Typography>
      ) : null}
    </>
  )
}

export default SubscriptionPurposePicker
