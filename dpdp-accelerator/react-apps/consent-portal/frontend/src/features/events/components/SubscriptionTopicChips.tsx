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

import { Button, Chip, Stack } from '@wso2/oxygen-ui'
import { useState } from 'react'
import { useTranslation } from 'react-i18next'

interface Props {
  topics: string[]
}
export default function SubscriptionTopicChips({ topics }: Props): React.JSX.Element {
  const { t } = useTranslation('common')
  const [expanded, setExpanded] = useState(false)
  return (
    <Stack direction="row" useFlexGap flexWrap="wrap" gap={0.75}>
      {(expanded ? topics : topics.slice(0, 2)).map((topic) => (
        <Chip key={topic} label={topic} title={topic} size="small" sx={{ maxWidth: '100%' }} />
      ))}
      {topics.length > 2 ? (
        <Button size="small" aria-expanded={expanded} onClick={() => setExpanded(!expanded)}>
          {t(expanded ? 'subscriptions.topicUi.showLess' : 'subscriptions.topicUi.more', {
            count: topics.length - 2,
          })}
        </Button>
      ) : null}
    </Stack>
  )
}
