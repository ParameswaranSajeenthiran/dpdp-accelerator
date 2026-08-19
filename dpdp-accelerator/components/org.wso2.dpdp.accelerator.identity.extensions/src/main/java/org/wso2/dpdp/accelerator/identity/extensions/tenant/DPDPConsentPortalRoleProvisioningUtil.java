/*
 * Copyright (c) 2026, WSO2 LLC. (https://www.wso2.com).
 *
 * WSO2 LLC. licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.wso2.dpdp.accelerator.identity.extensions.tenant;

import org.wso2.carbon.identity.role.v2.mgt.core.RoleManagementService;
import org.wso2.carbon.identity.role.v2.mgt.core.model.Permission;
import org.wso2.dpdp.accelerator.identity.extensions.internal.DPDPIdentityExtensionDataHolder;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Creates the {@code dpdp-consent-admin}/{@code dpdp-consent-user} roles for a tenant's
 * DPDP Consent Portal application. Split out from
 * {@link DPDPConsentPortalAppProvisioningUtil} since role creation is a distinct concern from
 * registering the application itself.
 */
public final class DPDPConsentPortalRoleProvisioningUtil {

    static final String ADMIN_ROLE = "dpdp-consent-admin";
    static final String USER_ROLE = "dpdp-consent-user";
    private static final String ROLE_AUDIENCE = "application";

    private DPDPConsentPortalRoleProvisioningUtil() {

    }

    public static void createRoles(String applicationId, String tenantDomain, List<String> authorizedScopeNames)
            throws Exception {

        RoleManagementService roleManagementService = DPDPIdentityExtensionDataHolder.getInstance()
                .getRoleManagementService();

        List<Permission> adminPermissions = new ArrayList<>();
        for (String scopeName : authorizedScopeNames) {
            adminPermissions.add(new Permission(scopeName));
        }
        roleManagementService.addRole(ADMIN_ROLE, Collections.emptyList(), Collections.emptyList(),
                adminPermissions, ROLE_AUDIENCE, applicationId, tenantDomain);
        roleManagementService.addRole(USER_ROLE, Collections.emptyList(), Collections.emptyList(),
                Collections.emptyList(), ROLE_AUDIENCE, applicationId, tenantDomain);
    }
}
