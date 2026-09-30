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

import { Box } from '@wso2/oxygen-ui'
import { useTranslation } from 'react-i18next'
import ConsentActionDialog from '../../my-consents/components/ConsentActionDialog'
import type { TopicRecord } from '../../../types/topic'

interface TopicDeleteDialogProps {
  open: boolean
  topic: TopicRecord
  loading: boolean
  error?: string
  onClose: () => void
  onConfirm: () => void
}

export default function TopicDeleteDialog({
  open,
  topic,
  loading,
  error,
  onClose,
  onConfirm,
}: TopicDeleteDialogProps): React.JSX.Element {
  const { t } = useTranslation('common')

  const rawMessage = t('topics.deleteModal.message', { name: topic.name })
  const [prefix, ...rest] = rawMessage.split(topic.name)
  const suffix = rest.join(topic.name)
  const message =
    rest.length > 0 ? (
      <>
        {prefix}
        <Box component="span" sx={{ fontWeight: 700, color: 'text.primary' }}>
          {topic.name}
        </Box>
        {suffix}
      </>
    ) : (
      rawMessage
    )

  return (
    <ConsentActionDialog
      open={open}
      consentId={topic.name}
      showId={false}
      title={t('topics.deleteModal.title')}
      message={message}
      note={t('topics.deleteModal.note')}
      confirmLabel={t('topics.deleteModal.confirm')}
      color="error"
      loading={loading}
      error={error}
      onClose={onClose}
      onConfirm={onConfirm}
    />
  )
}

TopicDeleteDialog.defaultProps = {
  error: undefined,
}
