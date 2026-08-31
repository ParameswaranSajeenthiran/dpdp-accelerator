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

import { Box, Button, Stack, Typography } from '@wso2/oxygen-ui'
import { CircleCheckBig } from '@wso2/oxygen-ui-icons-react'
import { useTranslation } from 'react-i18next'
import { runtimeBasePath } from '../../utils/basePath'

/**
 * Where a deleted account lands. Deliberately outside the authentication gate:
 * the visitor no longer has an account, so anything that tries to establish a
 * session would bounce them to a sign-in they cannot complete.
 */
export default function AccountDeletedPage(): React.JSX.Element {
  const { t } = useTranslation('common')

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
      <Stack spacing={2} alignItems="center" sx={{ textAlign: 'center', maxWidth: 480 }}>
        <CircleCheckBig size={40} aria-hidden="true" />
        <Typography variant="h4" fontWeight={700}>
          {t('account.deletedPage.title')}
        </Typography>
        <Typography variant="body1" color="text.secondary">
          {t('account.deletedPage.body')}
        </Typography>
        <Button
          variant="outlined"
          onClick={() => window.location.assign(`${window.location.origin}${runtimeBasePath()}`)}
        >
          {t('account.deletedPage.signInLink')}
        </Button>
      </Stack>
    </Box>
  )
}
