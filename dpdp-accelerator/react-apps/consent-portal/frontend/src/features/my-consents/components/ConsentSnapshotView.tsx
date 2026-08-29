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
  Accordion,
  AccordionDetails,
  AccordionSummary,
  Box,
  Chip,
  Divider,
  Stack,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableRow,
  Typography,
} from '@wso2/oxygen-ui'
import { ChevronRight } from '@wso2/oxygen-ui-icons-react'
import { useTranslation } from 'react-i18next'
import type {
  AnnotatedChangeKind,
  AnnotatedElement,
  AnnotatedField,
  AnnotatedProperty,
  AnnotatedPurpose,
  AnnotatedSnapshot,
} from '../../../utils/consentSnapshotDiff'
import { formatEpochTimestamp } from '../../../utils/dateTime'
import { getConsentStateChipColor, getConsentStateLabelKey } from '../utils/statusChip'

interface ConsentSnapshotViewProps {
  snapshot: AnnotatedSnapshot
}

/** Text color for a changed row - the same tag word doubles as the accessible label. */
function changeColor(kind: AnnotatedChangeKind): string | undefined {
  if (kind === 'added') {
    return 'success.main'
  }
  if (kind === 'removed') {
    return 'error.main'
  }
  if (kind === 'changed') {
    return 'warning.main'
  }
  return undefined
}

function ChangeTag({ kind }: { kind: AnnotatedChangeKind }): React.JSX.Element | null {
  const { t } = useTranslation('common')

  if (kind === 'unchanged') {
    return null
  }

  return (
    <Typography variant="caption" sx={{ color: changeColor(kind), whiteSpace: 'nowrap' }}>
      {t(`consentRegistry.history.snapshot.${kind}`)}
    </Typography>
  )
}

function FieldsSummary({ fields }: { fields: AnnotatedField[] }): React.JSX.Element | null {
  const { t } = useTranslation('common')

  if (fields.length === 0) {
    return null
  }

  return (
    <Stack direction="row" spacing={4} flexWrap="wrap" useFlexGap>
      {fields.map((field) => (
        <Box key={field.field}>
          <Typography
            variant="caption"
            color="text.disabled"
            sx={{ textTransform: 'uppercase', letterSpacing: 0.4, display: 'block' }}
          >
            {t(`consentRegistry.history.snapshot.field.${field.field}`)}
          </Typography>
          {field.field === 'state' ? (
            <Stack direction="row" alignItems="center" spacing={0.75} sx={{ mt: 0.25 }}>
              {field.kind === 'changed' && field.before !== undefined ? (
                <>
                  <Chip
                    size="small"
                    variant="outlined"
                    color={getConsentStateChipColor(String(field.before))}
                    label={t(
                      `consentRegistry.status.${getConsentStateLabelKey(String(field.before))}`,
                    )}
                  />
                  <Typography variant="body2" color="text.disabled">
                    →
                  </Typography>
                </>
              ) : null}
              {field.value !== undefined ? (
                <Chip
                  size="small"
                  variant="outlined"
                  color={getConsentStateChipColor(String(field.value))}
                  label={t(
                    `consentRegistry.status.${getConsentStateLabelKey(String(field.value))}`,
                  )}
                />
              ) : null}
            </Stack>
          ) : (
            <Typography variant="body2" fontWeight={500}>
              {field.kind === 'changed'
                ? `${field.before ?? '-'} → ${field.value ?? '-'}`
                : field.value}
            </Typography>
          )}
        </Box>
      ))}
    </Stack>
  )
}

function PropertiesTable({ properties }: { properties: AnnotatedProperty[] }): React.JSX.Element {
  const { t } = useTranslation('common')

  if (properties.length === 0) {
    return (
      <Typography variant="body2" color="text.secondary">
        {t('consentRegistry.details.noProperties')}
      </Typography>
    )
  }

  return (
    <TableContainer>
      <Table size="small">
        <TableHead>
          <TableRow>
            <TableCell sx={{ fontWeight: 700, width: '35%' }}>
              {t('catalog.fields.propertyKey')}
            </TableCell>
            <TableCell sx={{ fontWeight: 700 }}>{t('catalog.fields.propertyValue')}</TableCell>
          </TableRow>
        </TableHead>
        <TableBody>
          {properties.map((property) => (
            <TableRow key={property.key} hover>
              <TableCell>
                <Box
                  component="code"
                  sx={{
                    color: changeColor(property.kind),
                    textDecoration: property.kind === 'removed' ? 'line-through' : 'none',
                  }}
                >
                  {property.key}
                </Box>
              </TableCell>
              <TableCell sx={{ overflowWrap: 'anywhere' }}>
                <Stack direction="row" alignItems="center" spacing={1}>
                  <Typography
                    variant="body2"
                    sx={{
                      color: property.kind === 'removed' ? 'text.secondary' : 'text.primary',
                      textDecoration: property.kind === 'removed' ? 'line-through' : 'none',
                    }}
                  >
                    {property.kind === 'changed'
                      ? `${property.before ?? '-'} → ${property.value ?? '-'}`
                      : (property.value ?? property.before)}
                  </Typography>
                  <ChangeTag kind={property.kind} />
                </Stack>
              </TableCell>
            </TableRow>
          ))}
        </TableBody>
      </Table>
    </TableContainer>
  )
}

function elementConsentedLabel(
  element: AnnotatedElement,
  t: (key: string) => string,
): React.ReactNode {
  const yesNo = (value: boolean): string =>
    value ? t('consentRegistry.details.values.yes') : t('consentRegistry.details.values.no')

  if (element.kind === 'changed' && element.consentedBefore !== undefined) {
    return `${yesNo(element.consentedBefore)} → ${yesNo(element.consented)}`
  }
  return yesNo(element.consented)
}

function PurposesList({ purposes }: { purposes: AnnotatedPurpose[] }): React.JSX.Element {
  const { t } = useTranslation('common')

  if (purposes.length === 0) {
    return (
      <Typography variant="body2" color="text.secondary">
        {t('consentRegistry.details.noPurposes')}
      </Typography>
    )
  }

  return (
    <Stack spacing={1}>
      {purposes.map((purpose) => (
        <Accordion
          key={`${purpose.service}::${purpose.purpose}`}
          disableGutters
          elevation={0}
          sx={{
            border: 1,
            borderColor: 'divider',
            borderRadius: 1,
            overflow: 'hidden',
            '&:before': { display: 'none' },
          }}
        >
          <AccordionSummary
            expandIcon={<ChevronRight />}
            sx={{ '&:hover': { bgcolor: 'action.hover' } }}
          >
            <Stack direction="row" spacing={1.5} alignItems="center" flexWrap="wrap" useFlexGap>
              <Typography
                variant="body2"
                fontWeight={600}
                sx={{
                  color: purpose.kind === 'removed' ? 'text.secondary' : 'text.primary',
                  textDecoration: purpose.kind === 'removed' ? 'line-through' : 'none',
                }}
              >
                {purpose.purpose}
              </Typography>
              <Chip size="small" variant="outlined" label={purpose.service} />
              <ChangeTag kind={purpose.kind} />
            </Stack>
          </AccordionSummary>
          <AccordionDetails sx={{ p: 0 }}>
            <TableContainer>
              <Table size="small" sx={{ tableLayout: 'fixed' }}>
                <TableHead>
                  <TableRow>
                    <TableCell sx={{ fontWeight: 700, width: '35%' }}>
                      {t('consentRegistry.details.table.element')}
                    </TableCell>
                    <TableCell sx={{ fontWeight: 700, width: '35%' }}>
                      {t('catalog.fields.displayName')}
                    </TableCell>
                    <TableCell sx={{ fontWeight: 700, width: '30%' }}>
                      {t('consentRegistry.history.snapshot.consented')}
                    </TableCell>
                  </TableRow>
                </TableHead>
                <TableBody>
                  {purpose.elements.length === 0 ? (
                    <TableRow>
                      <TableCell colSpan={3}>
                        <Typography variant="body2" color="text.secondary" align="center">
                          {t('consentRegistry.details.noElements')}
                        </Typography>
                      </TableCell>
                    </TableRow>
                  ) : null}
                  {purpose.elements.map((element) => (
                    <TableRow key={element.name} hover>
                      <TableCell>
                        <Box
                          component="code"
                          sx={{
                            color: changeColor(element.kind),
                            textDecoration: element.kind === 'removed' ? 'line-through' : 'none',
                          }}
                        >
                          {element.name}
                        </Box>
                      </TableCell>
                      <TableCell
                        sx={{
                          color: element.kind === 'removed' ? 'text.secondary' : 'text.primary',
                          textDecoration: element.kind === 'removed' ? 'line-through' : 'none',
                        }}
                      >
                        {element.displayName}
                      </TableCell>
                      <TableCell>
                        <Stack direction="row" alignItems="center" spacing={1}>
                          <Typography variant="body2">
                            {elementConsentedLabel(element, t)}
                          </Typography>
                          <ChangeTag kind={element.kind} />
                        </Stack>
                      </TableCell>
                    </TableRow>
                  ))}
                </TableBody>
              </Table>
            </TableContainer>
          </AccordionDetails>
        </Accordion>
      ))}
    </Stack>
  )
}

function AuthorizationsTable({
  authorizations,
}: {
  authorizations: AnnotatedSnapshot['authorizations']
}): React.JSX.Element {
  const { t } = useTranslation('common')

  if (authorizations.length === 0) {
    return (
      <Typography variant="body2" color="text.secondary">
        {t('consentRegistry.details.noAuthorizations')}
      </Typography>
    )
  }

  return (
    <TableContainer>
      <Table sx={{ '& tbody tr:hover': { bgcolor: 'action.hover' } }}>
        <TableHead>
          <TableRow sx={{ bgcolor: 'action.default' }}>
            <TableCell sx={{ fontWeight: 700 }}>
              {t('consentRegistry.details.table.user')}
            </TableCell>
            <TableCell sx={{ fontWeight: 700 }}>
              {t('consentRegistry.details.table.state')}
            </TableCell>
            <TableCell sx={{ fontWeight: 700 }}>
              {t('consentRegistry.details.table.updated')}
            </TableCell>
          </TableRow>
        </TableHead>
        <TableBody>
          {authorizations.map((authorization) => (
            <TableRow key={authorization.userId}>
              <TableCell>
                <Stack direction="row" alignItems="center" spacing={1}>
                  <Typography
                    variant="body2"
                    sx={{
                      color: authorization.kind === 'removed' ? 'text.secondary' : 'text.primary',
                      textDecoration: authorization.kind === 'removed' ? 'line-through' : 'none',
                    }}
                  >
                    {authorization.userId}
                  </Typography>
                  <ChangeTag kind={authorization.kind} />
                </Stack>
              </TableCell>
              <TableCell>
                <Stack direction="row" alignItems="center" spacing={0.75}>
                  {authorization.kind === 'changed' && authorization.before ? (
                    <>
                      <Chip
                        size="small"
                        variant="outlined"
                        color={getConsentStateChipColor(authorization.before.status)}
                        label={t(
                          `consentRegistry.status.${getConsentStateLabelKey(authorization.before.status, 'authorization')}`,
                        )}
                      />
                      <Typography variant="body2" color="text.disabled">
                        →
                      </Typography>
                    </>
                  ) : null}
                  <Chip
                    size="small"
                    variant="outlined"
                    color={getConsentStateChipColor(authorization.status)}
                    label={t(
                      `consentRegistry.status.${getConsentStateLabelKey(authorization.status, 'authorization')}`,
                    )}
                  />
                </Stack>
              </TableCell>
              <TableCell>
                <Typography variant="body2">
                  {formatEpochTimestamp(authorization.updatedTime)}
                </Typography>
              </TableCell>
            </TableRow>
          ))}
        </TableBody>
      </Table>
    </TableContainer>
  )
}

function SnapshotSection({
  title,
  children,
}: {
  title: string
  children: React.ReactNode
}): React.JSX.Element {
  return (
    <Box sx={{ border: 1, borderColor: 'divider', borderRadius: 1, overflow: 'hidden' }}>
      <Typography variant="subtitle2" fontWeight={700} sx={{ px: 2, py: 1.25 }}>
        {title}
      </Typography>
      <Divider />
      {children}
    </Box>
  )
}

function ConsentSnapshotView({ snapshot }: ConsentSnapshotViewProps): React.JSX.Element {
  const { t } = useTranslation('common')

  return (
    <Stack spacing={2}>
      <FieldsSummary fields={snapshot.fields} />

      <SnapshotSection title={t('catalog.fields.properties')}>
        <Box sx={{ p: snapshot.properties.length === 0 ? 2 : 0 }}>
          <PropertiesTable properties={snapshot.properties} />
        </Box>
      </SnapshotSection>

      <SnapshotSection title={t('consentRegistry.details.section.purposes')}>
        <Box sx={{ p: snapshot.purposes.length === 0 ? 2 : 1.5 }}>
          <PurposesList purposes={snapshot.purposes} />
        </Box>
      </SnapshotSection>

      <SnapshotSection title={t('consentRegistry.details.section.authorizations')}>
        <Box sx={{ p: snapshot.authorizations.length === 0 ? 2 : 0 }}>
          <AuthorizationsTable authorizations={snapshot.authorizations} />
        </Box>
      </SnapshotSection>
    </Stack>
  )
}

export default ConsentSnapshotView
