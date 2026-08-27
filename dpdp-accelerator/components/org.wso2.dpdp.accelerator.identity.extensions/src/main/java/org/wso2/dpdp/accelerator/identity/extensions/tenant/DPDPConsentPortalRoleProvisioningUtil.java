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

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.wso2.carbon.identity.role.v2.mgt.core.RoleManagementService;
import org.wso2.carbon.identity.role.v2.mgt.core.model.Permission;
import org.wso2.dpdp.accelerator.identity.extensions.internal.DPDPIdentityExtensionDataHolder;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Creates the {@code dpdp-consent-admin}/{@code dpdp-consent-user} roles for a tenant's
 * DPDP Consent Portal application. Split out from
 * {@link DPDPConsentPortalAppProvisioningUtil} since role creation is a distinct concern from
 * registering the application itself.
 */
public final class DPDPConsentPortalRoleProvisioningUtil {

    private static final Log LOG = LogFactory.getLog(DPDPConsentPortalRoleProvisioningUtil.class);
    static final String ADMIN_ROLE = "dpdp-consent-admin";
    static final String USER_ROLE = "dpdp-consent-user";
    private static final String ROLE_AUDIENCE = "application";

    private DPDPConsentPortalRoleProvisioningUtil() {

    }

    /**
     * Creates or reconciles the two portal roles. Admin scopes and user scopes are deliberately
     * separate lists: {@code account:self:delete} must reach {@code dpdp-consent-user} only, so
     * admins cannot delete their own account through the portal.
     */
    public static void createRoles(String applicationId, String tenantDomain, List<String> adminScopeNames,
            List<String> userScopeNames) throws Exception {

        RoleManagementService roleManagementService = DPDPIdentityExtensionDataHolder.getInstance()
                .getRoleManagementService();

        reconcileRole(roleManagementService, ADMIN_ROLE, toPermissions(adminScopeNames), applicationId, tenantDomain);
        reconcileRole(roleManagementService, USER_ROLE, toPermissions(userScopeNames), applicationId, tenantDomain);
    }

    private static List<Permission> toPermissions(List<String> scopeNames) {

        List<Permission> permissions = new ArrayList<>();
        for (String scopeName : scopeNames) {
            permissions.add(new Permission(scopeName));
        }
        return permissions;
    }

    /**
     * Creates the role when missing; otherwise adds whichever required permissions the role
     * doesn't have yet. Never removes permissions, so anything an operator granted by hand
     * survives. Adding on re-run is what lets tenants provisioned by an older accelerator pick
     * up newly introduced scopes through the documented "update the tenant" recovery flow.
     */
    private static void reconcileRole(RoleManagementService roleManagementService, String roleName,
            List<Permission> requiredPermissions, String applicationId, String tenantDomain) throws Exception {

        if (!roleManagementService.isExistingRoleName(roleName, ROLE_AUDIENCE, applicationId, tenantDomain)) {
            roleManagementService.addRole(roleName, Collections.emptyList(), Collections.emptyList(),
                    requiredPermissions, ROLE_AUDIENCE, applicationId, tenantDomain);
            LOG.debug("Created role '" + roleName + "' with " + requiredPermissions.size()
                    + " permission(s) for application: " + applicationId);
            return;
        }

        String roleId = roleManagementService.getRoleIdByName(roleName, ROLE_AUDIENCE, applicationId, tenantDomain);
        Set<String> existingNames = new HashSet<>();
        for (Permission permission : roleManagementService.getPermissionListOfRole(roleId, tenantDomain)) {
            existingNames.add(permission.getName());
        }

        List<Permission> missing = new ArrayList<>();
        for (Permission permission : requiredPermissions) {
            if (!existingNames.contains(permission.getName())) {
                missing.add(permission);
            }
        }
        if (missing.isEmpty()) {
            LOG.debug("Role '" + roleName + "' already has every required permission; leaving it as is.");
            return;
        }
        roleManagementService.updatePermissionListOfRole(roleId, missing, Collections.emptyList(), tenantDomain);
        LOG.debug("Added " + missing.size() + " missing permission(s) to role '" + roleName
                + "' for application: " + applicationId);
    }
}
