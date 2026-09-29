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
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  Stack,
  Typography,
} from '@wso2/oxygen-ui'
import { useTranslation } from 'react-i18next'
import type { SubscriptionRecord } from '../../../types/subscription'

interface SubscriptionDeleteDialogProps {
  open: boolean
  subscription: SubscriptionRecord
  loading: boolean
  error?: string
  onClose: () => void
  onConfirm: () => void
}

export default function SubscriptionDeleteDialog({
  open,
  subscription,
  loading,
  error,
  onClose,
  onConfirm,
}: SubscriptionDeleteDialogProps): React.JSX.Element {
  const { t } = useTranslation('common')

  const rawMessage = t('subscriptions.deleteModal.message', { id: subscription.subscriptionId })
  const [prefix, ...rest] = rawMessage.split(subscription.subscriptionId)
  const suffix = rest.join(subscription.subscriptionId)
  const message =
    rest.length > 0 ? (
      <>
        {prefix}
        <Box component="span" sx={{ fontWeight: 700, color: 'text.primary' }}>
          {subscription.subscriptionId}
        </Box>
        {suffix}
      </>
    ) : (
      rawMessage
    )

  return (
    <Dialog
      open={open}
      onClose={loading ? undefined : onClose}
      maxWidth="xs"
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
            {t('subscriptions.deleteModal.title')}
          </Typography>
          <Typography variant="body2" color="text.secondary">
            {message}
          </Typography>
        </Stack>
      </DialogTitle>

      <DialogContent sx={{ px: 3, pt: 3.5, pb: 3 }}>
        <Stack spacing={2} sx={{ mt: 3 }}>
          {error ? <Alert severity="error">{error}</Alert> : null}
          <Box
            sx={{
              width: '100%',
              p: 2,
              border: 1,
              borderColor: 'error.light',
              borderRadius: 1,
              bgcolor: 'error.lighter',
            }}
          >
            <Typography variant="body2" color="text.secondary">
              {t('subscriptions.deleteModal.note')}
            </Typography>
          </Box>
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
          color="error"
          variant="contained"
          disabled={loading}
          onClick={() => onConfirm()}
        >
          {loading
            ? t('consentRegistry.modals.actions.processing')
            : t('subscriptions.deleteModal.confirm')}
        </Button>
        <Button fullWidth variant="outlined" disabled={loading} onClick={onClose}>
          {t('consentRegistry.modals.actions.cancel')}
        </Button>
      </DialogActions>
    </Dialog>
  )
}

SubscriptionDeleteDialog.defaultProps = {
  error: undefined,
}
