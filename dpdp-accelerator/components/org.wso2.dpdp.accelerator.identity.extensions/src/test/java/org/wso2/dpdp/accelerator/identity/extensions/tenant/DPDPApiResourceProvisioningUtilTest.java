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
import org.wso2.carbon.identity.api.resource.mgt.APIResourceManager;
import org.wso2.carbon.identity.application.common.IdentityApplicationManagementException;
import org.wso2.carbon.identity.application.common.model.APIResource;
import org.wso2.carbon.identity.application.common.model.AuthorizedAPI;
import org.wso2.carbon.identity.application.common.model.Scope;
import org.wso2.carbon.identity.application.mgt.AuthorizedAPIManagementService;
import org.wso2.dpdp.accelerator.identity.extensions.internal.DPDPIdentityExtensionDataHolder;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertEqualsNoOrder;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.expectThrows;

public class DPDPApiResourceProvisioningUtilTest {

    private static final String TENANT_DOMAIN = "tenant-a.com";
    private static final String APPLICATION_ID = "app-1234";
    private static final String CONSENT_HISTORY_API_IDENTIFIER = "/api/dpdp/consent-mgt/v1";
    private static final String CONSENT_HISTORY_API_RESOURCE_ID = "res-consent-history";

    @Mock
    private APIResourceManager apiResourceManager;

    @Mock
    private AuthorizedAPIManagementService authorizedAPIManagementService;

    @BeforeMethod
    public void setUp() {

        MockitoAnnotations.openMocks(this);
        DPDPIdentityExtensionDataHolder.getInstance().setApiResourceManager(apiResourceManager);
        DPDPIdentityExtensionDataHolder.getInstance()
                .setAuthorizedAPIManagementService(authorizedAPIManagementService);
    }

    // ----- Consent Management (IS-native) -----

    @Test
    public void authorizeConsentManagementAPIsAuthorizesAllThreeAndCollectsScopes() throws Exception {

        Scope consentScope = mockScope("internal_consent_mgt_consent_view");
        APIResource consentsResource = mockResource("res-consents", Arrays.asList(consentScope));
        Scope purposeScope = mockScope("internal_consent_mgt_purpose_view");
        APIResource purposesResource = mockResource("res-purposes", Arrays.asList(purposeScope));
        Scope elementScope = mockScope("internal_consent_mgt_element_view");
        APIResource elementsResource = mockResource("res-elements", Arrays.asList(elementScope));

        when(apiResourceManager.getAPIResourceByIdentifier("/api/identity/consent-mgt/v2.0/consents", TENANT_DOMAIN))
                .thenReturn(consentsResource);
        when(apiResourceManager.getAPIResourceByIdentifier("/api/identity/consent-mgt/v2.0/purposes", TENANT_DOMAIN))
                .thenReturn(purposesResource);
        when(apiResourceManager.getAPIResourceByIdentifier("/api/identity/consent-mgt/v2.0/elements", TENANT_DOMAIN))
                .thenReturn(elementsResource);

        List<String> scopes = DPDPApiResourceProvisioningUtil
                .authorizeConsentManagementAPIs(APPLICATION_ID, TENANT_DOMAIN);

        assertEquals(scopes, Arrays.asList("internal_consent_mgt_consent_view", "internal_consent_mgt_purpose_view",
                "internal_consent_mgt_element_view"));
        verify(authorizedAPIManagementService, times(3)).addAuthorizedAPI(eq(APPLICATION_ID), any(AuthorizedAPI.class),
                eq(TENANT_DOMAIN));
    }

    @Test
    public void authorizeConsentManagementAPIsSkipsApisAlreadyAuthorized() throws Exception {

        Scope consentScope = mockScope("internal_consent_mgt_consent_view");
        APIResource consentsResource = mockResource("res-consents", Arrays.asList(consentScope));
        Scope purposeScope = mockScope("internal_consent_mgt_purpose_view");
        APIResource purposesResource = mockResource("res-purposes", Arrays.asList(purposeScope));
        Scope elementScope = mockScope("internal_consent_mgt_element_view");
        APIResource elementsResource = mockResource("res-elements", Arrays.asList(elementScope));

        when(apiResourceManager.getAPIResourceByIdentifier("/api/identity/consent-mgt/v2.0/consents", TENANT_DOMAIN))
                .thenReturn(consentsResource);
        when(apiResourceManager.getAPIResourceByIdentifier("/api/identity/consent-mgt/v2.0/purposes", TENANT_DOMAIN))
                .thenReturn(purposesResource);
        when(apiResourceManager.getAPIResourceByIdentifier("/api/identity/consent-mgt/v2.0/elements", TENANT_DOMAIN))
                .thenReturn(elementsResource);

        // "consents" is already authorized - it should be left alone, not re-added.
        AuthorizedAPI existingAuthorization = mock(AuthorizedAPI.class);
        when(existingAuthorization.getScopes()).thenReturn(Arrays.asList(consentScope));
        when(authorizedAPIManagementService.getAuthorizedAPI(APPLICATION_ID, "res-consents", TENANT_DOMAIN))
                .thenReturn(existingAuthorization);

        List<String> scopes = DPDPApiResourceProvisioningUtil
                .authorizeConsentManagementAPIs(APPLICATION_ID, TENANT_DOMAIN);

        assertEquals(scopes, Arrays.asList("internal_consent_mgt_consent_view", "internal_consent_mgt_purpose_view",
                "internal_consent_mgt_element_view"));

        ArgumentCaptor<AuthorizedAPI> authorizedApiCaptor = ArgumentCaptor.forClass(AuthorizedAPI.class);
        verify(authorizedAPIManagementService, times(2)).addAuthorizedAPI(eq(APPLICATION_ID),
                authorizedApiCaptor.capture(), eq(TENANT_DOMAIN));
        List<String> reAuthorizedApiIds = new ArrayList<>();
        for (AuthorizedAPI authorizedApi : authorizedApiCaptor.getAllValues()) {
            reAuthorizedApiIds.add(authorizedApi.getAPIId());
        }
        assertFalse(reAuthorizedApiIds.contains("res-consents"));
    }

    @Test
    public void authorizeConsentManagementAPIsThrowsWhenAResourceIsMissing() throws Exception {

        when(apiResourceManager.getAPIResourceByIdentifier(anyString(), eq(TENANT_DOMAIN))).thenReturn(null);

        expectThrows(IdentityApplicationManagementException.class,
                () -> DPDPApiResourceProvisioningUtil.authorizeConsentManagementAPIs(APPLICATION_ID, TENANT_DOMAIN));

        verify(authorizedAPIManagementService, never()).addAuthorizedAPI(anyString(), any(AuthorizedAPI.class),
                anyString());
    }

    // ----- Event Notification -----

    @Test
    public void registerEventNotificationAPIsRegistersMissingResourcesWithAllScopes() throws Exception {

        when(apiResourceManager.getAPIResourceByIdentifier(anyString(), eq(TENANT_DOMAIN))).thenReturn(null);

        DPDPApiResourceProvisioningUtil.registerEventNotificationAPIs(TENANT_DOMAIN);

        ArgumentCaptor<APIResource> resourceCaptor = ArgumentCaptor.forClass(APIResource.class);
        verify(apiResourceManager, times(5)).addAPIResource(resourceCaptor.capture(), eq(TENANT_DOMAIN));
        assertEquals(resourceCaptor.getAllValues().get(0).getType(), "BUSINESS");
        assertEquals(resourceCaptor.getAllValues().get(0).getScopes().size(), 2);
        assertEquals(resourceCaptor.getAllValues().get(1).getScopes().size(), 2);
        assertEquals(resourceCaptor.getAllValues().get(2).getScopes().size(), 2);
        assertEquals(resourceCaptor.getAllValues().get(2).getScopes().get(1).getName(),
                "notifications:events:write");
        assertEquals(resourceCaptor.getAllValues().get(3).getScopes().get(0).getName(),
                "notifications:events:poll");
        assertEquals(resourceCaptor.getAllValues().get(4).getScopes().get(0).getName(),
                "notifications:event-deliveries:complete");
    }

    @Test
    public void registerEventNotificationAPIsDoesNotDuplicateExistingResources() throws Exception {

        when(apiResourceManager.getAPIResourceByIdentifier(anyString(), eq(TENANT_DOMAIN)))
                .thenReturn(mock(APIResource.class));

        DPDPApiResourceProvisioningUtil.registerEventNotificationAPIs(TENANT_DOMAIN);

        verify(apiResourceManager, never()).addAPIResource(any(APIResource.class), anyString());
    }

    @Test
    public void authorizeEventNotificationAPIsAuthorizesAllResources() throws Exception {

        Scope topicScope = mockScope("notifications:topics:read");
        Scope subscriptionScope = mockScope("notifications:subscriptions:read");
        Scope eventScope = mockScope("notifications:events:read");
        Scope pollScope = mockScope("notifications:events:poll");
        Scope completionScope = mockScope("notifications:event-deliveries:complete");
        APIResource topicsResource = mockResource("event-topics", Arrays.asList(topicScope));
        APIResource subscriptionsResource = mockResource("event-subscriptions", Arrays.asList(subscriptionScope));
        APIResource eventsResource = mockResource("event-events", Arrays.asList(eventScope));
        APIResource pollResource = mockResource("event-poll", Arrays.asList(pollScope));
        APIResource completionResource = mockResource("event-completion", Arrays.asList(completionScope));
        when(apiResourceManager.getAPIResourceByIdentifier("/api/dpdp/event-notifications/v1/topics", TENANT_DOMAIN))
                .thenReturn(topicsResource);
        when(apiResourceManager.getAPIResourceByIdentifier("/api/dpdp/event-notifications/v1/subscriptions",
                TENANT_DOMAIN)).thenReturn(subscriptionsResource);
        when(apiResourceManager.getAPIResourceByIdentifier("/api/dpdp/event-notifications/v1/events", TENANT_DOMAIN))
                .thenReturn(eventsResource);
        when(apiResourceManager.getAPIResourceByIdentifier("/api/dpdp/event-notifications/v1/events/poll",
                TENANT_DOMAIN)).thenReturn(pollResource);
        when(apiResourceManager.getAPIResourceByIdentifier("/api/dpdp/event-notifications/v1/deliveries",
                TENANT_DOMAIN)).thenReturn(completionResource);

        List<String> scopes = DPDPApiResourceProvisioningUtil
                .authorizeEventNotificationAPIs(APPLICATION_ID, TENANT_DOMAIN);

        assertEquals(scopes, Arrays.asList("notifications:topics:read",
                "notifications:subscriptions:read", "notifications:events:read",
                "notifications:events:poll", "notifications:event-deliveries:complete"));
        verify(authorizedAPIManagementService, times(5)).addAuthorizedAPI(eq(APPLICATION_ID),
                any(AuthorizedAPI.class), eq(TENANT_DOMAIN));
    }

    // ----- Consent History -----

    @Test
    public void registerConsentHistoryApiSkipsWhenAlreadyRegistered() throws Exception {

        when(apiResourceManager.getAPIResourceByIdentifier(CONSENT_HISTORY_API_IDENTIFIER, TENANT_DOMAIN))
                .thenReturn(mock(APIResource.class));

        DPDPApiResourceProvisioningUtil.registerConsentHistoryApi(TENANT_DOMAIN);

        verify(apiResourceManager, never()).addAPIResource(any(APIResource.class), eq(TENANT_DOMAIN));
    }

    @Test
    public void registerConsentHistoryApiCreatesItWithAllFourScopes() throws Exception {

        when(apiResourceManager.getAPIResourceByIdentifier(CONSENT_HISTORY_API_IDENTIFIER, TENANT_DOMAIN))
                .thenReturn(null);

        DPDPApiResourceProvisioningUtil.registerConsentHistoryApi(TENANT_DOMAIN);

        ArgumentCaptor<APIResource> resourceCaptor = ArgumentCaptor.forClass(APIResource.class);
        verify(apiResourceManager).addAPIResource(resourceCaptor.capture(), eq(TENANT_DOMAIN));
        APIResource resource = resourceCaptor.getValue();
        assertEquals(resource.getIdentifier(), CONSENT_HISTORY_API_IDENTIFIER);
        assertEquals(resource.getType(), "BUSINESS");

        List<String> scopeNames = new ArrayList<>();
        for (Scope scope : resource.getScopes()) {
            scopeNames.add(scope.getName());
        }
        assertEqualsNoOrder(scopeNames.toArray(), new String[]{
                DPDPApiResourceProvisioningUtil.STATUS_HISTORY_VIEW_ANY,
                DPDPApiResourceProvisioningUtil.STATUS_HISTORY_VIEW_SELF,
                DPDPApiResourceProvisioningUtil.HISTORY_VIEW_ANY,
                DPDPApiResourceProvisioningUtil.HISTORY_VIEW_SELF
        });
    }

    @Test
    public void authorizeConsentHistoryApiAuthorizesAndReturnsScopeNamesWhenNotYetAuthorized() throws Exception {

        Scope anyScope = mockScope(DPDPApiResourceProvisioningUtil.STATUS_HISTORY_VIEW_ANY);
        Scope selfScope = mockScope(DPDPApiResourceProvisioningUtil.STATUS_HISTORY_VIEW_SELF);
        APIResource apiResource = mockResource(CONSENT_HISTORY_API_RESOURCE_ID, Arrays.asList(anyScope, selfScope));
        when(apiResourceManager.getAPIResourceByIdentifier(CONSENT_HISTORY_API_IDENTIFIER, TENANT_DOMAIN))
                .thenReturn(apiResource);
        when(authorizedAPIManagementService.getAuthorizedAPI(APPLICATION_ID, CONSENT_HISTORY_API_RESOURCE_ID,
                TENANT_DOMAIN)).thenReturn(null);

        List<String> scopeNames = DPDPApiResourceProvisioningUtil.authorizeConsentHistoryApi(APPLICATION_ID,
                TENANT_DOMAIN);

        assertEquals(scopeNames, Arrays.asList(DPDPApiResourceProvisioningUtil.STATUS_HISTORY_VIEW_ANY,
                DPDPApiResourceProvisioningUtil.STATUS_HISTORY_VIEW_SELF));
        verify(authorizedAPIManagementService).addAuthorizedAPI(eq(APPLICATION_ID), any(AuthorizedAPI.class),
                eq(TENANT_DOMAIN));
    }

    @Test
    public void authorizeConsentHistoryApiSkipsWhenAlreadyAuthorized() throws Exception {

        Scope anyScope = mockScope(DPDPApiResourceProvisioningUtil.STATUS_HISTORY_VIEW_ANY);
        APIResource apiResource = mockResource(CONSENT_HISTORY_API_RESOURCE_ID, Arrays.asList(anyScope));
        when(apiResourceManager.getAPIResourceByIdentifier(CONSENT_HISTORY_API_IDENTIFIER, TENANT_DOMAIN))
                .thenReturn(apiResource);

        AuthorizedAPI existingAuthorization = mock(AuthorizedAPI.class);
        when(existingAuthorization.getScopes()).thenReturn(Arrays.asList(anyScope));
        when(authorizedAPIManagementService.getAuthorizedAPI(APPLICATION_ID, CONSENT_HISTORY_API_RESOURCE_ID,
                TENANT_DOMAIN)).thenReturn(existingAuthorization);

        List<String> scopeNames = DPDPApiResourceProvisioningUtil.authorizeConsentHistoryApi(APPLICATION_ID,
                TENANT_DOMAIN);

        assertEquals(scopeNames, Arrays.asList(DPDPApiResourceProvisioningUtil.STATUS_HISTORY_VIEW_ANY));
        verify(authorizedAPIManagementService, never()).addAuthorizedAPI(eq(APPLICATION_ID), any(AuthorizedAPI.class),
                eq(TENANT_DOMAIN));
    }

    private static Scope mockScope(String name) {

        Scope scope = mock(Scope.class);
        when(scope.getName()).thenReturn(name);
        return scope;
    }

    private static APIResource mockResource(String id, List<Scope> scopes) {

        APIResource resource = mock(APIResource.class);
        when(resource.getId()).thenReturn(id);
        when(resource.getType()).thenReturn("BUSINESS");
        when(resource.getScopes()).thenReturn(scopes);
        return resource;
    }
}
