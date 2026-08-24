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

package org.wso2.dpdp.accelerator.identity.extensions.notification;

import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import org.wso2.carbon.identity.application.common.model.ServiceProvider;
import org.wso2.carbon.identity.application.mgt.ApplicationManagementService;
import org.wso2.carbon.identity.role.v2.mgt.core.RoleManagementService;
import org.wso2.carbon.identity.role.v2.mgt.core.model.UserBasicInfo;
import org.wso2.carbon.identity.core.util.IdentityTenantUtil;
import org.wso2.carbon.user.api.UserRealm;
import org.wso2.carbon.user.core.common.AbstractUserStoreManager;
import org.wso2.carbon.user.core.service.RealmService;
import org.wso2.carbon.user.core.tenant.TenantManager;
import org.wso2.dpdp.accelerator.identity.extensions.internal.DPDPIdentityExtensionDataHolder;
import org.wso2.dpdp.accelerator.identity.extensions.tenant.DPDPConsentPortalRoleProvisioningUtil;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

public class ComplaintNotificationRecipientResolverTest {

    private static final String TENANT_DOMAIN = "tenant-a.com";
    private static final String APPLICATION_ID = "app-1234";
    private static final String ROLE_AUDIENCE = "application";
    private static final String ADMIN_ROLE_ID = "admin-role-id";
    private static final int TENANT_ID = 1;
    private static final String EMAIL_CLAIM = "http://wso2.org/claims/email";

    @Mock
    private ApplicationManagementService applicationManagementService;
    @Mock
    private RoleManagementService roleManagementService;
    @Mock
    private RealmService realmService;
    @Mock
    private UserRealm userRealm;
    @Mock
    private AbstractUserStoreManager userStoreManager;
    @Mock
    private TenantManager tenantManager;

    @BeforeMethod
    public void setUp() throws Exception {

        MockitoAnnotations.openMocks(this);
        DPDPIdentityExtensionDataHolder.getInstance().setApplicationManagementService(applicationManagementService);
        DPDPIdentityExtensionDataHolder.getInstance().setRoleManagementService(roleManagementService);
        DPDPIdentityExtensionDataHolder.getInstance().setRealmService(realmService);
        // IdentityTenantUtil.getTenantId(...) resolves against its own static RealmService
        // reference - normally set by IS's own bootstrap, never by this bundle - so tests must
        // set it directly.
        IdentityTenantUtil.setRealmService(realmService);

        ServiceProvider serviceProvider = new ServiceProvider();
        serviceProvider.setApplicationResourceId(APPLICATION_ID);
        when(applicationManagementService.getApplicationExcludingFileBasedSPs(anyString(), eq(TENANT_DOMAIN)))
                .thenReturn(serviceProvider);
        when(realmService.getTenantManager()).thenReturn(tenantManager);
        when(tenantManager.getTenantId(TENANT_DOMAIN)).thenReturn(TENANT_ID);
        when(realmService.getTenantUserRealm(TENANT_ID)).thenReturn(userRealm);
        when(userRealm.getUserStoreManager()).thenReturn(userStoreManager);
    }

    @Test
    public void resolveOfficersReturnsEveryMemberWithAResolvableEmail() throws Exception {

        when(roleManagementService.isExistingRoleName(DPDPConsentPortalRoleProvisioningUtil.ADMIN_ROLE, ROLE_AUDIENCE,
                APPLICATION_ID, TENANT_DOMAIN)).thenReturn(true);
        when(roleManagementService.getRoleIdByName(DPDPConsentPortalRoleProvisioningUtil.ADMIN_ROLE, ROLE_AUDIENCE,
                APPLICATION_ID, TENANT_DOMAIN)).thenReturn(ADMIN_ROLE_ID);
        when(roleManagementService.getUserListOfRole(ADMIN_ROLE_ID, TENANT_DOMAIN)).thenReturn(List.of(
                new UserBasicInfo("officer-1", "officer1"), new UserBasicInfo("officer-2", "officer2")));
        when(userStoreManager.getUserClaimValue("officer1", EMAIL_CLAIM, null)).thenReturn("officer1@example.com");
        when(userStoreManager.getUserClaimValue("officer2", EMAIL_CLAIM, null)).thenReturn("officer2@example.com");

        List<ComplaintNotificationRecipientResolver.Recipient> recipients =
                ComplaintNotificationRecipientResolver.resolveOfficers(TENANT_DOMAIN);

        assertEquals(recipients.size(), 2);
        assertEquals(recipients.get(0).getEmail(), "officer1@example.com");
        assertEquals(recipients.get(1).getEmail(), "officer2@example.com");
    }

    @Test
    public void resolveOfficersSkipsMembersWithNoResolvableEmail() throws Exception {

        when(roleManagementService.isExistingRoleName(DPDPConsentPortalRoleProvisioningUtil.ADMIN_ROLE, ROLE_AUDIENCE,
                APPLICATION_ID, TENANT_DOMAIN)).thenReturn(true);
        when(roleManagementService.getRoleIdByName(DPDPConsentPortalRoleProvisioningUtil.ADMIN_ROLE, ROLE_AUDIENCE,
                APPLICATION_ID, TENANT_DOMAIN)).thenReturn(ADMIN_ROLE_ID);
        when(roleManagementService.getUserListOfRole(ADMIN_ROLE_ID, TENANT_DOMAIN))
                .thenReturn(List.of(new UserBasicInfo("officer-1", "officer1")));
        when(userStoreManager.getUserClaimValue("officer1", EMAIL_CLAIM, null)).thenReturn(null);

        List<ComplaintNotificationRecipientResolver.Recipient> recipients =
                ComplaintNotificationRecipientResolver.resolveOfficers(TENANT_DOMAIN);

        assertTrue(recipients.isEmpty());
    }

    @Test
    public void resolveOfficersReturnsEmptyWhenTheRoleDoesNotExist() throws Exception {

        when(roleManagementService.isExistingRoleName(DPDPConsentPortalRoleProvisioningUtil.ADMIN_ROLE, ROLE_AUDIENCE,
                APPLICATION_ID, TENANT_DOMAIN)).thenReturn(false);

        List<ComplaintNotificationRecipientResolver.Recipient> recipients =
                ComplaintNotificationRecipientResolver.resolveOfficers(TENANT_DOMAIN);

        assertTrue(recipients.isEmpty());
    }

    @Test
    public void resolveOfficersReturnsEmptyWhenNoApplicationExistsForTheTenant() throws Exception {

        when(applicationManagementService.getApplicationExcludingFileBasedSPs(anyString(), eq(TENANT_DOMAIN)))
                .thenReturn(null);

        List<ComplaintNotificationRecipientResolver.Recipient> recipients =
                ComplaintNotificationRecipientResolver.resolveOfficers(TENANT_DOMAIN);

        assertTrue(recipients.isEmpty());
    }

    @Test
    public void resolveCreatorUsesTheGivenUsernameWhenPresent() throws Exception {

        when(userStoreManager.getUserClaimValue("user1", EMAIL_CLAIM, null)).thenReturn("user1@example.com");

        Optional<ComplaintNotificationRecipientResolver.Recipient> recipient =
                ComplaintNotificationRecipientResolver.resolveCreator("user-id-1", "user1", TENANT_DOMAIN);

        assertTrue(recipient.isPresent());
        assertEquals(recipient.get().getUsername(), "user1");
        assertEquals(recipient.get().getEmail(), "user1@example.com");
    }

    @Test
    public void resolveCreatorFallsBackToResolvingUsernameFromUserIdWhenUsernameIsBlank() throws Exception {

        when(userStoreManager.getUserNameFromUserID("user-id-1")).thenReturn("user1");
        when(userStoreManager.getUserClaimValue("user1", EMAIL_CLAIM, null)).thenReturn("user1@example.com");

        Optional<ComplaintNotificationRecipientResolver.Recipient> recipient =
                ComplaintNotificationRecipientResolver.resolveCreator("user-id-1", null, TENANT_DOMAIN);

        assertTrue(recipient.isPresent());
        assertEquals(recipient.get().getUsername(), "user1");
    }

    @Test
    public void resolveCreatorReturnsEmptyWhenNoUsernameCanBeResolvedAtAll() throws Exception {

        when(userStoreManager.getUserNameFromUserID("user-id-1")).thenReturn(null);

        Optional<ComplaintNotificationRecipientResolver.Recipient> recipient =
                ComplaintNotificationRecipientResolver.resolveCreator("user-id-1", "  ", TENANT_DOMAIN);

        assertFalse(recipient.isPresent());
    }

    @Test
    public void resolveCreatorReturnsEmptyWhenTheEmailClaimIsUnresolvable() throws Exception {

        when(userStoreManager.getUserClaimValue("user1", EMAIL_CLAIM, null)).thenReturn(" ");

        Optional<ComplaintNotificationRecipientResolver.Recipient> recipient =
                ComplaintNotificationRecipientResolver.resolveCreator("user-id-1", "user1", TENANT_DOMAIN);

        assertFalse(recipient.isPresent());
    }

    @Test
    public void resolveOfficersReturnsEmptyWhenRoleManagementServiceThrows() throws Exception {

        when(roleManagementService.isExistingRoleName(DPDPConsentPortalRoleProvisioningUtil.ADMIN_ROLE, ROLE_AUDIENCE,
                APPLICATION_ID, TENANT_DOMAIN)).thenThrow(new RuntimeException("boom"));

        List<ComplaintNotificationRecipientResolver.Recipient> recipients =
                ComplaintNotificationRecipientResolver.resolveOfficers(TENANT_DOMAIN);

        assertEquals(recipients, Collections.emptyList());
    }
}
