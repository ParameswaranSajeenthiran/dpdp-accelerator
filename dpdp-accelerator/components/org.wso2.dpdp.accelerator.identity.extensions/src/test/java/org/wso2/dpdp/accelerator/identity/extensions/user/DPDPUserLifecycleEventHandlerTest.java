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

package org.wso2.dpdp.accelerator.identity.extensions.user;

import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import org.wso2.carbon.identity.event.IdentityEventConstants;
import org.wso2.carbon.identity.event.event.Event;
import org.wso2.dpdp.accelerator.event.notifications.common.listener.DPDPLifecycleEventListener;
import org.wso2.dpdp.accelerator.identity.extensions.internal.DPDPIdentityExtensionDataHolder;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Unlike {@code DPDPConsentManagementListener}/{@code DPDPConsentExpiryReconciler}, this touches
 * no {@code PrivilegedCarbonContext}, so it's fully testable with plain Mockito.
 */
public class DPDPUserLifecycleEventHandlerTest {

    @Mock
    private DPDPLifecycleEventListener lifecycleEventListener;

    @Mock
    private Event event;

    private DPDPUserLifecycleEventHandler handler;

    @BeforeMethod
    public void setUp() {

        MockitoAnnotations.openMocks(this);
        DPDPIdentityExtensionDataHolder.getInstance().setLifecycleEventListener(lifecycleEventListener);
        handler = new DPDPUserLifecycleEventHandler();
    }

    @Test
    public void getNameReturnsAFixedValue() {

        org.testng.Assert.assertEquals(handler.getName(), "dpdpUserLifecycleEventHandler");
    }

    @Test
    public void handleEventNotifiesAccountDeletedUsingUserId() {

        Map<String, Object> properties = new HashMap<>();
        properties.put(IdentityEventConstants.EventProperty.TENANT_DOMAIN, "tenant.example");
        properties.put(IdentityEventConstants.EventProperty.USER_ID, "user-id-123");
        properties.put(IdentityEventConstants.EventProperty.USER_NAME, "jdoe");
        when(event.getEventName()).thenReturn(IdentityEventConstants.Event.POST_DELETE_USER);
        when(event.getEventProperties()).thenReturn(properties);

        handler.handleEvent(event);

        verify(lifecycleEventListener).onUserAccountDeleted("tenant.example", "user-id-123");
    }

    @Test
    public void handleEventFallsBackToUsernameWhenUserIdIsAbsent() {

        Map<String, Object> properties = new HashMap<>();
        properties.put(IdentityEventConstants.EventProperty.TENANT_DOMAIN, "tenant.example");
        properties.put(IdentityEventConstants.EventProperty.USER_NAME, "jdoe");
        when(event.getEventName()).thenReturn(IdentityEventConstants.Event.POST_DELETE_USER);
        when(event.getEventProperties()).thenReturn(properties);

        handler.handleEvent(event);

        verify(lifecycleEventListener).onUserAccountDeleted("tenant.example", "jdoe");
    }

    @Test
    public void handleEventNotifiesDataChangedWithClaimUrisOnly() {

        Map<String, Object> claims = new HashMap<>();
        claims.put("http://wso2.org/claims/emailaddress", "jdoe@example.com");
        claims.put("http://wso2.org/claims/givenname", "Jane");

        Map<String, Object> properties = new HashMap<>();
        properties.put(IdentityEventConstants.EventProperty.TENANT_DOMAIN, "tenant.example");
        properties.put(IdentityEventConstants.EventProperty.USER_ID, "user-id-123");
        properties.put(IdentityEventConstants.EventProperty.USER_CLAIMS, claims);
        when(event.getEventName()).thenReturn(IdentityEventConstants.Event.POST_SET_USER_CLAIMS);
        when(event.getEventProperties()).thenReturn(properties);

        handler.handleEvent(event);

        verify(lifecycleEventListener).onUserDataChanged(anyString(), anyString(), anyList());
    }

    @Test
    @SuppressWarnings("unchecked")
    public void handleEventNeverForwardsClaimValuesOnlyUris() {

        Map<String, Object> claims = new HashMap<>();
        claims.put("http://wso2.org/claims/emailaddress", "jdoe@example.com");

        Map<String, Object> properties = new HashMap<>();
        properties.put(IdentityEventConstants.EventProperty.TENANT_DOMAIN, "tenant.example");
        properties.put(IdentityEventConstants.EventProperty.USER_ID, "user-id-123");
        properties.put(IdentityEventConstants.EventProperty.USER_CLAIMS, claims);
        when(event.getEventName()).thenReturn(IdentityEventConstants.Event.POST_SET_USER_CLAIMS);
        when(event.getEventProperties()).thenReturn(properties);

        handler.handleEvent(event);

        org.mockito.ArgumentCaptor<List> captor = org.mockito.ArgumentCaptor.forClass(List.class);
        verify(lifecycleEventListener).onUserDataChanged(anyString(), anyString(), captor.capture());
        List<String> changedClaimUris = captor.getValue();
        org.testng.Assert.assertEquals(changedClaimUris, Arrays.asList("http://wso2.org/claims/emailaddress"));
    }

    @Test
    public void handleEventIgnoresUnrelatedEvents() {

        when(event.getEventName()).thenReturn("SOME_OTHER_EVENT");

        handler.handleEvent(event);

        verifyNoInteractions(lifecycleEventListener);
    }

    @Test
    public void handleEventIsANoOpWhenNoListenerIsRegistered() {

        DPDPIdentityExtensionDataHolder.getInstance().setLifecycleEventListener(null);
        Map<String, Object> properties = new HashMap<>();
        properties.put(IdentityEventConstants.EventProperty.TENANT_DOMAIN, "tenant.example");
        properties.put(IdentityEventConstants.EventProperty.USER_ID, "user-id-123");
        when(event.getEventName()).thenReturn(IdentityEventConstants.Event.POST_DELETE_USER);
        when(event.getEventProperties()).thenReturn(properties);

        handler.handleEvent(event);
        // No exception - and nothing to verify against, since no listener was ever bound.
    }

    @Test
    public void handleEventSkipsWhenTenantDomainIsMissing() {

        Map<String, Object> properties = new HashMap<>();
        properties.put(IdentityEventConstants.EventProperty.USER_ID, "user-id-123");
        when(event.getEventName()).thenReturn(IdentityEventConstants.Event.POST_DELETE_USER);
        when(event.getEventProperties()).thenReturn(properties);

        handler.handleEvent(event);

        verify(lifecycleEventListener, never()).onUserAccountDeleted(anyString(), anyString());
    }

    @Test
    public void handleEventSwallowsAnExceptionFromTheListener() {

        Map<String, Object> properties = new HashMap<>();
        properties.put(IdentityEventConstants.EventProperty.TENANT_DOMAIN, "tenant.example");
        properties.put(IdentityEventConstants.EventProperty.USER_ID, "user-id-123");
        when(event.getEventName()).thenReturn(IdentityEventConstants.Event.POST_DELETE_USER);
        when(event.getEventProperties()).thenReturn(properties);
        org.mockito.Mockito.doThrow(new RuntimeException("boom")).when(lifecycleEventListener)
                .onUserAccountDeleted(anyString(), anyString());

        handler.handleEvent(event);
        // No exception propagated out of handleEvent.
    }
}
