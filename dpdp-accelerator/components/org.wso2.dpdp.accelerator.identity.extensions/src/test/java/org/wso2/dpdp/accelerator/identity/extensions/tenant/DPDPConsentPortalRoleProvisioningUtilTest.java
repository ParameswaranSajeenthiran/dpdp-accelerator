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

import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import org.wso2.carbon.identity.role.v2.mgt.core.RoleManagementService;
import org.wso2.carbon.identity.role.v2.mgt.core.model.Permission;
import org.wso2.carbon.identity.role.v2.mgt.core.model.RoleBasicInfo;
import org.wso2.carbon.identity.role.v2.mgt.core.model.UserBasicInfo;
import org.wso2.carbon.stratos.common.beans.TenantInfoBean;
import org.wso2.carbon.user.api.UserRealm;
import org.wso2.carbon.user.core.common.AbstractUserStoreManager;
import org.wso2.carbon.user.core.service.RealmService;
import org.wso2.dpdp.accelerator.identity.extensions.internal.DPDPIdentityExtensionDataHolder;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertEquals;

public class DPDPConsentPortalRoleProvisioningUtilTest {

    private static final String TENANT_DOMAIN = "tenant-a.com";
    private static final String APPLICATION_ID = "app-1234";
    private static final String ROLE_AUDIENCE = "application";
    private static final String ADMIN_ROLE_ID = "admin-role-id";
    private static final String USER_ROLE_ID = "user-role-id";
    private static final int TENANT_ID = 1;
    private static final String TENANT_ADMIN_USERNAME = "admin";
    private static final String TENANT_ADMIN_USER_ID = "admin-user-id";

    @Mock
    private RoleManagementService roleManagementService;
    @Mock
    private RealmService realmService;
    @Mock
    private UserRealm userRealm;
    @Mock
    private AbstractUserStoreManager userStoreManager;

    private TenantInfoBean tenantInfoBean;

    @BeforeMethod
    public void setUp() throws Exception {

        MockitoAnnotations.openMocks(this);
        DPDPIdentityExtensionDataHolder.getInstance().setRoleManagementService(roleManagementService);
        DPDPIdentityExtensionDataHolder.getInstance().setRealmService(realmService);

        tenantInfoBean = new TenantInfoBean();
        tenantInfoBean.setTenantId(TENANT_ID);
        tenantInfoBean.setAdmin(TENANT_ADMIN_USERNAME);

        // Resolves the tenant admin's user id by default in every test - only the tests that
        // specifically cover the assignment behaviour assert on it.
        when(realmService.getTenantUserRealm(TENANT_ID)).thenReturn(userRealm);
        when(userRealm.getUserStoreManager()).thenReturn(userStoreManager);
        when(userStoreManager.getUserIDFromUserName(TENANT_ADMIN_USERNAME)).thenReturn(TENANT_ADMIN_USER_ID);
        when(roleManagementService.addRole(eq(DPDPConsentPortalRoleProvisioningUtil.ADMIN_ROLE), anyList(),
                anyList(), anyList(), anyString(), anyString(), anyString()))
                .thenReturn(new RoleBasicInfo(ADMIN_ROLE_ID, DPDPConsentPortalRoleProvisioningUtil.ADMIN_ROLE));
        when(roleManagementService.addRole(eq(DPDPConsentPortalRoleProvisioningUtil.USER_ROLE), anyList(), anyList(),
                anyList(), anyString(), anyString(), anyString()))
                .thenReturn(new RoleBasicInfo(USER_ROLE_ID, DPDPConsentPortalRoleProvisioningUtil.USER_ROLE));
        // No one assigned yet by default, so the assignment path runs unless a test says otherwise.
        when(roleManagementService.getUserListOfRole(anyString(), anyString())).thenReturn(Collections.emptyList());
    }

    private static List<String> permissionNames(List<Permission> permissions) {

        List<String> names = new ArrayList<>();
        for (Permission permission : permissions) {
            names.add(permission.getName());
        }
        return names;
    }

    @Test
    public void createRolesCreatesAdminRoleWithAllScopesAndUserRoleWithOnlySelfScopedOnes() throws Exception {

        List<String> scopes = Arrays.asList("internal_consent_mgt_consent_view",
                "portal:complaints:read:self", "portal:complaints:read:any");

        DPDPConsentPortalRoleProvisioningUtil.createRoles(APPLICATION_ID, TENANT_DOMAIN, scopes, tenantInfoBean);

        ArgumentCaptor<List<Permission>> adminPermissionsCaptor = ArgumentCaptor.forClass(List.class);
        verify(roleManagementService).addRole(eq(DPDPConsentPortalRoleProvisioningUtil.ADMIN_ROLE),
                eq(Collections.emptyList()), eq(Collections.emptyList()), adminPermissionsCaptor.capture(),
                eq(ROLE_AUDIENCE), eq(APPLICATION_ID), eq(TENANT_DOMAIN));
        assertEquals(permissionNames(adminPermissionsCaptor.getValue()), scopes);

        ArgumentCaptor<List<Permission>> userPermissionsCaptor = ArgumentCaptor.forClass(List.class);
        verify(roleManagementService).addRole(eq(DPDPConsentPortalRoleProvisioningUtil.USER_ROLE),
                eq(Collections.emptyList()), eq(Collections.emptyList()), userPermissionsCaptor.capture(),
                eq(ROLE_AUDIENCE), eq(APPLICATION_ID), eq(TENANT_DOMAIN));
        assertEquals(permissionNames(userPermissionsCaptor.getValue()),
                Collections.singletonList("portal:complaints:read:self"));

        verify(roleManagementService, times(2)).addRole(anyString(), anyList(), anyList(), anyList(), anyString(),
                anyString(), anyString());
    }

    @Test
    public void createRolesHandlesEmptyScopeList() throws Exception {

        DPDPConsentPortalRoleProvisioningUtil.createRoles(APPLICATION_ID, TENANT_DOMAIN, Collections.emptyList(),
                tenantInfoBean);

        verify(roleManagementService).addRole(eq(DPDPConsentPortalRoleProvisioningUtil.ADMIN_ROLE),
                eq(Collections.emptyList()), eq(Collections.emptyList()), eq(Collections.emptyList()),
                eq(ROLE_AUDIENCE), eq(APPLICATION_ID), eq(TENANT_DOMAIN));
        verify(roleManagementService).addRole(eq(DPDPConsentPortalRoleProvisioningUtil.USER_ROLE),
                eq(Collections.emptyList()), eq(Collections.emptyList()), eq(Collections.emptyList()),
                eq(ROLE_AUDIENCE), eq(APPLICATION_ID), eq(TENANT_DOMAIN));
    }

    @Test
    public void createRolesAddsOnlyTheMissingScopesToAnAlreadyExistingRole() throws Exception {

        // dpdp-consent-admin already exists (e.g. created before portal:complaints:* scopes
        // existed) and already carries the consent-mgt scope - only the two new complaint scopes
        // should be added, and nothing already granted should be touched or removed.
        when(roleManagementService.isExistingRoleName(DPDPConsentPortalRoleProvisioningUtil.ADMIN_ROLE,
                ROLE_AUDIENCE, APPLICATION_ID, TENANT_DOMAIN)).thenReturn(true);
        when(roleManagementService.isExistingRoleName(DPDPConsentPortalRoleProvisioningUtil.USER_ROLE,
                ROLE_AUDIENCE, APPLICATION_ID, TENANT_DOMAIN)).thenReturn(false);
        when(roleManagementService.getRoleIdByName(DPDPConsentPortalRoleProvisioningUtil.ADMIN_ROLE, ROLE_AUDIENCE,
                APPLICATION_ID, TENANT_DOMAIN)).thenReturn(ADMIN_ROLE_ID);
        when(roleManagementService.getPermissionListOfRole(ADMIN_ROLE_ID, TENANT_DOMAIN))
                .thenReturn(Collections.singletonList(new Permission("internal_consent_mgt_consent_view")));

        List<String> scopes = Arrays.asList("internal_consent_mgt_consent_view", "portal:complaints:read:any",
                "portal:complaints:write:any");

        DPDPConsentPortalRoleProvisioningUtil.createRoles(APPLICATION_ID, TENANT_DOMAIN, scopes, tenantInfoBean);

        verify(roleManagementService, never()).addRole(eq(DPDPConsentPortalRoleProvisioningUtil.ADMIN_ROLE),
                anyList(), anyList(), anyList(), anyString(), anyString(), anyString());

        ArgumentCaptor<List<Permission>> addedCaptor = ArgumentCaptor.forClass(List.class);
        verify(roleManagementService).updatePermissionListOfRole(eq(ADMIN_ROLE_ID), addedCaptor.capture(),
                eq(Collections.emptyList()), eq(TENANT_DOMAIN));
        assertEquals(permissionNames(addedCaptor.getValue()),
                Arrays.asList("portal:complaints:read:any", "portal:complaints:write:any"));

        // USER_ROLE didn't exist, so it's created fresh with only the :self-scoped permission -
        // none of the :any ones apply to it.
        verify(roleManagementService).addRole(eq(DPDPConsentPortalRoleProvisioningUtil.USER_ROLE), anyList(),
                anyList(), eq(Collections.emptyList()), eq(ROLE_AUDIENCE), eq(APPLICATION_ID), eq(TENANT_DOMAIN));
    }

    @Test
    public void createRolesLeavesAnExistingRoleAloneWhenItAlreadyHasEveryDesiredScope() throws Exception {

        when(roleManagementService.isExistingRoleName(DPDPConsentPortalRoleProvisioningUtil.USER_ROLE,
                ROLE_AUDIENCE, APPLICATION_ID, TENANT_DOMAIN)).thenReturn(true);
        when(roleManagementService.isExistingRoleName(DPDPConsentPortalRoleProvisioningUtil.ADMIN_ROLE,
                ROLE_AUDIENCE, APPLICATION_ID, TENANT_DOMAIN)).thenReturn(false);
        when(roleManagementService.getRoleIdByName(DPDPConsentPortalRoleProvisioningUtil.USER_ROLE, ROLE_AUDIENCE,
                APPLICATION_ID, TENANT_DOMAIN)).thenReturn(USER_ROLE_ID);
        when(roleManagementService.getPermissionListOfRole(USER_ROLE_ID, TENANT_DOMAIN))
                .thenReturn(Collections.singletonList(new Permission("portal:complaints:read:self")));

        DPDPConsentPortalRoleProvisioningUtil.createRoles(APPLICATION_ID, TENANT_DOMAIN,
                Collections.singletonList("portal:complaints:read:self"), tenantInfoBean);

        verify(roleManagementService, never()).updatePermissionListOfRole(anyString(), anyList(), anyList(),
                anyString());
    }

    @Test
    public void createRolesAssignsTheTenantAdminToTheAdminRoleWhenNotAlreadyAMember() throws Exception {

        DPDPConsentPortalRoleProvisioningUtil.createRoles(APPLICATION_ID, TENANT_DOMAIN,
                Collections.singletonList("portal:complaints:read:any"), tenantInfoBean);

        verify(roleManagementService).updateUserListOfRole(ADMIN_ROLE_ID,
                Collections.singletonList(TENANT_ADMIN_USER_ID), Collections.emptyList(), TENANT_DOMAIN);
    }

    @Test
    public void createRolesDoesNotReassignATenantAdminAlreadyInTheAdminRole() throws Exception {

        when(roleManagementService.getUserListOfRole(ADMIN_ROLE_ID, TENANT_DOMAIN))
                .thenReturn(Collections.singletonList(new UserBasicInfo(TENANT_ADMIN_USER_ID, TENANT_ADMIN_USERNAME)));

        DPDPConsentPortalRoleProvisioningUtil.createRoles(APPLICATION_ID, TENANT_DOMAIN,
                Collections.singletonList("portal:complaints:read:any"), tenantInfoBean);

        verify(roleManagementService, never()).updateUserListOfRole(anyString(), anyList(), anyList(), anyString());
    }

    @Test
    public void createRolesSkipsAssignmentWhenTheTenantUserRealmIsUnavailable() throws Exception {

        when(realmService.getTenantUserRealm(TENANT_ID)).thenReturn(null);

        // Must not throw - a provisioning run that can't resolve the admin's user id should still
        // finish creating/updating the roles themselves.
        DPDPConsentPortalRoleProvisioningUtil.createRoles(APPLICATION_ID, TENANT_DOMAIN,
                Collections.singletonList("portal:complaints:read:any"), tenantInfoBean);

        verify(roleManagementService, never()).updateUserListOfRole(anyString(), anyList(), anyList(), anyString());
    }
}
