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
  CircularProgress,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  FormControl,
  IconButton,
  InputAdornment,
  InputLabel,
  MenuItem,
  Select,
  Stack,
  Stepper,
  Step,
  StepLabel,
  TextField,
  Typography,
} from '@wso2/oxygen-ui'
import { Key, ArrowLeft, ArrowRight, X, RefreshCw } from '@wso2/oxygen-ui-icons-react'
import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import type {
  DeliveryMode,
  PurposeFilterMode,
  SubscriptionInput,
} from '../../../types/subscription'
import { DELIVERY_MODES, PURPOSE_FILTER_MODES } from '../../../types/subscription'
import SubscriptionTopicPicker from './SubscriptionTopicPicker'
import { supportsConsentPurposeFilter } from '../utils/topicCapabilities'
import { MAX_SUBSCRIPTION_TOPICS } from '../constants'

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

  const [step, setStep] = useState(0)
  const [selectionBusy, setSelectionBusy] = useState(false)

  const [selectedTopics, setSelectedTopics] = useState<string[]>([])
  const [filterMode, setFilterMode] = useState<PurposeFilterMode>('all')
  const [purposesInput, setPurposesInput] = useState('')
  const [deliveryMode, setDeliveryMode] = useState<DeliveryMode>('webhook')
  const [callbackUrl, setCallbackUrl] = useState('')
  const [sharedSecret, setSharedSecret] = useState(() => generateRandomHexSecret())

  const [topicError, setTopicError] = useState('')
  const [callbackUrlError, setCallbackUrlError] = useState('')
  const [purposesError, setPurposesError] = useState('')
  const [secretError, setSecretError] = useState('')

  const handleGenerateSecret = (): void => {
    setSharedSecret(generateRandomHexSecret())
    if (secretError) setSecretError('')
  }

  const handleSubmit = (event: React.FormEvent): void => {
    event.preventDefault()
    if (selectionBusy || loading) return
    if (step === 0) {
      if (selectedTopics.length > 0 && selectedTopics.length <= MAX_SUBSCRIPTION_TOPICS) setStep(1)
      return
    }

    let hasError = false

    if (selectedTopics.length === 0 || selectedTopics.length > MAX_SUBSCRIPTION_TOPICS) {
      setTopicError(t('subscriptions.dialog.topicRequired'))
      hasError = true
    } else {
      setTopicError('')
    }

    const trimmedPurposes = purposesInput
      .split(',')
      .map((p) => p.trim())
      .filter(Boolean)

    const supportsPurposeFilter = selectedTopics.every(supportsConsentPurposeFilter)
    const effectiveFilterMode: PurposeFilterMode = filterMode
    if (!supportsPurposeFilter && filterMode !== 'all') {
      setPurposesError(t('subscriptions.dialog.multiTopicFilter'))
      hasError = true
    }

    if (effectiveFilterMode !== 'all' && trimmedPurposes.length === 0) {
      setPurposesError(t('subscriptions.dialog.purposesRequired'))
      hasError = true
    } else if (supportsPurposeFilter || filterMode === 'all') {
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
      topics: selectedTopics,
      filter: {
        type: effectiveFilterMode,
        purposes: effectiveFilterMode !== 'all' ? trimmedPurposes : undefined,
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
      onClose={loading || selectionBusy ? undefined : onClose}
      maxWidth="sm"
      fullWidth
      PaperProps={{
        sx: (theme) => ({
          borderRadius: 1,
          height: 'min(760px, calc(100dvh - 64px))',
          overflow: 'hidden',
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
          flexShrink: 0,
          position: 'relative',
        }}
      >
        <IconButton
          aria-label={t('subscriptions.topicUi.close')}
          disabled={loading || selectionBusy}
          onClick={onClose}
          sx={{ position: 'absolute', top: 1, right: 1 }}
        >
          <X size={20} />
        </IconButton>
        <Stack spacing={0.75}>
          <Typography variant="h6" fontWeight={700}>
            {t('subscriptions.dialog.registerTitle')}
          </Typography>
          <Typography variant="body2" color="text.secondary">
            {t(
              step === 0
                ? 'subscriptions.topicUi.chooseTopics'
                : 'subscriptions.topicUi.configureDelivery',
            )}
          </Typography>
          <Stepper activeStep={step} sx={{ pt: 2 }}>
            <Step>
              <StepLabel>{t('subscriptions.topicUi.topics')}</StepLabel>
            </Step>
            <Step>
              <StepLabel>{t('subscriptions.topicUi.delivery')}</StepLabel>
            </Step>
          </Stepper>
        </Stack>
      </DialogTitle>

      <Box
        component="form"
        onSubmit={handleSubmit}
        sx={{ display: 'flex', flexDirection: 'column', flex: 1, minHeight: 0 }}
      >
        <DialogContent sx={{ px: 3, py: 3, flex: 1, minHeight: 0, overflowY: 'auto' }}>
          <Stack spacing={2.5}>
            {error ? <Alert severity="error">{error}</Alert> : null}
            {step === 1 && purposesError ? <Alert severity="error">{purposesError}</Alert> : null}

            {topicError ? <Alert severity="error">{topicError}</Alert> : null}
            {step === 0 ? (
              <SubscriptionTopicPicker
                selected={selectedTopics}
                onBusyChange={setSelectionBusy}
                onChange={(values) => {
                  setSelectedTopics(values)
                  setTopicError('')
                }}
              />
            ) : (
              <>
                {selectedTopics.every(supportsConsentPurposeFilter) || filterMode !== 'all' ? (
                  <>
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
                  </>
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
              </>
            )}
          </Stack>
        </DialogContent>

        <DialogActions
          sx={{
            px: 3,
            py: 2,
            borderTop: 1,
            borderColor: 'divider',
            bgcolor: 'background.default',
            justifyContent: 'space-between',
            flexShrink: 0,
            gap: 2,
          }}
        >
          {step === 0 ? (
            <Button variant="outlined" disabled={selectionBusy} onClick={onClose}>
              {t('consentRegistry.modals.actions.cancel')}
            </Button>
          ) : (
            <Button
              variant="outlined"
              startIcon={<ArrowLeft size={16} />}
              disabled={loading}
              onClick={() => setStep(0)}
            >
              {t('subscriptions.topicUi.back')}
            </Button>
          )}
          <Button
            type="submit"
            variant="contained"
            disabled={loading || selectionBusy || selectedTopics.length === 0}
            endIcon={step === 0 ? <ArrowRight size={16} /> : undefined}
          >
            {loading && <CircularProgress size={16} color="inherit" sx={{ mr: 1 }} />}
            {step === 0 && t('subscriptions.topicUi.next')}
            {step === 1 && !loading && t('subscriptions.dialog.registerSubmit')}
            {step === 1 && loading && t('subscriptions.dialog.registering')}
          </Button>
        </DialogActions>
      </Box>
    </Dialog>
  )
}

SubscriptionRegisterDialog.defaultProps = { error: undefined }
