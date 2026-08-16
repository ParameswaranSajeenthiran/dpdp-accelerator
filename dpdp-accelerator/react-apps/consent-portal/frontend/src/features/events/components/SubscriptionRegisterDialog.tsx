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
  IconButton,
  InputAdornment,
  InputLabel,
  MenuItem,
  Select,
  Stack,
  TextField,
  Typography,
} from '@wso2/oxygen-ui'
import { Key, Plus, RefreshCw } from '@wso2/oxygen-ui-icons-react'
import { useEffect, useState } from 'react'
import { useTranslation } from 'react-i18next'
import type {
  DeliveryMode,
  PurposeFilterMode,
  SubscriptionInput,
} from '../../../types/subscription'
import { DELIVERY_MODES, PURPOSE_FILTER_MODES } from '../../../types/subscription'
import { fetchTopics } from '../api/topicsApi'

interface SubscriptionRegisterDialogProps {
  open: boolean
  loading: boolean
  error?: string
  onClose: () => void
  onSubmit: (payload: SubscriptionInput) => void
}

function generateRandomHexSecret(length = 32): string {
  const bytes = new Uint8Array(length / 2)
  window.crypto.getRandomValues(bytes)
  return Array.from(bytes, (byte) => byte.toString(16).padStart(2, '0')).join('')
}

export default function SubscriptionRegisterDialog({
  open,
  loading,
  error,
  onClose,
  onSubmit,
}: SubscriptionRegisterDialogProps): React.JSX.Element {
  const { t } = useTranslation('common')

  const [topics, setTopics] = useState<string[]>([])
  const [topicsLoading, setTopicsLoading] = useState(false)

  const [topic, setTopic] = useState('')
  const [groupId, setGroupId] = useState('')
  const [filterMode, setFilterMode] = useState<PurposeFilterMode>('all')
  const [purposesInput, setPurposesInput] = useState('')
  const [deliveryMode, setDeliveryMode] = useState<DeliveryMode>('webhook')
  const [callbackUrl, setCallbackUrl] = useState('')
  const [sharedSecret, setSharedSecret] = useState(() => generateRandomHexSecret())

  const [topicError, setTopicError] = useState('')
  const [callbackUrlError, setCallbackUrlError] = useState('')
  const [purposesError, setPurposesError] = useState('')
  const [secretError, setSecretError] = useState('')

  useEffect(() => {
    if (open) {
      setTopicsLoading(true)
      fetchTopics({ limit: 100, offset: 0, status: 'ACTIVE' })
        .then((res) => {
          const names = (res.items ?? []).map((item) => item.name)
          setTopics(names)
          if (names.length > 0 && !topic) {
            setTopic(names[0])
          }
        })
        .catch(() => setTopics([]))
        .finally(() => setTopicsLoading(false))
    }
  }, [open])

  const handleGenerateSecret = (): void => {
    setSharedSecret(generateRandomHexSecret())
    if (secretError) setSecretError('')
  }

  const handleSubmit = (event: React.FormEvent): void => {
    event.preventDefault()

    let hasError = false

    if (!topic.trim()) {
      setTopicError(t('subscriptions.dialog.topicRequired'))
      hasError = true
    } else {
      setTopicError('')
    }

    const trimmedPurposes = purposesInput
      .split(',')
      .map((p) => p.trim())
      .filter(Boolean)

    if (filterMode !== 'all' && trimmedPurposes.length === 0) {
      setPurposesError(t('subscriptions.dialog.purposesRequired'))
      hasError = true
    } else {
      setPurposesError('')
    }

    if (deliveryMode === 'webhook') {
      const trimmedUrl = callbackUrl.trim()
      if (!trimmedUrl) {
        setCallbackUrlError(t('subscriptions.dialog.callbackUrlRequired'))
        hasError = true
      } else if (!/^https?:\/\/.+/i.test(trimmedUrl)) {
        setCallbackUrlError(t('subscriptions.dialog.callbackUrlInvalid'))
        hasError = true
      } else {
        setCallbackUrlError('')
      }
    } else {
      setCallbackUrlError('')
    }

    if (!sharedSecret.trim()) {
      setSecretError(t('subscriptions.dialog.secretRequired'))
      hasError = true
    } else {
      setSecretError('')
    }

    if (hasError) return

    onSubmit({
      topic: topic.trim(),
      groupId: groupId.trim() || undefined,
      filter: {
        type: filterMode,
        purposes: filterMode !== 'all' ? trimmedPurposes : undefined,
      },
      delivery: {
        mode: deliveryMode,
        callbackUrl: deliveryMode === 'webhook' ? callbackUrl.trim() : undefined,
        sharedSecret: sharedSecret.trim(),
      },
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
            {t('subscriptions.dialog.registerTitle')}
          </Typography>
          <Typography variant="body2" color="text.secondary">
            {t('subscriptions.dialog.registerSubtitle')}
          </Typography>
        </Stack>
      </DialogTitle>

      <form onSubmit={handleSubmit}>
        <DialogContent sx={{ px: 3, py: 3 }}>
          <Stack spacing={2.5}>
            {error ? <Alert severity="error">{error}</Alert> : null}

            <FormControl fullWidth size="small" required error={Boolean(topicError)}>
              <InputLabel id="topic-select-label">
                {t('subscriptions.dialog.topicLabel')}
              </InputLabel>
              <Select
                labelId="topic-select-label"
                label={t('subscriptions.dialog.topicLabel')}
                value={topic}
                disabled={topicsLoading}
                onChange={(e) => {
                  setTopic(e.target.value)
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
              label={t('subscriptions.dialog.groupIdLabel')}
              placeholder={t('subscriptions.dialog.groupIdPlaceholder')}
              value={groupId}
              onChange={(e) => setGroupId(e.target.value)}
              helperText={t('subscriptions.dialog.groupIdHelper')}
            />

            <FormControl fullWidth size="small">
              <InputLabel id="filter-mode-label">
                {t('subscriptions.dialog.filterModeLabel')}
              </InputLabel>
              <Select
                labelId="filter-mode-label"
                label={t('subscriptions.dialog.filterModeLabel')}
                value={filterMode}
                onChange={(e) => setFilterMode(e.target.value as PurposeFilterMode)}
              >
                {PURPOSE_FILTER_MODES.map((mode) => (
                  <MenuItem key={mode} value={mode}>
                    {t(`subscriptions.filterType.${mode}`, mode)}
                  </MenuItem>
                ))}
              </Select>
            </FormControl>

            {filterMode !== 'all' ? (
              <TextField
                required
                fullWidth
                size="small"
                label={t('subscriptions.dialog.purposesLabel')}
                placeholder={t('subscriptions.dialog.purposesPlaceholder')}
                value={purposesInput}
                error={Boolean(purposesError)}
                helperText={purposesError || t('subscriptions.dialog.purposesHelper')}
                onChange={(e) => {
                  setPurposesInput(e.target.value)
                  if (purposesError) setPurposesError('')
                }}
              />
            ) : null}

            <FormControl fullWidth size="small">
              <InputLabel id="delivery-mode-label">
                {t('subscriptions.dialog.deliveryModeLabel')}
              </InputLabel>
              <Select
                labelId="delivery-mode-label"
                label={t('subscriptions.dialog.deliveryModeLabel')}
                value={deliveryMode}
                onChange={(e) => setDeliveryMode(e.target.value as DeliveryMode)}
              >
                {DELIVERY_MODES.map((mode) => (
                  <MenuItem key={mode} value={mode}>
                    {t(`subscriptions.deliveryMode.${mode}`, mode)}
                  </MenuItem>
                ))}
              </Select>
            </FormControl>

            {deliveryMode === 'webhook' ? (
              <TextField
                required
                fullWidth
                size="small"
                label={t('subscriptions.dialog.callbackUrlLabel')}
                placeholder="https://example.com/webhook"
                value={callbackUrl}
                error={Boolean(callbackUrlError)}
                helperText={callbackUrlError}
                onChange={(e) => {
                  setCallbackUrl(e.target.value)
                  if (callbackUrlError) setCallbackUrlError('')
                }}
              />
            ) : null}

            <TextField
              required
              fullWidth
              size="small"
              label={t('subscriptions.dialog.secretLabel')}
              value={sharedSecret}
              error={Boolean(secretError)}
              helperText={secretError || t('subscriptions.dialog.secretHelper')}
              onChange={(e) => {
                setSharedSecret(e.target.value)
                if (secretError) setSecretError('')
              }}
              InputProps={{
                startAdornment: (
                  <InputAdornment position="start">
                    <Key size={16} />
                  </InputAdornment>
                ),
                endAdornment: (
                  <InputAdornment position="end">
                    <IconButton
                      size="small"
                      title={t('subscriptions.dialog.generateSecret')}
                      onClick={handleGenerateSecret}
                      edge="end"
                    >
                      <RefreshCw size={16} />
                    </IconButton>
                  </InputAdornment>
                ),
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
            startIcon={loading ? <CircularProgress size={16} color="inherit" /> : <Plus size={16} />}
            disabled={loading || topicsLoading}
          >
            {loading
              ? t('subscriptions.dialog.registering')
              : t('subscriptions.dialog.registerSubmit')}
          </Button>
          <Button fullWidth variant="outlined" disabled={loading} onClick={onClose}>
            {t('consentRegistry.modals.actions.cancel')}
          </Button>
        </DialogActions>
      </form>
    </Dialog>
  )
}
