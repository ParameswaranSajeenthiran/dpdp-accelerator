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

import { Alert, Button, Snackbar, UserMenu } from '@wso2/oxygen-ui'
import { UserX } from '@wso2/oxygen-ui-icons-react'
import { useEffect, useState } from 'react'
import { useTranslation } from 'react-i18next'
import DeleteAccountDialog from '../../../features/account/components/DeleteAccountDialog'
import useDeleteAccountMutation from '../../../features/account/hooks/useDeleteAccountMutation'
import useAuthorization from '../../../features/auth/useAuthorization'
import { APIError } from '../../../utils/apiClient'
import { getUserProfile, logout } from '../../../utils/authClient'
import { REQUIRED_SCOPES } from '../../../utils/scopes'

type UserClaims = Record<string, unknown>

function claim(claims: UserClaims, name: string): string | undefined {
  const value = claims[name]

  return typeof value === 'string' && value.trim() ? value.trim() : undefined
}

function displayName(claims: UserClaims, fallback: string): string {
  const name = claim(claims, 'name') ?? claim(claims, 'displayName')
  if (name) {
    return name
  }

  const fullName = [claim(claims, 'given_name'), claim(claims, 'family_name')]
    .filter(Boolean)
    .join(' ')

  return fullName || claim(claims, 'preferred_username') || claim(claims, 'username') || fallback
}

function UserProfileMenu(): React.JSX.Element {
  const { t } = useTranslation('common')
  const { hasScope } = useAuthorization()
  const [logoutFailed, setLogoutFailed] = useState(false)
  const [logoutPending, setLogoutPending] = useState(false)
  const [deleteDialogOpen, setDeleteDialogOpen] = useState(false)
  const [deletionPending, setDeletionPending] = useState(false)
  const [claims, setClaims] = useState<UserClaims>({})
  const deleteAccount = useDeleteAccountMutation()

  useEffect(() => {
    let cancelled = false
    // A failure here is not worth interrupting the page for: the menu falls
    // back to the unknown-user labels.
    void getUserProfile()
      .then((profile) => {
        if (!cancelled && profile) {
          setClaims(profile)
        }
      })
      .catch(() => {})
    return () => {
      cancelled = true
    }
  }, [])

  const name = displayName(claims, t('layout.userMenu.unknownUser'))
  const email = claim(claims, 'email') ?? claim(claims, 'sub') ?? t('layout.userMenu.noEmail')
  const avatar = claim(claims, 'picture')
  const canDeleteAccount = hasScope(REQUIRED_SCOPES.ACCOUNT_SELF_DELETE)

  const handleLogout = async (): Promise<void> => {
    if (logoutPending) {
      return
    }

    setLogoutFailed(false)
    setLogoutPending(true)
    try {
      await logout()
    } catch {
      setLogoutFailed(true)
    } finally {
      setLogoutPending(false)
    }
  }

  const handleDeleteDialogClose = (): void => {
    if (!deleteAccount.isPending) {
      deleteAccount.reset()
      setDeleteDialogOpen(false)
    }
  }

  const handleDeleteConfirm = (): void => {
    if (deleteAccount.isPending) {
      return
    }
    deleteAccount.mutate(undefined, {
      onSuccess: (outcome) => {
        // A completed deletion navigates away; only the approval case returns
        // to a page that is still there to update.
        if (outcome === 'pendingApproval') {
          setDeleteDialogOpen(false)
          setDeletionPending(true)
        }
      },
    })
  }

  /**
   * A 400 here is the Identity Server refusing a second request while one is
   * still awaiting approval - the only invalid state this call can be in.
   */
  const deleteErrorMessage = ((): string | undefined => {
    if (!deleteAccount.isError) {
      return undefined
    }
    const { error } = deleteAccount
    return error instanceof APIError && error.status === 400
      ? t('account.deleteAlreadyRequested')
      : t('account.deleteError')
  })()

  return (
    <>
      <UserMenu>
        <UserMenu.Trigger name={name} avatar={avatar} />
        <UserMenu.Header name={name} email={email} avatar={avatar} />
        <UserMenu.Divider />
        {canDeleteAccount ? (
          <UserMenu.Item
            icon={<UserX size={16} />}
            label={t('account.deleteMenuItem')}
            onClick={() => setDeleteDialogOpen(true)}
          />
        ) : null}
        <UserMenu.Logout label={t('layout.userMenu.signOut')} onClick={handleLogout} />
      </UserMenu>
      {canDeleteAccount ? (
        <DeleteAccountDialog
          open={deleteDialogOpen}
          accountLabel={email}
          loading={deleteAccount.isPending}
          error={deleteErrorMessage}
          onClose={handleDeleteDialogClose}
          onConfirm={handleDeleteConfirm}
        />
      ) : null}
      <Snackbar
        open={deletionPending}
        onClose={() => setDeletionPending(false)}
        anchorOrigin={{ vertical: 'bottom', horizontal: 'center' }}
      >
        <Alert severity="info" variant="filled">
          {t('account.deletePendingApproval')}
        </Alert>
      </Snackbar>
      <Snackbar
        open={logoutFailed}
        onClose={() => setLogoutFailed(false)}
        anchorOrigin={{ vertical: 'bottom', horizontal: 'center' }}
      >
        <Alert
          severity="error"
          variant="filled"
          action={
            <Button color="inherit" size="small" disabled={logoutPending} onClick={handleLogout}>
              {t('layout.userMenu.tryAgain')}
            </Button>
          }
        >
          {t('layout.userMenu.signOutError')}
        </Alert>
      </Snackbar>
    </>
  )
}

export default UserProfileMenu
