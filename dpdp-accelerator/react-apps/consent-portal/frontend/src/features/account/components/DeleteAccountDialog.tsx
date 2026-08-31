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

import { UserX } from '@wso2/oxygen-ui-icons-react'
import { useTranslation } from 'react-i18next'
import ConsentActionDialog from '../../my-consents/components/ConsentActionDialog'

interface DeleteAccountDialogProps {
  open: boolean
  /** Whatever identifies the account to the reader - username or email. */
  accountLabel: string
  loading: boolean
  error?: string
  onClose: () => void
  onConfirm: () => void
}

/**
 * Irreversible-action confirmation for self-service account deletion. Uses the
 * shared destructive dialog so it reads like every other delete in the portal.
 */
function DeleteAccountDialog({
  open,
  accountLabel,
  loading,
  error,
  onClose,
  onConfirm,
}: DeleteAccountDialogProps): React.JSX.Element {
  const { t } = useTranslation('common')

  return (
    <ConsentActionDialog
      open={open}
      consentId={accountLabel}
      idLabel={t('account.deleteDialog.accountLabel')}
      title={t('account.deleteDialog.title')}
      message={t('account.deleteDialog.message')}
      note={t('account.deleteDialog.note')}
      confirmLabel={t('account.deleteDialog.confirm')}
      color="error"
      icon={<UserX size={20} />}
      loading={loading}
      error={error}
      onClose={onClose}
      onConfirm={onConfirm}
    />
  )
}

DeleteAccountDialog.defaultProps = {
  error: undefined,
}

export default DeleteAccountDialog
