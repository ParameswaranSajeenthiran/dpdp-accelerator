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

import { config, requireConfigured, trimTrailingSlash } from './config'
import type { Persona } from './env'

/**
 * The subset of server configuration that is safe to import before any persona exists -
 * everything here comes straight from utils/config.ts, which validates nothing until asked.
 * Never import utils/env.ts as a value from setup-project code (tests/01-provisioning/*,
 * playwright.config.ts, global-setup.ts) - see this plan's top-level note on why.
 */
export const isBaseUrl = trimTrailingSlash(config.identityServer.baseUrl)
export const ignoreHttpsErrors = config.identityServer.ignoreHttpsErrors

export const superAdmin: Persona = {
  username: requireConfigured(config.superAdmin.username, 'superAdmin.username'),
  password: requireConfigured(config.superAdmin.password, 'superAdmin.password'),
}
