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

import type { CurrentUser } from '../../../types/auth'
import { getBasicUser, isAuthEnabled } from '../../../utils/authClient'
import { tenantFromPath } from '../../../utils/basePath'
import { IS_SCOPES, parseScopes } from '../../../utils/scopes'

const SUPER_TENANT = 'carbon.super'

/**
 * The signed-in user, taken from the session the auth SDK holds.
 *
 * `allowedScopes` is what the Identity Server actually granted this session,
 * and that is what the UI gates on - a user without the consent management
 * scopes never sees those areas.
 */
export async function fetchCurrentUser(): Promise<CurrentUser> {
  if (!isAuthEnabled()) {
    // Development only: authentication is switched off, so nothing is gated.
    return {
      userId: 'anonymous',
      organizationId: tenantFromPath() ?? SUPER_TENANT,
      scopes: Object.values(IS_SCOPES),
    }
  }

  const user = await getBasicUser()
  if (!user) {
    throw new Error('no authenticated session')
  }

  const userId = (user.sub ?? user.username ?? '').trim()
  if (!userId) {
    throw new Error('the authenticated session has no subject')
  }

  return {
    userId,
    organizationId: user.tenantDomain?.trim() || tenantFromPath() || SUPER_TENANT,
    scopes: parseScopes(user.allowedScopes),
  }
}
