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
import org.wso2.carbon.identity.role.v2.mgt.core.model.UserBasicInfo;
import org.wso2.carbon.stratos.common.beans.TenantInfoBean;
import org.wso2.carbon.user.api.UserRealm;
import org.wso2.carbon.user.core.common.AbstractUserStoreManager;
import org.wso2.dpdp.accelerator.identity.extensions.internal.DPDPIdentityExtensionDataHolder;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Creates/updates the {@code dpdp-consent-admin}/{@code dpdp-consent-user} roles for a tenant's
 * DPDP Consent Portal application. Split out from
 * {@link DPDPConsentPortalAppProvisioningUtil} since role creation is a distinct concern from
 * registering the application itself.
 *
 * <p>{@code dpdp-consent-admin} gets every authorized scope (consent-mgt and
 * {@code portal:complaints:*}, both {@code :self} and {@code :any}); {@code dpdp-consent-user}
 * gets only the {@code :self}-suffixed ones, so an ordinary citizen can file/view their own
 * complaints via {@code /me/*} but never reach the officer-facing {@code /complaints/*} routes.
 * The tenant's admin user is also added as a member of {@code dpdp-consent-admin} - role creation
 * alone grants no one membership, so without this no one could use either the consent or
 * complaint admin features until someone manually assigned themselves the role via the Console.
 */
public final class DPDPConsentPortalRoleProvisioningUtil {

    private static final Log LOG = LogFactory.getLog(DPDPConsentPortalRoleProvisioningUtil.class);
    // Public so other bundle code (e.g. complaint notification recipient resolution) can look up
    // role membership without duplicating the role name literal.
    public static final String ADMIN_ROLE = "dpdp-consent-admin";
    static final String USER_ROLE = "dpdp-consent-user";
    private static final String ROLE_AUDIENCE = "application";
    private static final String SELF_SCOPE_SUFFIX = ":self";

    private DPDPConsentPortalRoleProvisioningUtil() {

    }

    public static void createRoles(String applicationId, String tenantDomain, List<String> authorizedScopeNames,
            TenantInfoBean tenantInfoBean) throws Exception {

        RoleManagementService roleManagementService = DPDPIdentityExtensionDataHolder.getInstance()
                .getRoleManagementService();

        List<String> selfScopeNames = authorizedScopeNames.stream()
                .filter(scopeName -> scopeName.endsWith(SELF_SCOPE_SUFFIX))
                .collect(Collectors.toList());

        String adminRoleId = createOrUpdateRole(roleManagementService, ADMIN_ROLE, authorizedScopeNames,
                applicationId, tenantDomain);
        createOrUpdateRole(roleManagementService, USER_ROLE, selfScopeNames, applicationId, tenantDomain);

        assignTenantAdminIfMissing(roleManagementService, adminRoleId, tenantInfoBean, tenantDomain);
    }

    /**
     * Creates the role fresh (with every one of {@code scopeNames} as a permission) if it doesn't
     * exist yet. If it already exists - e.g. this tenant's role predates a scope this accelerator
     * only started granting later, such as when {@code portal:complaints:*} scopes were added
     * after {@code dpdp-consent-admin} already existed - only the scopes it's still missing are
     * added; nothing already granted is ever removed.
     *
     * @return the role's id, whether just-created or pre-existing.
     */
    private static String createOrUpdateRole(RoleManagementService roleManagementService, String roleName,
            List<String> scopeNames, String applicationId, String tenantDomain) throws Exception {

        List<Permission> permissions = new ArrayList<>();
        for (String scopeName : scopeNames) {
            permissions.add(new Permission(scopeName));
        }

        if (!roleManagementService.isExistingRoleName(roleName, ROLE_AUDIENCE, applicationId, tenantDomain)) {
            String roleId = roleManagementService.addRole(roleName, Collections.emptyList(), Collections.emptyList(),
                    permissions, ROLE_AUDIENCE, applicationId, tenantDomain).getId();
            LOG.debug("Created role '" + roleName + "' with " + permissions.size() + " permission(s) for "
                    + "application: " + applicationId);
            return roleId;
        }

        String roleId = roleManagementService.getRoleIdByName(roleName, ROLE_AUDIENCE, applicationId, tenantDomain);
        Set<String> existingScopeNames = roleManagementService.getPermissionListOfRole(roleId, tenantDomain).stream()
                .map(Permission::getName)
                .collect(Collectors.toCollection(HashSet::new));

        List<Permission> missingPermissions = permissions.stream()
                .filter(permission -> !existingScopeNames.contains(permission.getName()))
                .collect(Collectors.toList());
        if (missingPermissions.isEmpty()) {
            LOG.debug("Role '" + roleName + "' already has every desired permission for application: "
                    + applicationId + "; nothing to add.");
        } else {
            roleManagementService.updatePermissionListOfRole(roleId, missingPermissions, Collections.emptyList(),
                    tenantDomain);
            LOG.debug("Added " + missingPermissions.size() + " missing permission(s) to existing role '" + roleName
                    + "' for application: " + applicationId);
        }
        return roleId;
    }

    /**
     * Adds the tenant's admin user as a member of {@code dpdp-consent-admin} unless they're
     * already a member (of this role specifically, or via some other route) - re-running this on
     * every tenant update must not re-add a user who was deliberately removed from the role since
     * the last run.
     */
    private static void assignTenantAdminIfMissing(RoleManagementService roleManagementService, String adminRoleId,
            TenantInfoBean tenantInfoBean, String tenantDomain) throws Exception {

        String adminUserId = resolveUserId(tenantInfoBean);
        if (adminUserId == null) {
            LOG.warn("Could not resolve a user id for tenant admin '" + tenantInfoBean.getAdmin() + "' in tenant '"
                    + tenantDomain + "'; skipping automatic " + ADMIN_ROLE + " membership.");
            return;
        }

        boolean alreadyAssigned = roleManagementService.getUserListOfRole(adminRoleId, tenantDomain).stream()
                .map(UserBasicInfo::getId)
                .anyMatch(adminUserId::equals);
        if (alreadyAssigned) {
            LOG.debug("Tenant admin '" + tenantInfoBean.getAdmin() + "' is already a member of " + ADMIN_ROLE
                    + " for tenant: " + tenantDomain);
            return;
        }

        roleManagementService.updateUserListOfRole(adminRoleId, Collections.singletonList(adminUserId),
                Collections.emptyList(), tenantDomain);
        LOG.debug("Assigned tenant admin '" + tenantInfoBean.getAdmin() + "' to " + ADMIN_ROLE + " for tenant: "
                + tenantDomain);
    }

    /**
     * Resolves the tenant admin's user id from their username via the tenant's own user store -
     * role membership is tracked by user id, not username, in the role v2 model. Returns
     * {@code null} (rather than throwing) if the tenant's user realm isn't available or its user
     * store isn't the unique-id-capable kind this accelerator requires - callers must treat that
     * as "can't determine", not fail the whole provisioning run over it.
     */
    private static String resolveUserId(TenantInfoBean tenantInfoBean) throws Exception {

        UserRealm userRealm = DPDPIdentityExtensionDataHolder.getInstance().getRealmService()
                .getTenantUserRealm(tenantInfoBean.getTenantId());
        if (userRealm == null) {
            return null;
        }
        org.wso2.carbon.user.api.UserStoreManager userStoreManager = userRealm.getUserStoreManager();
        if (!(userStoreManager instanceof AbstractUserStoreManager)) {
            return null;
        }
        return ((AbstractUserStoreManager) userStoreManager).getUserIDFromUserName(tenantInfoBean.getAdmin());
    }
}
