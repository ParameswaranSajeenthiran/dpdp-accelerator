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
import org.wso2.dpdp.accelerator.identity.extensions.internal.DPDPIdentityExtensionDataHolder;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertEquals;

public class DPDPConsentPortalRoleProvisioningUtilTest {

    private static final String TENANT_DOMAIN = "tenant-a.com";
    private static final String APPLICATION_ID = "app-1234";
    private static final String ROLE_AUDIENCE = "application";

    @Mock
    private RoleManagementService roleManagementService;

    @BeforeMethod
    public void setUp() {

        MockitoAnnotations.openMocks(this);
        DPDPIdentityExtensionDataHolder.getInstance().setRoleManagementService(roleManagementService);
    }

    @Test
    public void createRolesCreatesAdminRoleWithAllScopesAndUserRoleWithNone() throws Exception {

        List<String> scopes = Arrays.asList("internal_consent_mgt_consent_view",
                "internal_consent_mgt_purpose_view", "notifications:events:read",
                "notifications:events:write", "notifications:events:poll",
                "notifications:event-deliveries:complete");

        DPDPConsentPortalRoleProvisioningUtil.createRoles(APPLICATION_ID, TENANT_DOMAIN, scopes);

        ArgumentCaptor<List<Permission>> adminPermissionsCaptor = ArgumentCaptor.forClass(List.class);
        verify(roleManagementService).addRole(eq(DPDPConsentPortalRoleProvisioningUtil.ADMIN_ROLE),
                eq(Collections.emptyList()), eq(Collections.emptyList()), adminPermissionsCaptor.capture(),
                eq(ROLE_AUDIENCE), eq(APPLICATION_ID), eq(TENANT_DOMAIN));
        List<String> adminPermissionNames = new java.util.ArrayList<>();
        for (Permission permission : adminPermissionsCaptor.getValue()) {
            adminPermissionNames.add(permission.getName());
        }
        assertEquals(adminPermissionNames, Arrays.asList("internal_consent_mgt_consent_view",
                "internal_consent_mgt_purpose_view", "notifications:events:read", "notifications:events:write",
                "notifications:events:poll", "notifications:event-deliveries:complete"));

        verify(roleManagementService).addRole(eq(DPDPConsentPortalRoleProvisioningUtil.USER_ROLE),
                eq(Collections.emptyList()), eq(Collections.emptyList()), eq(Collections.emptyList()),
                eq(ROLE_AUDIENCE), eq(APPLICATION_ID), eq(TENANT_DOMAIN));

        verify(roleManagementService, times(2)).addRole(anyString(), anyList(), anyList(), anyList(), anyString(),
                anyString(), anyString());
    }

    @Test
    public void createRolesHandlesEmptyScopeList() throws Exception {

        DPDPConsentPortalRoleProvisioningUtil.createRoles(APPLICATION_ID, TENANT_DOMAIN, Collections.emptyList());

        verify(roleManagementService).addRole(eq(DPDPConsentPortalRoleProvisioningUtil.ADMIN_ROLE),
                eq(Collections.emptyList()), eq(Collections.emptyList()), eq(Collections.emptyList()),
                eq(ROLE_AUDIENCE), eq(APPLICATION_ID), eq(TENANT_DOMAIN));
    }

    @Test
    public void createRolesSkipsRolesThatAlreadyExist() throws Exception {

        when(roleManagementService.isExistingRoleName(DPDPConsentPortalRoleProvisioningUtil.ADMIN_ROLE,
                ROLE_AUDIENCE, APPLICATION_ID, TENANT_DOMAIN)).thenReturn(true);
        when(roleManagementService.isExistingRoleName(DPDPConsentPortalRoleProvisioningUtil.USER_ROLE,
                ROLE_AUDIENCE, APPLICATION_ID, TENANT_DOMAIN)).thenReturn(false);
        DPDPConsentPortalRoleProvisioningUtil.createRoles(APPLICATION_ID, TENANT_DOMAIN, Collections.emptyList());

        verify(roleManagementService, never()).addRole(eq(DPDPConsentPortalRoleProvisioningUtil.ADMIN_ROLE),
                anyList(), anyList(), anyList(), anyString(), anyString(), anyString());
        verify(roleManagementService).addRole(eq(DPDPConsentPortalRoleProvisioningUtil.USER_ROLE), anyList(),
                anyList(), anyList(), eq(ROLE_AUDIENCE), eq(APPLICATION_ID), eq(TENANT_DOMAIN));
    }

}
