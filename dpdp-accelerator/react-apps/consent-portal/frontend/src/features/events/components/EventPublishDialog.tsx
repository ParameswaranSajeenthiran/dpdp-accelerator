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
  Button,
  CircularProgress,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  FormControl,
  FormHelperText,
  InputLabel,
  MenuItem,
  Select,
  Stack,
  TextField,
  Typography,
} from '@wso2/oxygen-ui'
import { Send } from '@wso2/oxygen-ui-icons-react'
import { useEffect, useState } from 'react'
import { useTranslation } from 'react-i18next'
import type { EventInput } from '../../../types/event'
import { fetchTopics } from '../api/topicsApi'

interface EventPublishDialogProps {
  open: boolean
  loading: boolean
  error?: string
  onClose: () => void
  onSubmit: (payload: EventInput) => void
}

const DEFAULT_SAMPLE_PAYLOAD = JSON.stringify(
  {
    consentId: 'sample-consent-id',
    status: 'REVOKED',
    timestamp: new Date().toISOString(),
  },
  null,
  2,
)

export default function EventPublishDialog({
  open,
  loading,
  error,
  onClose,
  onSubmit,
}: EventPublishDialogProps): React.JSX.Element {
  const { t } = useTranslation('common')

  const [topics, setTopics] = useState<string[]>([])
  const [topicsLoading, setTopicsLoading] = useState(false)

  const [topicName, setTopicName] = useState('')
  const [groupId, setGroupId] = useState('')
  const [purposesInput, setPurposesInput] = useState('')
  const [payloadText, setPayloadText] = useState(DEFAULT_SAMPLE_PAYLOAD)

  const [topicError, setTopicError] = useState('')
  const [payloadError, setPayloadError] = useState('')

  useEffect(() => {
    if (open) {
      setTopicsLoading(true)
      fetchTopics({ limit: 100, offset: 0, status: 'ACTIVE' })
        .then((res) => {
          const names = (res.items ?? []).map((item) => item.name)
          setTopics(names)
          if (names.length > 0 && !topicName) {
            setTopicName(names[0])
          }
        })
        .catch(() => setTopics([]))
        .finally(() => setTopicsLoading(false))
    }
  }, [open])

  const handleSubmit = (event: React.FormEvent): void => {
    event.preventDefault()

    let hasError = false

    if (!topicName.trim()) {
      setTopicError(t('events.dialog.topicRequired'))
      hasError = true
    } else {
      setTopicError('')
    }

    let parsedPayload: Record<string, unknown> = {}
    if (!payloadText.trim()) {
      setPayloadError(t('events.dialog.payloadRequired'))
      hasError = true
    } else {
      try {
        const parsed = JSON.parse(payloadText.trim())
        if (typeof parsed !== 'object' || parsed === null || Array.isArray(parsed)) {
          setPayloadError(t('events.dialog.payloadMustBeObject'))
          hasError = true
        } else {
          parsedPayload = parsed as Record<string, unknown>
          setPayloadError('')
        }
      } catch {
        setPayloadError(t('events.dialog.invalidJson'))
        hasError = true
      }
    }

    if (hasError) return

    const trimmedPurposes = purposesInput
      .split(',')
      .map((p) => p.trim())
      .filter(Boolean)

    onSubmit({
      topicName: topicName.trim(),
      groupId: groupId.trim() || undefined,
      purposes: trimmedPurposes.length > 0 ? trimmedPurposes : undefined,
      payload: parsedPayload,
    })
  }

  return (
    <Dialog
      open={open}
      onClose={loading ? undefined : onClose}
      maxWidth="sm"
      fullWidth
      PaperProps={{
        sx: (theme) => ({
          borderRadius: 1,
          ...theme.applyStyles('light', { bgcolor: theme.palette.grey[50] }),
          ...theme.applyStyles('dark', { bgcolor: 'rgba(255, 255, 255, 0.06)' }),
        }),
      }}
    >
      <DialogTitle
        sx={{
          p: 3,
          borderBottom: 1,
          borderColor: 'divider',
          textAlign: 'center',
        }}
      >
        <Stack spacing={0.75}>
          <Typography variant="h6" fontWeight={700}>
            {t('events.dialog.publishTitle')}
          </Typography>
          <Typography variant="body2" color="text.secondary">
            {t('events.dialog.publishSubtitle')}
          </Typography>
        </Stack>
      </DialogTitle>

      <form onSubmit={handleSubmit}>
        <DialogContent sx={{ px: 3, py: 3 }}>
          <Stack spacing={2.5}>
            {error ? <Alert severity="error">{error}</Alert> : null}

            <FormControl fullWidth size="small" required error={Boolean(topicError)}>
              <InputLabel id="event-topic-select-label">
                {t('events.dialog.topicLabel')}
              </InputLabel>
              <Select
                labelId="event-topic-select-label"
                label={t('events.dialog.topicLabel')}
                value={topicName}
                disabled={topicsLoading}
                onChange={(e) => {
                  setTopicName(e.target.value)
                  if (topicError) setTopicError('')
                }}
              >
                {topics.map((name) => (
                  <MenuItem key={name} value={name}>
                    {name}
                  </MenuItem>
                ))}
              </Select>
              {topicError ? <FormHelperText>{topicError}</FormHelperText> : null}
            </FormControl>

            <TextField
              fullWidth
              size="small"
              label={t('events.dialog.groupIdLabel')}
              placeholder={t('events.dialog.groupIdPlaceholder')}
              value={groupId}
              onChange={(e) => setGroupId(e.target.value)}
              helperText={t('events.dialog.groupIdHelper')}
            />

            <TextField
              fullWidth
              size="small"
              label={t('events.dialog.purposesLabel')}
              placeholder={t('events.dialog.purposesPlaceholder')}
              value={purposesInput}
              helperText={t('events.dialog.purposesHelper')}
              onChange={(e) => setPurposesInput(e.target.value)}
            />

            <TextField
              required
              fullWidth
              multiline
              rows={6}
              size="small"
              label={t('events.dialog.payloadLabel')}
              value={payloadText}
              error={Boolean(payloadError)}
              helperText={payloadError || t('events.dialog.payloadHelper')}
              onChange={(e) => {
                setPayloadText(e.target.value)
                if (payloadError) setPayloadError('')
              }}
              InputProps={{
                sx: { fontFamily: 'monospace', fontSize: '0.85rem' },
              }}
            />
          </Stack>
        </DialogContent>

        <DialogActions
          sx={{
            p: 3,
            pt: 2,
            borderTop: 1,
            borderColor: 'divider',
            bgcolor: 'background.default',
            flexDirection: 'column',
            gap: 1.25,
          }}
        >
          <Button
            fullWidth
            type="submit"
            variant="contained"
            color="primary"
            startIcon={loading ? <CircularProgress size={16} color="inherit" /> : <Send size={16} />}
            disabled={loading || topicsLoading}
          >
            {loading ? t('events.dialog.publishing') : t('events.dialog.publishSubmit')}
          </Button>
          <Button fullWidth variant="outlined" disabled={loading} onClick={onClose}>
            {t('consentRegistry.modals.actions.cancel')}
          </Button>
        </DialogActions>
      </form>
    </Dialog>
  )
}
