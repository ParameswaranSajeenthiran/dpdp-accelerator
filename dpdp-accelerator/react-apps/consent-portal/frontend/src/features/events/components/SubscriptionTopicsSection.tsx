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
  Card,
  CardContent,
  CardHeader,
  Button,
  Chip,
  Collapse,
  Divider,
  Stack,
  TextField,
  Typography,
} from '@wso2/oxygen-ui'
import { ChevronDown } from '@wso2/oxygen-ui-icons-react'
import { useState } from 'react'
import { useTranslation } from 'react-i18next'

interface Props {
  topics: string[]
}
export default function SubscriptionTopicsSection({ topics }: Props): React.JSX.Element {
  const { t } = useTranslation('common')
  const [search, setSearch] = useState('')
  const [expanded, setExpanded] = useState(false)
  const matches = topics.filter((topic) =>
    topic.toLocaleLowerCase().includes(search.trim().toLocaleLowerCase()),
  )
  return (
    <Card sx={{ border: 1, borderColor: 'divider', boxShadow: 1 }}>
      <CardHeader
        sx={{
          alignItems: { xs: 'flex-start', sm: 'center' },
          flexWrap: { xs: 'wrap', sm: 'nowrap' },
          gap: { xs: 2, sm: 0 },
          '& .MuiCardHeader-action': {
            alignSelf: { xs: 'stretch', sm: 'center' },
            m: 0,
            width: { xs: '100%', sm: 'auto' },
          },
        }}
        title={
          <Button
            color="inherit"
            onClick={() => setExpanded(!expanded)}
            aria-expanded={expanded}
            aria-controls="subscribed-topic-content"
            aria-label={t('subscriptions.topicUi.subscribedCount', { count: topics.length }).replace(/\s*\([^)]*\)\s*$/, '')}
            sx={{
              p: 0,
              minWidth: 'auto',
              textTransform: 'none',
              display: 'inline-flex',
              alignItems: 'center',
              gap: 1,
              color: 'text.primary',
              '&:hover': { bgcolor: 'transparent', opacity: 0.8 },
            }}
          >
            <Typography variant="h6" fontWeight={700} component="span">
              {t('subscriptions.topicUi.subscribedCount', { count: topics.length }).replace(/\s*\([^)]*\)\s*$/, '')}
            </Typography>
            <Chip
              size="small"
              label={topics.length.toString()}
              color="primary"
              variant="filled"
              sx={{ height: 20, fontSize: '0.75rem', fontWeight: 600 }}
            />
            <ChevronDown
              size={20}
              style={{
                transform: expanded ? 'rotate(180deg)' : 'rotate(0deg)',
                transition: 'transform 200ms ease-in-out',
              }}
            />
          </Button>
        }
        subheader={
          <Typography variant="body2" color="text.secondary">
            {t('subscriptions.details.topicsSubtitle')}
          </Typography>
        }
        action={
          <TextField
            size="small"
            label={t('subscriptions.topicUi.searchAssociated')}
            value={search}
            sx={{ width: { xs: '100%', sm: 280 }, maxWidth: '100%' }}
            onChange={(event) => {
              setSearch(event.target.value)
              setExpanded(true)
            }}
          />
        }
      />
      <Collapse in={expanded}>
        <Divider />
        <CardContent id="subscribed-topic-content" sx={{ p: 3 }}>
          <Stack spacing={2}>
            <Stack direction="row" useFlexGap flexWrap="wrap" gap={1}>
              {matches.map((topic) => (
                <Chip
                  key={topic}
                  label={topic}
                  title={topic}
                  variant="outlined"
                  sx={{ maxWidth: '100%' }}
                />
              ))}
            </Stack>
            {!matches.length ? (
              <Typography role="status">{t('subscriptions.topicUi.noMatches')}</Typography>
            ) : null}
          </Stack>
        </CardContent>
      </Collapse>
    </Card>
  )
}
