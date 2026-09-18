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
  Card,
  CardContent,
  CardHeader,
  Chip,
  Divider,
  Skeleton,
  Stack,
  StatCard,
  Typography,
} from '@wso2/oxygen-ui'
import {
  ArrowRight,
  Ban,
  Blocks,
  CheckCircle2,
  Clock3,
  Hourglass,
  Inbox,
  Layers,
  RefreshCw,
  ShieldCheck,
  Target,
  UserCheck,
  XCircle,
} from '@wso2/oxygen-ui-icons-react'
import { useMemo } from 'react'
import { useTranslation } from 'react-i18next'
import { Link as RouterLink, useNavigate } from 'react-router-dom'
import HeaderBreadcrumbs from '../../components/layout/main-layout/HeaderBreadcrumbs'
import useAuthorization from '../auth/useAuthorization'
import type { ConsentSummary } from '../../types/consent'
import { formatEpochTimestamp } from '../../utils/dateTime'
import { REQUIRED_SCOPES, isDpoOnlyProfile } from '../../utils/scopes'
import type { PageCount } from '../../utils/cursorPagination'
import useDashboardSelfConsentStateCountsQuery from './hooks/useDashboardSelfConsentStateCountsQuery'
import useDashboardTenantConsentStateCountsQuery from './hooks/useDashboardTenantConsentStateCountsQuery'
import type { ConsentStateCounts } from './hooks/consentStateCounts'
import useDashboardPurposesCountQuery, {
  useDashboardElementsCountQuery,
} from './hooks/useDashboardCatalogCountsQuery'
import useDashboardPendingConsentsQuery from './hooks/useDashboardPendingConsentsQuery'
import useDashboardMyComplaintCountsQuery, {
  type ComplaintStateCounts,
} from './hooks/useDashboardMyComplaintCountsQuery'

const ATTENTION_ITEM_LIMIT = 5

function summarizePurposes(consent: ConsentSummary): string {
  const labels = (consent.purposes ?? []).map((purpose) => purpose.name)

  if (labels.length === 0) return '-'
  if (labels.length === 1) return labels[0]
  return `${labels[0]} +${String(labels.length - 1)}`
}

function PendingConsentRow({ consent }: { consent: ConsentSummary }): React.JSX.Element {
  const navigate = useNavigate()
  const consentPath = `/consents/${encodeURIComponent(consent.id)}`

  return (
    <Box
      role="link"
      tabIndex={0}
      onClick={() => navigate(consentPath)}
      onKeyDown={(event) => {
        if (event.key === 'Enter' || event.key === ' ') {
          event.preventDefault()
          navigate(consentPath)
        }
      }}
      sx={{
        py: 1.25,
        px: 1,
        mx: -1,
        borderRadius: 1,
        cursor: 'pointer',
        '&:hover': { bgcolor: 'action.hover' },
      }}
    >
      <Stack direction="row" alignItems="center" justifyContent="space-between" spacing={2}>
        <Stack spacing={0.25} minWidth={0}>
          <Typography variant="body2" fontWeight={600} noWrap>
            {consent.serviceId}
          </Typography>
          <Typography variant="caption" color="text.secondary" noWrap>
            {summarizePurposes(consent)}
          </Typography>
        </Stack>
        <Stack direction="row" spacing={1} alignItems="center" flexShrink={0}>
          <Typography variant="caption" color="text.secondary">
            {formatEpochTimestamp(consent.timestamp)}
          </Typography>
          <ArrowRight size={16} />
        </Stack>
      </Stack>
    </Box>
  )
}

/** "42" when exact, "100+" when the count hit its cap - see PageCount. */
function formatPageCount(pageCount: PageCount | undefined): string {
  if (!pageCount) return '-'
  return pageCount.isAtLeast ? `${String(pageCount.count)}+` : String(pageCount.count)
}

/** "-" while loading, on error, or with no data yet - a real 0 only once the count is known. */
function formatComplaintCount(
  query: { isLoading: boolean; isError: boolean; data?: ComplaintStateCounts },
  select: (data: ComplaintStateCounts) => number,
): string {
  if (query.isLoading || query.isError || !query.data) return '-'
  return String(select(query.data))
}

interface ConsentStateCardsProps {
  counts: ConsentStateCounts | undefined
  isLoading: boolean
  t: (key: string) => string
}

function ConsentStateCards({ counts, isLoading, t }: ConsentStateCardsProps): React.JSX.Element {
  const value = (pageCount: PageCount | undefined): string =>
    isLoading ? '-' : formatPageCount(pageCount)

  return (
    <Box
      sx={{
        display: 'grid',
        gridTemplateColumns: { xs: '1fr 1fr', sm: 'repeat(3, 1fr)', md: 'repeat(6, 1fr)' },
        gap: 2,
      }}
    >
      <StatCard
        value={value(counts?.total)}
        label={t('dashboard.totalConsents')}
        icon={<Layers size={22} />}
        iconColor="primary"
      />
      <StatCard
        value={value(counts?.pending)}
        label={t('consentRegistry.status.pending')}
        icon={<Clock3 size={22} />}
        iconColor="warning"
      />
      <StatCard
        value={value(counts?.active)}
        label={t('consentRegistry.status.active')}
        icon={<ShieldCheck size={22} />}
        iconColor="success"
      />
      <StatCard
        value={value(counts?.rejected)}
        label={t('consentRegistry.status.rejected')}
        icon={<XCircle size={22} />}
        iconColor="error"
      />
      <StatCard
        value={value(counts?.revoked)}
        label={t('consentRegistry.status.revoked')}
        icon={<Ban size={22} />}
        iconColor="error"
      />
      <StatCard
        value={value(counts?.expired)}
        label={t('consentRegistry.status.expired')}
        icon={<Hourglass size={22} />}
        iconColor="secondary"
      />
    </Box>
  )
}

function DashboardPage(): React.JSX.Element {
  const { t } = useTranslation('common')
  const { hasScope } = useAuthorization()

  // Admin is assumed to have no consents of his own to report -- the tenant-wide view replaces
  // the self-service one entirely rather than sitting alongside it.
  const isTenantConsentView = hasScope(REQUIRED_SCOPES.CONSENTS_READ_ANY)
  const showMyComplaints = hasScope(REQUIRED_SCOPES.COMPLAINTS_READ_SELF)

  // A DPO's token also carries internal_login (see isDpoOnlyProfile), so without this check a
  // direct visit to /dashboard would render the self-consents view empty instead of hiding it.
  const showConsentSection = isTenantConsentView || !isDpoOnlyProfile(hasScope)
  const showSelfConsentDetail = showConsentSection && !isTenantConsentView

  const selfStateCountsQuery = useDashboardSelfConsentStateCountsQuery(showSelfConsentDetail)
  const tenantStateCountsQuery = useDashboardTenantConsentStateCountsQuery(isTenantConsentView)
  const stateCountsQuery = isTenantConsentView ? tenantStateCountsQuery : selfStateCountsQuery

  const showPurposesCount = isTenantConsentView && hasScope(REQUIRED_SCOPES.PURPOSES_READ)
  const showElementsCount = isTenantConsentView && hasScope(REQUIRED_SCOPES.ELEMENTS_READ)
  const purposesCountQuery = useDashboardPurposesCountQuery(showPurposesCount)
  const elementsCountQuery = useDashboardElementsCountQuery(showElementsCount)

  const pendingConsentsQuery = useDashboardPendingConsentsQuery(showSelfConsentDetail)
  const pendingConsents = useMemo(
    () =>
      [...(pendingConsentsQuery.data ?? [])]
        .sort((left, right) => right.timestamp - left.timestamp)
        .slice(0, ATTENTION_ITEM_LIMIT),
    [pendingConsentsQuery.data],
  )

  const complaintCountsQuery = useDashboardMyComplaintCountsQuery(showMyComplaints)

  const consentSectionError =
    showConsentSection &&
    (stateCountsQuery.isError ||
      (showSelfConsentDetail && pendingConsentsQuery.isError) ||
      (showPurposesCount && purposesCountQuery.isError) ||
      (showElementsCount && elementsCountQuery.isError))
  const complaintSectionError = showMyComplaints && complaintCountsQuery.isError

  return (
    <Box component="main" sx={{ p: { xs: 2, md: 4 } }}>
      <Stack spacing={3}>
        <Stack spacing={0.75}>
          <HeaderBreadcrumbs />
          <Typography variant="h4" fontWeight={700}>
            {t('dashboard.title')}
          </Typography>
          <Typography variant="body2" color="text.secondary">
            {t(isTenantConsentView ? 'dashboard.subtitleAdmin' : 'dashboard.subtitle')}
          </Typography>
        </Stack>

        {consentSectionError ? <Alert severity="error">{t('dashboard.loadFailed')}</Alert> : null}

        {showConsentSection ? (
          <>
            <Typography variant="h6" fontWeight={700}>
              {t('dashboard.consentsByStatus')}
            </Typography>
            <ConsentStateCards
              counts={stateCountsQuery.data}
              isLoading={stateCountsQuery.isLoading}
              t={t}
            />

            {showPurposesCount || showElementsCount ? (
              <>
                <Typography variant="h6" fontWeight={700}>
                  {t('sidebar.catalog')}
                </Typography>
                <Box
                  sx={{
                    display: 'grid',
                    gridTemplateColumns: { xs: '1fr', sm: 'repeat(2, 1fr)' },
                    gap: 2,
                  }}
                >
                  {showPurposesCount ? (
                    <StatCard
                      value={
                        purposesCountQuery.isLoading
                          ? '-'
                          : formatPageCount(purposesCountQuery.data)
                      }
                      label={t('sidebar.purposes')}
                      icon={<Target size={22} />}
                      iconColor="primary"
                    />
                  ) : null}
                  {showElementsCount ? (
                    <StatCard
                      value={
                        elementsCountQuery.isLoading
                          ? '-'
                          : formatPageCount(elementsCountQuery.data)
                      }
                      label={t('sidebar.elements')}
                      icon={<Blocks size={22} />}
                      iconColor="secondary"
                    />
                  ) : null}
                </Box>
              </>
            ) : null}

            {showSelfConsentDetail ? (
              <>
                <Typography variant="h6" fontWeight={700}>
                  {t('dashboard.attention')}
                </Typography>

                <Card sx={{ boxShadow: 1 }}>
                  <CardHeader
                    avatar={<Clock3 size={20} />}
                    title={
                      <Typography fontWeight={600}>{t('dashboard.pendingConsents')}</Typography>
                    }
                    action={
                      <Chip size="small" label={formatPageCount(stateCountsQuery.data?.pending)} />
                    }
                  />
                  <Divider />
                  <CardContent>
                    {pendingConsentsQuery.isLoading ? (
                      <Stack spacing={1}>
                        <Skeleton height={40} />
                        <Skeleton height={40} />
                        <Skeleton height={40} />
                      </Stack>
                    ) : null}
                    {!pendingConsentsQuery.isLoading &&
                    !pendingConsentsQuery.isError &&
                    pendingConsents.length === 0 ? (
                      <Typography variant="body2" color="text.secondary">
                        {t('dashboard.noPending')}
                      </Typography>
                    ) : null}
                    {pendingConsents.map((consent) => (
                      <PendingConsentRow key={consent.id} consent={consent} />
                    ))}
                    {pendingConsents.length > 0 ? (
                      <Button
                        component={RouterLink}
                        to="/consents?view=pending&state=PENDING"
                        size="small"
                        endIcon={<ArrowRight size={15} />}
                        sx={{ mt: 1 }}
                      >
                        {t('dashboard.viewPending')}
                      </Button>
                    ) : null}
                  </CardContent>
                </Card>
              </>
            ) : null}
          </>
        ) : null}

        {showMyComplaints ? (
          <Stack spacing={2}>
            <Typography variant="h6" fontWeight={700}>
              {t('dashboard.complaintsTitle')}
            </Typography>

            {complaintSectionError ? (
              <Alert severity="error">{t('dashboard.complaintsLoadFailed')}</Alert>
            ) : null}

            <Box
              sx={{
                display: 'grid',
                gridTemplateColumns: { xs: '1fr 1fr', sm: 'repeat(3, 1fr)', md: 'repeat(6, 1fr)' },
                gap: 2,
              }}
            >
              <StatCard
                value={formatComplaintCount(complaintCountsQuery, (data) => data.total)}
                label={t('dashboard.totalComplaints')}
                icon={<Layers size={22} />}
                iconColor="primary"
              />
              <StatCard
                value={formatComplaintCount(complaintCountsQuery, (data) => data.open)}
                label={t('complaints.status.open')}
                icon={<Inbox size={22} />}
                iconColor="info"
              />
              <StatCard
                value={formatComplaintCount(complaintCountsQuery, (data) => data.inProgress)}
                label={t('complaints.status.investigation')}
                icon={<RefreshCw size={22} />}
                iconColor="warning"
              />
              <StatCard
                value={formatComplaintCount(complaintCountsQuery, (data) => data.waitingOnClient)}
                label={t('complaints.status.awaitingInfo')}
                icon={<UserCheck size={22} />}
                iconColor="error"
              />
              <StatCard
                value={formatComplaintCount(
                  complaintCountsQuery,
                  (data) => data.waitingOnInternalReview,
                )}
                label={t('complaints.status.waitingOnDpo')}
                icon={<Clock3 size={22} />}
                iconColor="warning"
              />
              <StatCard
                value={formatComplaintCount(complaintCountsQuery, (data) => data.resolved)}
                label={t('complaints.status.resolved')}
                icon={<CheckCircle2 size={22} />}
                iconColor="success"
              />
            </Box>

            <Box>
              <Button
                component={RouterLink}
                to="/complaints"
                size="small"
                endIcon={<ArrowRight size={15} />}
              >
                {t('dashboard.viewComplaints')}
              </Button>
            </Box>
          </Stack>
        ) : null}
      </Stack>
    </Box>
  )
}

export default DashboardPage
