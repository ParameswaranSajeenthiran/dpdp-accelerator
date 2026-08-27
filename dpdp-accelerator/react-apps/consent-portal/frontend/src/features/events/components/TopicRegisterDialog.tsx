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
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  Stack,
  TextField,
  Typography,
} from '@wso2/oxygen-ui'
import { Plus } from '@wso2/oxygen-ui-icons-react'
import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import type { TopicInput } from '../../../types/topic'

interface TopicRegisterDialogProps {
  open: boolean
  loading: boolean
  error?: string
  onClose: () => void
  onSubmit: (payload: TopicInput) => void
}

export default function TopicRegisterDialog({
  open,
  loading,
  error,
  onClose,
  onSubmit,
}: TopicRegisterDialogProps): React.JSX.Element {
  const { t } = useTranslation('common')
  const [name, setName] = useState('')
  const [description, setDescription] = useState('')
  const [nameError, setNameError] = useState('')

  const handleSubmit = (event: React.FormEvent): void => {
    event.preventDefault()

    const trimmedName = name.trim()
    if (!trimmedName) {
      setNameError(t('topics.dialog.nameRequired'))
      return
    }

    setNameError('')
    onSubmit({
      name: trimmedName,
      description: description.trim() || undefined,
    })
  }

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
            {t('topics.dialog.registerTitle')}
          </Typography>
          <Typography variant="body2" color="text.secondary">
            {t('topics.dialog.registerSubtitle')}
          </Typography>
        </Stack>
      </DialogTitle>

      <form onSubmit={handleSubmit}>
        <DialogContent sx={{ px: 3, py: 3 }}>
          <Stack spacing={2.5}>
            {error ? <Alert severity="error">{error}</Alert> : null}
            <TextField
              required
              fullWidth
              size="small"
              label={t('topics.dialog.nameLabel')}
              placeholder={t('topics.dialog.namePlaceholder')}
              value={name}
              error={Boolean(nameError)}
              helperText={nameError}
              onChange={(event) => {
                setName(event.target.value)
                if (nameError) setNameError('')
              }}
            />
            <TextField
              fullWidth
              multiline
              rows={3}
              size="small"
              label={t('topics.dialog.descriptionLabel')}
              placeholder={t('topics.dialog.descriptionPlaceholder')}
              value={description}
              onChange={(event) => setDescription(event.target.value)}
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
            startIcon={<Plus size={16} />}
            disabled={loading}
          >
            {loading ? t('topics.dialog.registering') : t('topics.dialog.registerSubmit')}
          </Button>
          <Button fullWidth variant="outlined" disabled={loading} onClick={onClose}>
            {t('consentRegistry.modals.actions.cancel')}
          </Button>
        </DialogActions>
      </form>
    </Dialog>
  )
}
