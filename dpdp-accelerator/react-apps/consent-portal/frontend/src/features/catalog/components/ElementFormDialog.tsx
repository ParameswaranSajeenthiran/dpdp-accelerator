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
  IconButton,
  Stack,
  TextField,
  Typography,
} from '@wso2/oxygen-ui'
import { Plus, X } from '@wso2/oxygen-ui-icons-react'
import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import type { ElementInput } from '../../../types/catalog'

interface PropertyRow {
  key: string
  value: string
}

interface ElementFormDialogProps {
  open: boolean
  loading: boolean
  error?: string
  onClose: () => void
  onSubmit: (payload: ElementInput) => void
}

interface PropertyRowIssues {
  duplicateKey: boolean
  orphanedValue: boolean
}

const EMPTY_ROW: PropertyRow = { key: '', value: '' }

function toProperties(rows: PropertyRow[]): Record<string, string> | undefined {
  const entries = rows
    .map((row) => [row.key.trim(), row.value] as const)
    .filter(([key]) => key.length > 0)

  return entries.length > 0 ? Object.fromEntries(entries) : undefined
}

/** Flags rows that would otherwise be silently dropped or overwritten on submit. */
function getPropertyRowIssues(rows: PropertyRow[]): PropertyRowIssues[] {
  return rows.map((row) => {
    const trimmedKey = row.key.trim()
    const duplicateKey =
      trimmedKey.length > 0 && rows.filter((other) => other.key.trim() === trimmedKey).length > 1
    const orphanedValue = trimmedKey.length === 0 && row.value.trim().length > 0
    return { duplicateKey, orphanedValue }
  })
}

function ElementFormDialog({
  open,
  loading,
  error,
  onClose,
  onSubmit,
}: ElementFormDialogProps): React.JSX.Element {
  const { t } = useTranslation('common')
  const [name, setName] = useState('')
  const [displayName, setDisplayName] = useState('')
  const [description, setDescription] = useState('')
  const [properties, setProperties] = useState<PropertyRow[]>([])
  const [nameTouched, setNameTouched] = useState(false)
  const nameError = nameTouched && !name.trim()

  // Reset the form fields when the dialog transitions to open, adjusted
  // during render rather than in an effect (React docs: "Adjusting some
  // state when a prop changes").
  const [wasOpen, setWasOpen] = useState(open)
  if (open !== wasOpen) {
    setWasOpen(open)
    if (open) {
      setName('')
      setDisplayName('')
      setDescription('')
      setProperties([])
      setNameTouched(false)
    }
  }

  const propertyIssues = getPropertyRowIssues(properties)
  const hasPropertyErrors = propertyIssues.some(
    (issue) => issue.duplicateKey || issue.orphanedValue,
  )

  const updateProperty = (index: number, next: Partial<PropertyRow>): void => {
    setProperties((rows) => rows.map((row, i) => (i === index ? { ...row, ...next } : row)))
  }

  const removeProperty = (index: number): void => {
    setProperties((rows) => rows.filter((_, i) => i !== index))
  }

  const handleSubmit = (): void => {
    setNameTouched(true)
    if (!name.trim() || hasPropertyErrors) {
      return
    }
    onSubmit({
      name: name.trim(),
      displayName: displayName.trim() || undefined,
      description: description.trim() || undefined,
      properties: toProperties(properties),
    })
  }

  return (
    <Dialog open={open} onClose={loading ? undefined : onClose} maxWidth="sm" fullWidth>
      <DialogTitle sx={{ p: 3, borderBottom: 1, borderColor: 'divider' }}>
        {t('catalog.elementForm.title')}
      </DialogTitle>

      <DialogContent sx={{ p: 3 }}>
        <Stack spacing={2.5} sx={{ mt: 1 }}>
          {error ? <Alert severity="error">{error}</Alert> : null}

          <TextField
            autoFocus
            required
            fullWidth
            label={t('catalog.elementForm.nameLabel')}
            helperText={
              nameError ? t('catalog.elementForm.nameRequired') : t('catalog.elementForm.nameHelp')
            }
            error={nameError}
            value={name}
            disabled={loading}
            onChange={(event) => setName(event.target.value)}
            onBlur={() => setNameTouched(true)}
          />

          <TextField
            fullWidth
            label={t('catalog.elementForm.displayNameLabel')}
            value={displayName}
            disabled={loading}
            onChange={(event) => setDisplayName(event.target.value)}
          />

          <TextField
            fullWidth
            multiline
            minRows={2}
            label={t('catalog.elementForm.descriptionLabel')}
            value={description}
            disabled={loading}
            onChange={(event) => setDescription(event.target.value)}
          />

          <Stack spacing={1.5}>
            <Typography variant="subtitle2" fontWeight={600}>
              {t('catalog.elementForm.propertiesLabel')}
            </Typography>

            {properties.map((row, index) => {
              const { duplicateKey, orphanedValue } = propertyIssues[index]
              let keyHelperText: string | undefined
              if (duplicateKey) {
                keyHelperText = t('catalog.elementForm.propertyDuplicateKey')
              } else if (orphanedValue) {
                keyHelperText = t('catalog.elementForm.propertyKeyRequired')
              }

              return (
                // eslint-disable-next-line react/no-array-index-key -- rows have no stable id until saved
                <Stack key={index} direction="row" spacing={1} alignItems="flex-start">
                  <TextField
                    size="small"
                    fullWidth
                    label={t('catalog.elementForm.propertyKeyLabel')}
                    error={duplicateKey || orphanedValue}
                    helperText={keyHelperText}
                    value={row.key}
                    disabled={loading}
                    onChange={(event) => updateProperty(index, { key: event.target.value })}
                  />
                  <TextField
                    size="small"
                    fullWidth
                    label={t('catalog.elementForm.propertyValueLabel')}
                    value={row.value}
                    disabled={loading}
                    onChange={(event) => updateProperty(index, { value: event.target.value })}
                  />
                  <IconButton
                    size="small"
                    disabled={loading}
                    aria-label={t('catalog.elementForm.removeProperty')}
                    onClick={() => removeProperty(index)}
                  >
                    <X size={16} />
                  </IconButton>
                </Stack>
              )
            })}

            <Button
              size="small"
              variant="outlined"
              startIcon={<Plus size={16} />}
              disabled={loading}
              sx={{ alignSelf: 'flex-start' }}
              onClick={() => setProperties((rows) => [...rows, { ...EMPTY_ROW }])}
            >
              {t('catalog.elementForm.addProperty')}
            </Button>
          </Stack>
        </Stack>
      </DialogContent>

      <DialogActions sx={{ p: 3, pt: 2, borderTop: 1, borderColor: 'divider' }}>
        <Button disabled={loading} onClick={onClose}>
          {t('catalog.actions.cancel')}
        </Button>
        <Button variant="contained" disabled={loading || hasPropertyErrors} onClick={handleSubmit}>
          {loading ? t('catalog.elementForm.submitting') : t('catalog.actions.create')}
        </Button>
      </DialogActions>
    </Dialog>
  )
}

ElementFormDialog.defaultProps = {
  error: undefined,
}

export default ElementFormDialog
