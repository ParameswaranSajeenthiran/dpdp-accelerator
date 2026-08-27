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

import { Box, Button, CircularProgress, Stack, Typography } from '@wso2/oxygen-ui'
import { CircleAlert } from '@wso2/oxygen-ui-icons-react'
import { useEffect, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { Navigate, Route, Routes, useNavigate } from 'react-router-dom'
import MainLayout from './components/layout/main-layout/MainLayout'
import ElementDetailsPage from './features/catalog/ElementDetailsPage'
import ElementListPage from './features/catalog/ElementListPage'
import PurposeDetailsPage from './features/catalog/PurposeDetailsPage'
import PurposeListPage from './features/catalog/PurposeListPage'
import SubscriptionsPage from './features/events/SubscriptionsPage'
import SubscriptionDetailsPage from './features/events/SubscriptionDetailsPage'
import TopicsPage from './features/events/TopicsPage'
import EventsPage from './features/events/EventsPage'
import EventDetailsPage from './features/events/EventDetailsPage'
import AdminConsentRegistryPage from './features/admin-consents/AdminConsentRegistryPage'
import ConsentDetailsPage from './features/my-consents/ConsentDetailsPage'
import ConsentRegistryPage from './features/my-consents/ConsentRegistryPage'
import DashboardPage from './features/dashboard/DashboardPage'
import AccountDeletedPage from './features/account/AccountDeletedPage'
import { AuthorizationProvider } from './features/auth/AuthorizationProvider'
import useAuthorization from './features/auth/useAuthorization'
import firstAuthorizedPath from './features/auth/authorizationRoutes'
import NoAccessPage from './features/auth/NoAccessPage'
import useCurrentUserQuery from './features/auth/hooks/useCurrentUserQuery'
import { ensureSignedIn, isAuthEnabled, takeReturnPath } from './utils/authClient'
import { REQUIRED_SCOPES, type ScopeRequirement } from './utils/scopes'
import { APIError } from './utils/apiClient'

/**
 * Establishes the OIDC session before anything else renders.
 *
 * `ensureSignedIn` either completes a sign-in that is already in flight (the
 * authorization code handed over by the shell) or sends the browser to the
 * Identity Server, in which case there is nothing to draw.
 *
 * Sign-in always returns to the application home, so a session established
 * here may have started on another route. Restoring it is the last step.
 */
function AuthenticationGate({
  children,
}: {
  children: React.JSX.Element
}): React.JSX.Element | null {
  const { t } = useTranslation('common')
  const navigate = useNavigate()
  const [sessionReady, setSessionReady] = useState(false)
  const [sessionFailed, setSessionFailed] = useState(false)
  const currentUserQuery = useCurrentUserQuery(sessionReady)

  useEffect(() => {
    let cancelled = false
    void (async () => {
      try {
        const ready = await ensureSignedIn()
        if (!cancelled && ready) {
          const returnPath = takeReturnPath()
          if (returnPath) {
            navigate(returnPath, { replace: true })
          }
          setSessionReady(true)
        }
      } catch {
        if (!cancelled) {
          setSessionFailed(true)
        }
      }
    })()
    return () => {
      cancelled = true
    }
  }, [navigate])

  if (sessionFailed) {
    return (
      <Box
        sx={{
          alignItems: 'center',
          display: 'flex',
          justifyContent: 'center',
          minHeight: '100dvh',
          p: 4,
        }}
      >
        <Stack spacing={2} alignItems="center" sx={{ textAlign: 'center' }}>
          <CircleAlert size={40} aria-hidden="true" />
          <Typography variant="h4" fontWeight={700}>
            {t('authorization.loadFailed')}
          </Typography>
          <Button variant="outlined" onClick={() => window.location.reload()}>
            {t('authorization.tryAgain')}
          </Button>
        </Stack>
      </Box>
    )
  }

  if (!sessionReady || currentUserQuery.isPending) {
    return (
      <Box sx={{ display: 'grid', minHeight: '100vh', placeItems: 'center' }}>
        <CircularProgress aria-label={t('authorization.loading')} />
      </Box>
    )
  }

  if (currentUserQuery.isError || !currentUserQuery.data) {
    if (
      isAuthEnabled() &&
      currentUserQuery.error instanceof APIError &&
      currentUserQuery.error.status === 401
    ) {
      return null
    }
    return (
      <Box
        sx={{
          alignItems: 'center',
          display: 'flex',
          justifyContent: 'center',
          minHeight: '100dvh',
          p: 4,
        }}
      >
        <Stack spacing={2} alignItems="center" sx={{ textAlign: 'center' }}>
          <CircleAlert size={40} aria-hidden="true" />
          <Typography variant="h4" fontWeight={700}>
            {t('authorization.loadFailed')}
          </Typography>
          <Button variant="outlined" onClick={() => currentUserQuery.refetch()}>
            {t('authorization.tryAgain')}
          </Button>
        </Stack>
      </Box>
    )
  }

  return (
    <AuthorizationProvider currentUser={currentUserQuery.data}>{children}</AuthorizationProvider>
  )
}

function AuthorizedRoute({
  scope,
  children,
}: {
  scope: ScopeRequirement
  children: React.JSX.Element
}): React.JSX.Element {
  const { currentUser, hasScope } = useAuthorization()
  if (hasScope(scope)) {
    return children
  }
  const fallback = firstAuthorizedPath(currentUser.scopes)
  return fallback ? <Navigate to={fallback} replace /> : <NoAccessPage />
}

function AuthorizedFallback(): React.JSX.Element {
  const { currentUser } = useAuthorization()
  const fallback = firstAuthorizedPath(currentUser.scopes)
  return fallback ? <Navigate to={fallback} replace /> : <NoAccessPage />
}

/** Everything behind the sign-in. Extracted unchanged from App so the public
 * account-deleted route can live outside the authentication gate. */
function ProtectedRoutes(): React.JSX.Element {
  return (
    <AuthenticationGate>
      <Routes>
        <Route element={<MainLayout />}>
          <Route
            path="/dashboard"
            element={
              <AuthorizedRoute scope={REQUIRED_SCOPES.CONSENTS_READ_SELF}>
                <DashboardPage />
              </AuthorizedRoute>
            }
          />
          <Route
            path="/consents"
            element={
              <AuthorizedRoute scope={REQUIRED_SCOPES.CONSENTS_READ_SELF}>
                <ConsentRegistryPage />
              </AuthorizedRoute>
            }
          />
          <Route
            path="/consents/:id"
            element={
              <AuthorizedRoute scope={REQUIRED_SCOPES.CONSENTS_READ_SELF}>
                <ConsentDetailsPage />
              </AuthorizedRoute>
            }
          />
          <Route
            path="/purposes"
            element={
              <AuthorizedRoute scope={REQUIRED_SCOPES.PURPOSES_READ}>
                <PurposeListPage />
              </AuthorizedRoute>
            }
          />
          <Route
            path="/purposes/:id"
            element={
              <AuthorizedRoute scope={REQUIRED_SCOPES.PURPOSES_READ}>
                <PurposeDetailsPage />
              </AuthorizedRoute>
            }
          />
          <Route
            path="/elements"
            element={
              <AuthorizedRoute scope={REQUIRED_SCOPES.ELEMENTS_READ}>
                <ElementListPage />
              </AuthorizedRoute>
            }
          />
          <Route
            path="/elements/:id"
            element={
              <AuthorizedRoute scope={REQUIRED_SCOPES.ELEMENTS_READ}>
                <ElementDetailsPage />
              </AuthorizedRoute>
            }
          />
          <Route
            path="/events"
            element={
              <AuthorizedRoute scope={REQUIRED_SCOPES.EVENTS_READ}>
                <EventsPage />
              </AuthorizedRoute>
            }
          />
          <Route
            path="/events/topics"
            element={
              <AuthorizedRoute scope={REQUIRED_SCOPES.EVENT_TOPICS_READ}>
                <TopicsPage />
              </AuthorizedRoute>
            }
          />
          <Route
            path="/events/subscriptions"
            element={
              <AuthorizedRoute scope={REQUIRED_SCOPES.EVENT_SUBSCRIPTIONS_READ}>
                <SubscriptionsPage />
              </AuthorizedRoute>
            }
          />
          <Route
            path="/events/subscriptions/:id"
            element={
              <AuthorizedRoute scope={REQUIRED_SCOPES.EVENT_SUBSCRIPTIONS_READ}>
                <SubscriptionDetailsPage />
              </AuthorizedRoute>
            }
          />
          <Route
            path="/events/:id"
            element={
              <AuthorizedRoute scope={REQUIRED_SCOPES.EVENTS_READ}>
                <EventDetailsPage />
              </AuthorizedRoute>
            }
          />
          <Route
            path="/administration/consents"
            element={
              <AuthorizedRoute scope={REQUIRED_SCOPES.CONSENTS_READ_ANY}>
                <AdminConsentRegistryPage />
              </AuthorizedRoute>
            }
          />
          <Route
            path="/administration/consents/:id"
            element={
              <AuthorizedRoute scope={REQUIRED_SCOPES.CONSENTS_READ_ANY}>
                <ConsentDetailsPage variant="admin" />
              </AuthorizedRoute>
            }
          />
          <Route path="*" element={<AuthorizedFallback />} />
        </Route>
      </Routes>
    </AuthenticationGate>
  )
}

function App(): React.JSX.Element {
  return (
    <Routes>
      <Route path="/account-deleted" element={<AccountDeletedPage />} />
      <Route path="/*" element={<ProtectedRoutes />} />
    </Routes>
  )
}

export default App
