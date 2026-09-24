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
  Box,
  Button,
  Checkbox,
  Chip,
  Collapse,
  Divider,
  FormControlLabel,
  IconButton,
  LinearProgress,
  Stack,
  TextField,
  Typography,
} from '@wso2/oxygen-ui'
import { ChevronDown } from '@wso2/oxygen-ui-icons-react'
import { useMutation } from '@tanstack/react-query'
import { useState, useMemo } from 'react'
import { useTranslation } from 'react-i18next'
import useSubscriptionTopicPicker, {
  collectMatchingTopics,
} from '../hooks/useSubscriptionTopicPicker'
import { MAX_SUBSCRIPTION_TOPICS } from '../constants'

interface Props {
  selected: string[]
  onChange: (topics: string[]) => void
  onBusyChange?: (busy: boolean) => void
}
export default function SubscriptionTopicPicker({
  selected,
  onChange,
  onBusyChange,
}: Props): React.JSX.Element {
  const { t } = useTranslation('common')
  const [search, setSearch] = useState('')
  const [selectedExpanded, setSelectedExpanded] = useState(false)
  const query = useSubscriptionTopicPicker(search)
  const bulk = useMutation({ mutationFn: () => collectMatchingTopics(search, selected) })
  const topics = useMemo(() => {
    return [
      ...new Map(
        (query.data?.pages.flatMap((page) => page.items) ?? []).map((topic) => [topic.name, topic]),
      ).values(),
    ]
  }, [query.data])

  const availableTopics = useMemo(() => {
    if (selectedExpanded) {
      return topics.filter((topic) => !selected.includes(topic.name))
    }
    return topics
  }, [topics, selected, selectedExpanded])

  return (
    <Box sx={{ display: 'flex', flexDirection: 'column', height: '100%', maxHeight: 520 }}>
      {/* Sticky Header: Search Input, Collapsible Selected Topics, and Available Topics Controls */}
      <Box
        sx={{
          position: 'sticky',
          top: 0,
          zIndex: 1,
          bgcolor: 'background.paper',
          pb: 1.5,
          display: 'flex',
          flexDirection: 'column',
          gap: 1.5,
        }}
      >
        <TextField
          disabled={bulk.isPending}
          autoFocus
          size="small"
          label={t('subscriptions.topicUi.search')}
          value={search}
          onChange={(event) => setSearch(event.target.value)}
        />

        <Divider />

        {/* Collapsible Selected Topics Section */}
        <Box
          sx={{
            border: 1,
            borderColor: 'divider',
            borderRadius: 1,
            bgcolor: (theme) =>
              theme.palette.mode === 'dark' ? 'rgba(255, 255, 255, 0.03)' : 'rgba(0, 0, 0, 0.02)',
            overflow: 'hidden',
          }}
        >
          <Box
            role="button"
            tabIndex={0}
            aria-expanded={selectedExpanded}
            aria-controls="selected-topics-list"
            onClick={() => setSelectedExpanded(!selectedExpanded)}
            onKeyDown={(e) => {
              if (e.key === 'Enter' || e.key === ' ') {
                e.preventDefault()
                setSelectedExpanded(!selectedExpanded)
              }
            }}
            sx={{
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'space-between',
              px: 1.5,
              py: 1,
              cursor: 'pointer',
              userSelect: 'none',
              '&:hover': {
                bgcolor: (theme) =>
                  theme.palette.mode === 'dark'
                    ? 'rgba(255, 255, 255, 0.06)'
                    : 'rgba(0, 0, 0, 0.04)',
              },
            }}
          >
            <Stack direction="row" spacing={1} alignItems="center" role="status">
              <Typography variant="body2" fontWeight={600} color="text.secondary">
                {t('subscriptions.topicUi.selectedTopics')}
              </Typography>
              <Chip
                size="small"
                label={selected.length.toString()}
                color="primary"
                variant="filled"
                sx={{
                  height: 20,
                  minWidth: 20,
                  px: 0.5,
                  fontSize: '0.75rem',
                  fontWeight: 700,
                  borderRadius: '4px',
                }}
              />
            </Stack>
            <IconButton
              size="small"
              tabIndex={-1}
              aria-hidden="true"
              sx={{
                p: 0.25,
                transform: selectedExpanded ? 'rotate(180deg)' : 'none',
                transition: 'transform 0.2s ease-in-out',
              }}
            >
              <ChevronDown size={18} />
            </IconButton>
          </Box>

          <Collapse in={selectedExpanded} id="selected-topics-list">
            <Box
              sx={{
                px: 1.5,
                py: 1,
                borderTop: 1,
                borderColor: 'divider',
                maxHeight: 140,
                overflowY: 'auto',
              }}
            >
              {selected.length === 0 ? (
                <Typography
                  variant="body2"
                  color="text.secondary"
                  sx={{ py: 0.5, fontStyle: 'italic' }}
                >
                  {t('subscriptions.topicUi.noSelected')}
                </Typography>
              ) : (
                <Stack spacing={0.5}>
                  {selected.map((topicName) => (
                    <FormControlLabel
                      key={topicName}
                      label={topicName}
                      control={
                        <Checkbox
                          checked={true}
                          disabled={bulk.isPending}
                          onChange={() => onChange(selected.filter((name) => name !== topicName))}
                        />
                      }
                    />
                  ))}
                </Stack>
              )}
            </Box>
          </Collapse>
        </Box>

        <Divider />

        {/* Available Topics Header & Action Buttons */}
        <Stack direction="row" justifyContent="space-between" alignItems="center">
          <Typography variant="body2" fontWeight={600} color="text.secondary">
            {t('subscriptions.topicUi.availableTopics')}
          </Typography>
          <Stack direction="row" spacing={1}>
            <Button
              variant="outlined"
              size="small"
              disabled={bulk.isPending || !topics.length}
              onClick={() => {
                onBusyChange?.(true)
                bulk.mutate(undefined, {
                  onSuccess: (names) => onChange(names),
                  onSettled: () => onBusyChange?.(false),
                })
              }}
            >
              {t('subscriptions.topicUi.selectMatching')}
            </Button>
            <Button
              variant="outlined"
              size="small"
              disabled={bulk.isPending || !selected.length}
              onClick={() => onChange([])}
            >
              {t('subscriptions.topicUi.clear')}
            </Button>
          </Stack>
        </Stack>
      </Box>

      {/* Available Topics Scrollable List */}
      <Box
        sx={{
          flex: 1,
          overflowY: 'auto',
          position: 'relative',
          py: 1,
        }}
      >
        {(query.isFetching || bulk.isPending) && (
          <Box sx={{ position: 'absolute', top: 0, left: 0, right: 0, zIndex: 2 }}>
            <LinearProgress />
          </Box>
        )}
        <Stack spacing={1.5}>
          {bulk.isError ? (
            <Alert severity="error">
              {t(
                bulk.error instanceof RangeError
                  ? 'subscriptions.topicUi.selectionLimit'
                  : 'subscriptions.topicUi.fetchError',
                { max: MAX_SUBSCRIPTION_TOPICS },
              )}
            </Alert>
          ) : null}
          {query.isError ? (
            <Alert
              severity="error"
              action={
                <Button
                  onClick={() => {
                    query.refetch()
                  }}
                >
                  {t('subscriptions.topicUi.retry')}
                </Button>
              }
            >
              {t('subscriptions.topicUi.fetchError')}
            </Alert>
          ) : null}
          {!query.isPending && !query.isError && !topics.length ? (
            <Box sx={{ display: 'flex', justifyContent: 'center', py: 4 }}>
              <Typography>
                {t(
                  search.trim()
                    ? 'subscriptions.topicUi.noMatches'
                    : 'subscriptions.topicUi.noTopics',
                )}
              </Typography>
            </Box>
          ) : null}
          {!query.isPending &&
          !query.isError &&
          topics.length > 0 &&
          availableTopics.length === 0 ? (
            <Box sx={{ display: 'flex', justifyContent: 'center', py: 3 }}>
              <Typography variant="body2" color="text.secondary">
                {t('subscriptions.topicUi.allSelected')}
              </Typography>
            </Box>
          ) : null}
          <Stack spacing={0.5}>
            {availableTopics.map((topic) => {
              const isSelected = selected.includes(topic.name)
              return (
                <FormControlLabel
                  key={topic.topicId ?? topic.name}
                  label={topic.name}
                  control={
                    <Checkbox
                      checked={isSelected}
                      disabled={
                        bulk.isPending ||
                        (!isSelected && selected.length >= MAX_SUBSCRIPTION_TOPICS)
                      }
                      onChange={(_, checked) =>
                        onChange(
                          checked
                            ? [...selected, topic.name]
                            : selected.filter((name) => name !== topic.name),
                        )
                      }
                    />
                  }
                />
              )
            })}
          </Stack>
          {query.hasNextPage ? (
            <Button
              disabled={query.isFetching || bulk.isPending}
              onClick={() => {
                query.fetchNextPage()
              }}
            >
              {t('subscriptions.topicUi.loadMore')}
            </Button>
          ) : null}
        </Stack>
      </Box>

      {/* Footer Helper */}
      <Typography variant="body2" color="text.secondary" sx={{ pt: 1.5 }}>
        {t('subscriptions.topicUi.selectionHelp')}
      </Typography>
    </Box>
  )
}

SubscriptionTopicPicker.defaultProps = { onBusyChange: undefined }
