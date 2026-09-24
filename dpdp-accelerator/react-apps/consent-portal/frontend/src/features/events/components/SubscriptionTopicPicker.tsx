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
  FormControl,
  InputLabel,
  MenuItem,
  Select,
  Stack,
  TextField,
} from '@wso2/oxygen-ui'
import { useEffect, useMemo, useState } from 'react'
import { useTranslation } from 'react-i18next'
import useSubscriptionTopicPicker from '../hooks/useSubscriptionTopicPicker'
import { MAX_SUBSCRIPTION_TOPICS } from '../constants'
import { TOPIC_CATEGORIES, type TopicCategory, getTopicCategory } from '../utils/topicCategory'
import type { TopicRecord } from '../../../types/topic'

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
  const [inputValue, setInputValue] = useState('')
  const [searchTerm, setSearchTerm] = useState('')

  useEffect(() => {
    const timer = setTimeout(() => setSearchTerm(inputValue), 300)
    return () => clearTimeout(timer)
  }, [inputValue])

  const query = useSubscriptionTopicPicker(searchTerm)

  useEffect(() => {
    onBusyChange?.(query.isPending)
  }, [query.isPending, onBusyChange])

  const options: TopicRecord[] = useMemo(() => {
    const items = query.data?.items ?? []
    if (!category) return []
    return items.filter((topic) => getTopicCategory(topic) === category)
  }, [query.data, category])

  const selectedOptions = useMemo(
    () =>
      selected.map(
        (name) =>
          options.find((option) => option.name === name) ?? {
            topicId: name,
            name,
            status: 'ACTIVE',
          },
      ),
    // eslint-disable-next-line react-hooks/exhaustive-deps -- re-deriving on every options change would recreate on every keystroke
    [selected],
  )

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
            setCategory(nextCategory)
            if (nextCategory !== category) {
              onChange([])
            }
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

      {/* Topics Autocomplete - One to one with PurposeElementPicker */}
      <Autocomplete
        multiple
        disabled={disabled || !category}
        loading={query.isPending}
        options={options}
        value={selectedOptions}
        inputValue={inputValue}
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
        renderInput={(params) => (
          // eslint-disable-next-line react/jsx-props-no-spreading -- MUI's Autocomplete requires forwarding all of `params`
          <TextField
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

      {query.isError ? (
        <Alert severity="error">
          {t('subscriptions.topicUi.fetchError', 'Could not load topics. Please retry.')}
        </Alert>
      ) : null}
    </Stack>
  )
}

SubscriptionTopicPicker.defaultProps = {
  onBusyChange: undefined,
  disabled: false,
}
