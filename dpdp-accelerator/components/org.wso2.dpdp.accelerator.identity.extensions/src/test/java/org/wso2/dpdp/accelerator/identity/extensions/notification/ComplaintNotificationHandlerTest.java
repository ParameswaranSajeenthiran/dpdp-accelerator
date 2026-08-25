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

import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import org.wso2.carbon.identity.application.common.model.ServiceProvider;
import org.wso2.carbon.identity.application.mgt.ApplicationManagementService;
import org.wso2.carbon.identity.event.IdentityEventConstants;
import org.wso2.carbon.identity.event.bean.IdentityEventMessageContext;
import org.wso2.carbon.identity.event.event.Event;
import org.wso2.carbon.identity.core.util.IdentityTenantUtil;
import org.wso2.carbon.identity.core.util.IdentityUtil;
import org.wso2.carbon.identity.event.services.IdentityEventService;
import org.wso2.carbon.identity.role.v2.mgt.core.RoleManagementService;
import org.wso2.carbon.identity.role.v2.mgt.core.model.UserBasicInfo;
import org.wso2.carbon.user.api.UserRealm;
import org.wso2.carbon.user.core.common.AbstractUserStoreManager;
import org.wso2.carbon.user.core.service.RealmService;
import org.wso2.carbon.user.core.tenant.TenantManager;
import org.wso2.dpdp.accelerator.identity.extensions.internal.DPDPIdentityExtensionDataHolder;
import org.wso2.dpdp.accelerator.identity.extensions.tenant.DPDPConsentPortalRoleProvisioningUtil;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

public class ComplaintNotificationHandlerTest {

    private static final String TENANT_DOMAIN = "tenant-a.com";
    private static final String APPLICATION_ID = "app-1234";
    private static final String ROLE_AUDIENCE = "application";
    private static final String ADMIN_ROLE_ID = "admin-role-id";
    private static final int TENANT_ID = 1;
    private static final String EMAIL_CLAIM = "http://wso2.org/claims/emailaddress";

    @Mock
    private IdentityEventService identityEventService;
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

    private ComplaintNotificationHandler handler;
    private MockedStatic<IdentityUtil> identityUtilMock;

    @BeforeMethod
    public void setUp() throws Exception {

        MockitoAnnotations.openMocks(this);
        // IdentityUtil.getServerURL(...) resolves against IS's own bootstrap-populated
        // ConfigurationContextService, which is never set in a bare unit test - statically mocked
        // here rather than left to throw, since ComplaintNotificationHandler calls it to build the
        // "Review & Reply" action link for every email it sends.
        identityUtilMock = Mockito.mockStatic(IdentityUtil.class);
        identityUtilMock.when(() -> IdentityUtil.getServerURL(anyString(), anyBoolean(), anyBoolean()))
                .thenReturn("https://localhost:9443/consent-portal/");
        DPDPIdentityExtensionDataHolder.getInstance().setIdentityEventService(identityEventService);
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
        when(roleManagementService.isExistingRoleName(DPDPConsentPortalRoleProvisioningUtil.ADMIN_ROLE, ROLE_AUDIENCE,
                APPLICATION_ID, TENANT_DOMAIN)).thenReturn(true);
        when(roleManagementService.getRoleIdByName(DPDPConsentPortalRoleProvisioningUtil.ADMIN_ROLE, ROLE_AUDIENCE,
                APPLICATION_ID, TENANT_DOMAIN)).thenReturn(ADMIN_ROLE_ID);
        when(realmService.getTenantManager()).thenReturn(tenantManager);
        when(tenantManager.getTenantId(TENANT_DOMAIN)).thenReturn(TENANT_ID);
        when(realmService.getTenantUserRealm(TENANT_ID)).thenReturn(userRealm);
        when(userRealm.getUserStoreManager()).thenReturn(userStoreManager);

        handler = new ComplaintNotificationHandler();
    }

    @AfterMethod
    public void tearDown() {

        identityUtilMock.close();
    }

    private static Event customEvent(Map<String, Object> properties) {

        return new Event(DPDPComplaintEventConstants.COMPLAINT_NOTIFICATION_EVENT, properties);
    }

    @Test
    public void canHandleMatchesOnlyTheComplaintNotificationEventName() {

        assertTrue(handler.canHandle(new IdentityEventMessageContext(customEvent(new HashMap<>()))));
        assertFalse(handler.canHandle(
                new IdentityEventMessageContext(new Event("SOME_OTHER_EVENT", new HashMap<>()))));
    }

    @Test
    public void getNameReturnsTheRegisteredHandlerName() {

        assertEquals(handler.getName(), DPDPComplaintEventConstants.NOTIFICATION_HANDLER_NAME);
    }

    @Test
    public void handleEventForComplaintCreatedNotifiesEveryOfficer() throws Exception {

        when(roleManagementService.getUserListOfRole(ADMIN_ROLE_ID, TENANT_DOMAIN))
                .thenReturn(List.of(new UserBasicInfo("officer-1", "officer1")));
        when(userStoreManager.getUserClaimValue("officer1", EMAIL_CLAIM, null)).thenReturn("officer1@example.com");

        Map<String, Object> properties = new HashMap<>();
        properties.put(IdentityEventConstants.EventProperty.TENANT_DOMAIN, TENANT_DOMAIN);
        properties.put(DPDPComplaintEventConstants.PROP_NOTIFICATION_TYPE,
                DPDPComplaintEventConstants.NOTIFICATION_TYPE_COMPLAINT_CREATED);
        properties.put(DPDPComplaintEventConstants.PROP_REFERENCE_ID, "CMP-2026-00001");
        properties.put(DPDPComplaintEventConstants.PROP_CATEGORY, "DATA_BREACH");

        handler.handleEvent(customEvent(properties));

        ArgumentCaptor<Event> captor = ArgumentCaptor.forClass(Event.class);
        verify(identityEventService).handleEvent(captor.capture());
        Event triggerEvent = captor.getValue();
        assertEquals(triggerEvent.getEventName(), IdentityEventConstants.Event.TRIGGER_NOTIFICATION);
        Map<String, Object> triggerProps = triggerEvent.getEventProperties();
        assertEquals(triggerProps.get(DPDPComplaintEventConstants.TRIGGER_PROP_SEND_TO), "officer1@example.com");
        assertEquals(triggerProps.get(IdentityEventConstants.EventProperty.USER_NAME), "officer1");
        assertEquals(triggerProps.get(DPDPComplaintEventConstants.TRIGGER_PROP_TEMPLATE_TYPE),
                DPDPComplaintEventConstants.NOTIFICATION_TYPE_COMPLAINT_CREATED);
        assertEquals(triggerProps.get(DPDPComplaintEventConstants.PROP_REFERENCE_ID), "CMP-2026-00001");
    }

    @Test
    public void handleEventForOfficerCommentNotifiesEveryOfficerToo() throws Exception {

        when(roleManagementService.getUserListOfRole(ADMIN_ROLE_ID, TENANT_DOMAIN))
                .thenReturn(List.of(new UserBasicInfo("officer-1", "officer1")));
        when(userStoreManager.getUserClaimValue("officer1", EMAIL_CLAIM, null)).thenReturn("officer1@example.com");

        Map<String, Object> properties = new HashMap<>();
        properties.put(IdentityEventConstants.EventProperty.TENANT_DOMAIN, TENANT_DOMAIN);
        properties.put(DPDPComplaintEventConstants.PROP_NOTIFICATION_TYPE,
                DPDPComplaintEventConstants.NOTIFICATION_TYPE_COMMENT_ADDED);
        properties.put(DPDPComplaintEventConstants.PROP_ACTOR_ROLE, "USER");

        handler.handleEvent(customEvent(properties));

        verify(identityEventService, times(1)).handleEvent(any(Event.class));
        verify(roleManagementService).getUserListOfRole(ADMIN_ROLE_ID, TENANT_DOMAIN);
    }

    @Test
    public void handleEventForCommentByOfficerNotifiesTheComplaintCreatorInstead() throws Exception {

        when(userStoreManager.getUserClaimValue("citizen1", EMAIL_CLAIM, null)).thenReturn("citizen1@example.com");

        Map<String, Object> properties = new HashMap<>();
        properties.put(IdentityEventConstants.EventProperty.TENANT_DOMAIN, TENANT_DOMAIN);
        properties.put(DPDPComplaintEventConstants.PROP_NOTIFICATION_TYPE,
                DPDPComplaintEventConstants.NOTIFICATION_TYPE_COMMENT_ADDED);
        properties.put(DPDPComplaintEventConstants.PROP_ACTOR_ROLE, "COMPLAINT_OFFICER");
        properties.put(DPDPComplaintEventConstants.PROP_CREATOR_USER_ID, "citizen-id-1");
        properties.put(DPDPComplaintEventConstants.PROP_CREATOR_USER_NAME, "citizen1");

        handler.handleEvent(customEvent(properties));

        ArgumentCaptor<Event> captor = ArgumentCaptor.forClass(Event.class);
        verify(identityEventService).handleEvent(captor.capture());
        assertEquals(captor.getValue().getEventProperties().get(DPDPComplaintEventConstants.TRIGGER_PROP_SEND_TO),
                "citizen1@example.com");
        verify(roleManagementService, never()).getUserListOfRole(anyString(), anyString());
    }

    @Test
    public void handleEventDoesNothingWhenNoRecipientsResolve() throws Exception {

        when(roleManagementService.getUserListOfRole(ADMIN_ROLE_ID, TENANT_DOMAIN)).thenReturn(List.of());

        Map<String, Object> properties = new HashMap<>();
        properties.put(IdentityEventConstants.EventProperty.TENANT_DOMAIN, TENANT_DOMAIN);
        properties.put(DPDPComplaintEventConstants.PROP_NOTIFICATION_TYPE,
                DPDPComplaintEventConstants.NOTIFICATION_TYPE_COMPLAINT_CREATED);

        handler.handleEvent(customEvent(properties));

        verify(identityEventService, never()).handleEvent(any(Event.class));
    }

    @Test
    public void handleEventDoesNothingWhenTenantDomainOrNotificationTypeIsMissing() throws Exception {

        handler.handleEvent(customEvent(new HashMap<>()));

        verify(identityEventService, never()).handleEvent(any(Event.class));
    }
}
