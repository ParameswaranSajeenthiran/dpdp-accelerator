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
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  FormControl,
  InputLabel,
  MenuItem,
  Paper,
  Select,
  Stack,
  TextField,
  Typography,
} from '@wso2/oxygen-ui'
import { forwardRef, useEffect, useMemo, useState } from 'react'
import { useTranslation } from 'react-i18next'
import useSubscriptionTopicPicker from '../hooks/useSubscriptionTopicPicker'
import { MAX_SUBSCRIPTION_TOPICS } from '../constants'
import { TOPIC_CATEGORIES, type TopicCategory, getTopicCategory } from '../utils/topicCategory'
import type { TopicRecord } from '../../../types/topic'

export const PAGE_SIZE = 5

interface Props {
  selected: string[]
  onChange: (topics: string[]) => void
  onBusyChange?: (busy: boolean) => void
  disabled?: boolean
}

export default function SubscriptionTopicPicker({
  selected,
  onChange,
  onBusyChange,
  disabled = false,
}: Props): React.JSX.Element {
  const { t } = useTranslation('common')
  const [category, setCategory] = useState<TopicCategory | ''>('')
  const [pendingCategory, setPendingCategory] = useState<TopicCategory | null>(null)
  const [inputValue, setInputValue] = useState('')
  const [searchTerm, setSearchTerm] = useState('')
  const [page, setPage] = useState(1)

  useEffect(() => {
    const timer = setTimeout(() => setSearchTerm(inputValue), 300)
    return () => clearTimeout(timer)
  }, [inputValue])

  const query = useSubscriptionTopicPicker()

  useEffect(() => {
    onBusyChange?.(false)
  }, [onBusyChange])

  // Reset page when category or search changes
  useEffect(() => {
    setPage(1)
  }, [category, searchTerm])

  const allTopics = query.data?.items ?? []

  // Filter topics by selected category
  const categoryTopics: TopicRecord[] = useMemo(() => {
    if (!category) return []
    return allTopics.filter((topic) => getTopicCategory(topic) === category)
  }, [allTopics, category])

  // Filter category topics by typed search term
  const filteredTopics: TopicRecord[] = useMemo(() => {
    const term = searchTerm.trim().toLowerCase()
    if (!term) return categoryTopics
    return categoryTopics.filter((topic) => topic.name.toLowerCase().includes(term))
  }, [categoryTopics, searchTerm])

  const totalPages = Math.max(1, Math.ceil(filteredTopics.length / PAGE_SIZE))

  // Sliced page options for Autocomplete
  const pagedOptions: TopicRecord[] = useMemo(() => {
    const start = (page - 1) * PAGE_SIZE
    return filteredTopics.slice(start, start + PAGE_SIZE)
  }, [filteredTopics, page])

  // Preserve selected options across pages and categories
  const selectedOptions = useMemo(
    () =>
      selected.map(
        (name) =>
          allTopics.find((option) => option.name === name) ?? {
            topicId: name,
            name,
            status: 'ACTIVE',
          },
      ),
    [selected, allTopics],
  )

  const applyCategoryChange = (nextCategory: TopicCategory): void => {
    setCategory(nextCategory)
    onChange([])
    setInputValue('')
    setSearchTerm('')
    setPage(1)
    setPendingCategory(null)
  }

  const handleCategorySelect = (nextCategory: TopicCategory): void => {
    if (nextCategory === category) return
    if (selected.length > 0) {
      setPendingCategory(nextCategory)
    } else {
      applyCategoryChange(nextCategory)
    }
  }

  const handleCancelCategoryChange = (): void => {
    setPendingCategory(null)
  }

  const handleSelectAll = (): void => {
    const matchingNames = filteredTopics.map((item) => item.name)
    const combined = Array.from(new Set([...selected, ...matchingNames])).slice(
      0,
      MAX_SUBSCRIPTION_TOPICS,
    )
    onChange(combined)
  }

  const isAllMatchingSelected =
    filteredTopics.length > 0 && filteredTopics.every((topic) => selected.includes(topic.name))

  const PaperComponent = useMemo(() => {
    return forwardRef<HTMLDivElement, React.HTMLAttributes<HTMLDivElement>>(function PagedPaper(
      { children, ...paperProps },
      ref,
    ) {
      return (
        // eslint-disable-next-line react/jsx-props-no-spreading -- forward Paper props
        <Paper ref={ref} {...paperProps}>
          {children}
          {filteredTopics.length > PAGE_SIZE ? (
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
                  to: Math.min(page * PAGE_SIZE, filteredTopics.length),
                  total: filteredTopics.length,
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
  }, [filteredTopics.length, page, t, totalPages])

  return (
    <Stack spacing={2}>
      {/* Category Dropdown */}
      <FormControl fullWidth size="small">
        <InputLabel id="topic-category-label">
          {t('subscriptions.topicUi.category', 'Topic Category')}
        </InputLabel>
        <Select
          labelId="topic-category-label"
          id="topic-category-select"
          value={category}
          label={t('subscriptions.topicUi.category', 'Topic Category')}
          disabled={disabled}
          onChange={(event) => {
            const nextCategory = event.target.value as TopicCategory
            handleCategorySelect(nextCategory)
          }}
        >
          {TOPIC_CATEGORIES.map((cat) => (
            <MenuItem key={cat} value={cat}>
              {cat === 'consent'
                ? t('subscriptions.topicUi.categoryConsent', 'Consent Topics')
                : cat === 'user'
                  ? t('subscriptions.topicUi.categoryUser', 'User Topics')
                  : t('subscriptions.topicUi.categoryCustom', 'Custom Topics')}
            </MenuItem>
          ))}
        </Select>
      </FormControl>

      {/* Topics Autocomplete */}
      <Autocomplete
        multiple
        disabled={disabled || !category}
        loading={query.isPending}
        options={pagedOptions}
        value={selectedOptions}
        inputValue={inputValue}
        onOpen={() => {
          void query.refetch()
        }}
        onInputChange={(_event, newInputValue) => setInputValue(newInputValue)}
        filterOptions={(currentOptions) => currentOptions}
        getOptionLabel={(option) => option.name}
        isOptionEqualToValue={(option, val) => option.name === val.name}
        onChange={(_event, newValue) => {
          const next = newValue.map((item) => item.name)
          if (next.length <= MAX_SUBSCRIPTION_TOPICS) {
            onChange(next)
          }
        }}
        PaperComponent={PaperComponent}
        renderInput={(params) => (
          <TextField
            // eslint-disable-next-line react/jsx-props-no-spreading
            {...params}
            size="small"
            label={t('subscriptions.topicUi.topics', 'Topics')}
            placeholder={
              category
                ? t('subscriptions.topicUi.search', 'Search topics by name')
                : t(
                    'subscriptions.topicUi.categoryRequired',
                    'Select a category first to choose topics',
                  )
            }
          />
        )}
      />

      {/* Selection Summary and Actions */}
      {category ? (
        <Box sx={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
          <Typography variant="caption" color="text.secondary">
            {t('subscriptions.topicUi.selected', '{{count}} selected · maximum {{max}}', {
              count: selected.length,
              max: MAX_SUBSCRIPTION_TOPICS,
            })}
          </Typography>
          <Stack direction="row" spacing={1}>
            <Button
              size="small"
              variant="text"
              disabled={
                disabled ||
                selected.length >= MAX_SUBSCRIPTION_TOPICS ||
                filteredTopics.length === 0 ||
                isAllMatchingSelected
              }
              onClick={handleSelectAll}
            >
              {t('subscriptions.topicUi.selectAll', 'Select all')}
            </Button>
            {selected.length > 0 ? (
              <Button
                size="small"
                variant="text"
                color="error"
                disabled={disabled}
                onClick={() => onChange([])}
              >
                {t('subscriptions.topicUi.clear', 'Clear')}
              </Button>
            ) : null}
          </Stack>
        </Box>
      ) : null}

      {query.isError ? (
        <Alert severity="error">
          {t('subscriptions.topicUi.fetchError', 'Could not load topics. Please retry.')}
        </Alert>
      ) : null}

      {/* Category Change Confirmation Dialog */}
      <Dialog
        open={Boolean(pendingCategory)}
        onClose={handleCancelCategoryChange}
        transitionDuration={0}
        maxWidth="xs"
        fullWidth
      >
        <DialogTitle>
          {t('subscriptions.topicUi.switchCategoryTitle', 'Change topic category?')}
        </DialogTitle>
        <DialogContent>
          <Typography variant="body2">
            {t(
              'subscriptions.topicUi.switchCategoryConfirm',
              'Changing the category will clear your {{count}} selected topic(s). Do you want to continue?',
              { count: selected.length },
            )}
          </Typography>
        </DialogContent>
        <DialogActions>
          <Button variant="outlined" onClick={handleCancelCategoryChange}>
            {t('consentRegistry.modals.actions.cancel', 'Cancel')}
          </Button>
          <Button
            variant="contained"
            color="warning"
            onClick={() => {
              if (pendingCategory) applyCategoryChange(pendingCategory)
            }}
          >
            {t('subscriptions.topicUi.switchCategoryProceed', 'Change Category')}
          </Button>
        </DialogActions>
      </Dialog>
    </Stack>
  )
}

SubscriptionTopicPicker.defaultProps = {
  onBusyChange: undefined,
  disabled: false,
}
