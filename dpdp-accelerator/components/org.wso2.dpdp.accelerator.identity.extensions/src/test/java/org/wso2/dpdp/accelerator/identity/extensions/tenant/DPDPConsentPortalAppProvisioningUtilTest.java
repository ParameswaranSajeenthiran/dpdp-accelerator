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
import org.wso2.carbon.identity.application.common.model.ServiceProvider;
import org.wso2.carbon.identity.application.mgt.ApplicationManagementService;
import org.wso2.carbon.identity.application.mgt.AuthorizedAPIManagementService;
import org.wso2.carbon.identity.oauth.OAuthAdminServiceImpl;
import org.wso2.carbon.identity.oauth.dto.OAuthConsumerAppDTO;
import org.wso2.carbon.identity.application.common.model.Scope;
import org.wso2.carbon.stratos.common.beans.TenantInfoBean;
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
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.expectThrows;

public class DPDPConsentPortalAppProvisioningUtilTest {

    private static final String TENANT_DOMAIN = "tenant-a.com";
    private static final String APPLICATION_ID = "app-1234";
    private static final String CLIENT_ID = "DPDP_CONSENT_PORTAL";

    @Mock
    private ApplicationManagementService applicationManagementService;

    @Mock
    private OAuthAdminServiceImpl oAuthAdminService;

    @Mock
    private AuthorizedAPIManagementService authorizedAPIManagementService;

    @Mock
    private APIResourceManager apiResourceManager;

    @BeforeMethod
    public void setUp() {

        MockitoAnnotations.openMocks(this);
        DPDPIdentityExtensionDataHolder.getInstance().setApplicationManagementService(applicationManagementService);
        DPDPIdentityExtensionDataHolder.getInstance().setOAuthAdminService(oAuthAdminService);
        DPDPIdentityExtensionDataHolder.getInstance()
                .setAuthorizedAPIManagementService(authorizedAPIManagementService);
        DPDPIdentityExtensionDataHolder.getInstance().setApiResourceManager(apiResourceManager);
    }

    @Test
    public void getApplicationIdReturnsTheResourceIdWhenAlreadyRegistered() throws Exception {

        ServiceProvider serviceProvider = new ServiceProvider();
        serviceProvider.setApplicationResourceId(APPLICATION_ID);
        when(applicationManagementService.getApplicationExcludingFileBasedSPs(
                DPDPConsentPortalAppProvisioningUtil.APPLICATION_NAME, TENANT_DOMAIN))
                .thenReturn(serviceProvider);

        assertEquals(DPDPConsentPortalAppProvisioningUtil.getApplicationId(TENANT_DOMAIN), APPLICATION_ID);
    }

    @Test
    public void getApplicationIdReturnsNullWhenNotRegistered() throws Exception {

        when(applicationManagementService.getApplicationExcludingFileBasedSPs(
                DPDPConsentPortalAppProvisioningUtil.APPLICATION_NAME, TENANT_DOMAIN))
                .thenReturn(null);

        assertNull(DPDPConsentPortalAppProvisioningUtil.getApplicationId(TENANT_DOMAIN));
    }

    @Test
    public void toRegexCallbackEscapesDotsAndAllowsOptionalTrailingSlash() {

        String result = DPDPConsentPortalAppProvisioningUtil
                .toRegexCallback("https://localhost:9443/t/tenant-a.com/consent-portal");

        assertEquals(result, "regexp=(https://localhost:9443/t/tenant-a\\.com/consent-portal/?)");
        // The specific regression this guards against: Pattern.quote()'s \Q...\E wrapping
        // is confirmed (by direct testing against a live tenant) to break this validator.
        assertFalse(result.contains("\\Q"));
        assertFalse(result.contains(","));
    }

    @Test
    public void registerOAuthApplicationSetsExpectedOAuthAppFields() throws Exception {

        String callbackUrl = DPDPConsentPortalAppProvisioningUtil.toRegexCallback(
                "https://localhost:9443/t/" + TENANT_DOMAIN + "/consent-portal");

        DPDPConsentPortalAppProvisioningUtil.registerOAuthApplication(TENANT_DOMAIN, callbackUrl, CLIENT_ID);

        ArgumentCaptor<OAuthConsumerAppDTO> dtoCaptor = ArgumentCaptor.forClass(OAuthConsumerAppDTO.class);
        verify(oAuthAdminService).registerOAuthApplicationData(dtoCaptor.capture());
        OAuthConsumerAppDTO dto = dtoCaptor.getValue();
        assertEquals(dto.getApplicationName(), DPDPConsentPortalAppProvisioningUtil.APPLICATION_NAME);
        assertEquals(dto.getOauthConsumerKey(), CLIENT_ID);
        assertEquals(dto.getCallbackUrl(), callbackUrl);
        assertTrue(dto.getPkceMandatory());
        // Without this, the app defaults to an opaque (UUID) access token, which
        // TokenIntrospectionClient cannot decode - it only ever parses a JWT's payload segment.
        assertEquals(dto.getTokenType(), "JWT");
        assertEquals(dto.getTokenBindingType(), "cookie");
        assertTrue(dto.isTokenBindingValidationEnabled());
        assertTrue(dto.isTokenRevocationWithIDPSessionTerminationEnabled());
        assertTrue(dto.getAllowedOrigins().isEmpty());
        assertEquals(dto.getAccessTokenClaims(), new String[]{"username"});
    }

    @Test
    public void createApplicationBuildsExpectedServiceProvider() throws Exception {

        TenantInfoBean tenantInfoBean = new TenantInfoBean();
        tenantInfoBean.setTenantDomain(TENANT_DOMAIN);
        tenantInfoBean.setAdmin("admin");

        when(applicationManagementService.createApplication(any(ServiceProvider.class), eq(TENANT_DOMAIN),
                eq("admin"))).thenReturn(APPLICATION_ID);

        String applicationId = DPDPConsentPortalAppProvisioningUtil.createApplication(tenantInfoBean, CLIENT_ID);

        assertEquals(applicationId, APPLICATION_ID);

        ArgumentCaptor<ServiceProvider> spCaptor = ArgumentCaptor.forClass(ServiceProvider.class);
        verify(applicationManagementService).createApplication(spCaptor.capture(), eq(TENANT_DOMAIN), eq("admin"));
        ServiceProvider serviceProvider = spCaptor.getValue();
        assertEquals(serviceProvider.getApplicationName(), DPDPConsentPortalAppProvisioningUtil.APPLICATION_NAME);
        assertEquals(serviceProvider.getInboundAuthenticationConfig().getInboundAuthenticationRequestConfigs()[0]
                .getInboundAuthKey(), CLIENT_ID);
        assertTrue(serviceProvider.getLocalAndOutBoundAuthenticationConfig().isSkipConsent());
        assertTrue(serviceProvider.getLocalAndOutBoundAuthenticationConfig().isSkipLogoutConsent());
        // Must be the full local claim URI, not the OIDC claim URI used for
        // registerOAuthApplication's access token claims - ClaimConfig here is local-dialect
        // (setLocalClaimDialect(true)), and WSO2 IS rejects "username" alone as an unknown local
        // claim ("Local claim username is not available in the server").
        assertTrue(serviceProvider.getClaimConfig().isLocalClaimDialect());
        assertEquals(serviceProvider.getClaimConfig().getClaimMappings()[0].getLocalClaim().getClaimUri(),
                "http://wso2.org/claims/username");
    }

    @Test
    public void authorizeConsentManagementAPIsAuthorizesAllThreeAndCollectsScopes() throws Exception {

        Scope consentScope = mockScope("internal_consent_mgt_consent_view");
        APIResource consentsResource = mockResource("res-consents", "/api/identity/consent-mgt/v2.0/consents",
                Arrays.asList(consentScope));
        Scope purposeScope = mockScope("internal_consent_mgt_purpose_view");
        APIResource purposesResource = mockResource("res-purposes", "/api/identity/consent-mgt/v2.0/purposes",
                Arrays.asList(purposeScope));
        Scope elementScope = mockScope("internal_consent_mgt_element_view");
        APIResource elementsResource = mockResource("res-elements", "/api/identity/consent-mgt/v2.0/elements",
                Arrays.asList(elementScope));

        when(apiResourceManager.getAPIResourceByIdentifier("/api/identity/consent-mgt/v2.0/consents", TENANT_DOMAIN))
                .thenReturn(consentsResource);
        when(apiResourceManager.getAPIResourceByIdentifier("/api/identity/consent-mgt/v2.0/purposes", TENANT_DOMAIN))
                .thenReturn(purposesResource);
        when(apiResourceManager.getAPIResourceByIdentifier("/api/identity/consent-mgt/v2.0/elements", TENANT_DOMAIN))
                .thenReturn(elementsResource);

        List<String> scopes = DPDPConsentPortalAppProvisioningUtil
                .authorizeConsentManagementAPIs(APPLICATION_ID, TENANT_DOMAIN);

        assertEquals(scopes, Arrays.asList("internal_consent_mgt_consent_view", "internal_consent_mgt_purpose_view",
                "internal_consent_mgt_element_view"));
        verify(authorizedAPIManagementService, times(3)).addAuthorizedAPI(eq(APPLICATION_ID), any(AuthorizedAPI.class),
                eq(TENANT_DOMAIN));
    }

    @Test
    public void authorizeConsentManagementAPIsSkipsApisAlreadyAuthorized() throws Exception {

        Scope consentScope = mockScope("internal_consent_mgt_consent_view");
        APIResource consentsResource = mockResource("res-consents", "/api/identity/consent-mgt/v2.0/consents",
                Arrays.asList(consentScope));
        Scope purposeScope = mockScope("internal_consent_mgt_purpose_view");
        APIResource purposesResource = mockResource("res-purposes", "/api/identity/consent-mgt/v2.0/purposes",
                Arrays.asList(purposeScope));
        Scope elementScope = mockScope("internal_consent_mgt_element_view");
        APIResource elementsResource = mockResource("res-elements", "/api/identity/consent-mgt/v2.0/elements",
                Arrays.asList(elementScope));

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

        List<String> scopes = DPDPConsentPortalAppProvisioningUtil
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
                () -> DPDPConsentPortalAppProvisioningUtil.authorizeConsentManagementAPIs(APPLICATION_ID,
                        TENANT_DOMAIN));

        verify(authorizedAPIManagementService, never()).addAuthorizedAPI(anyString(), any(AuthorizedAPI.class),
                anyString());
    }

    @Test
    public void authorizeEventNotificationAPIsAuthorizesAllResources() throws Exception {
        Scope topicScope = mockScope("notifications:topics:read");
        Scope subscriptionScope = mockScope("notifications:subscriptions:read");
        Scope eventScope = mockScope("notifications:events:read");
        Scope pollScope = mockScope("notifications:events:poll");
        Scope completionScope = mockScope("notifications:event-deliveries:complete");
        APIResource topicsResource = mockResource("event-topics", "/api/dpdp/event-notifications/v1/topics",
                Arrays.asList(topicScope));
        APIResource subscriptionsResource = mockResource("event-subscriptions", "/api/dpdp/event-notifications/v1/subscriptions",
                Arrays.asList(subscriptionScope));
        APIResource eventsResource = mockResource("event-events", "/api/dpdp/event-notifications/v1/events",
                Arrays.asList(eventScope));
        APIResource pollResource = mockResource("event-poll", "/api/dpdp/event-notifications/v1/events/poll",
                Arrays.asList(pollScope));
        APIResource completionResource = mockResource("event-completion", "/api/dpdp/event-notifications/v1/deliveries",
                Arrays.asList(completionScope));
        when(apiResourceManager.getAPIResourceByIdentifier("/api/dpdp/event-notifications/v1/topics", TENANT_DOMAIN))
                .thenReturn(topicsResource);
        when(apiResourceManager.getAPIResourceByIdentifier("/api/dpdp/event-notifications/v1/subscriptions", TENANT_DOMAIN))
                .thenReturn(subscriptionsResource);
        when(apiResourceManager.getAPIResourceByIdentifier("/api/dpdp/event-notifications/v1/events", TENANT_DOMAIN))
                .thenReturn(eventsResource);
        when(apiResourceManager.getAPIResourceByIdentifier("/api/dpdp/event-notifications/v1/events/poll", TENANT_DOMAIN))
                .thenReturn(pollResource);
        when(apiResourceManager.getAPIResourceByIdentifier("/api/dpdp/event-notifications/v1/deliveries", TENANT_DOMAIN))
                .thenReturn(completionResource);

        List<String> scopes = DPDPConsentPortalAppProvisioningUtil
                .authorizeEventNotificationAPIs(APPLICATION_ID, TENANT_DOMAIN);

        assertEquals(scopes, Arrays.asList("notifications:topics:read",
                "notifications:subscriptions:read", "notifications:events:read",
                "notifications:events:poll", "notifications:event-deliveries:complete"));
        verify(authorizedAPIManagementService, times(5)).addAuthorizedAPI(eq(APPLICATION_ID),
                any(AuthorizedAPI.class), eq(TENANT_DOMAIN));
    }

    @Test
    public void registerEventNotificationAPIsRegistersMissingResourcesWithAllScopes() throws Exception {

        when(apiResourceManager.getAPIResourceByIdentifier(anyString(), eq(TENANT_DOMAIN))).thenReturn(null);

        DPDPConsentPortalAppProvisioningUtil.registerEventNotificationAPIs(TENANT_DOMAIN);

        ArgumentCaptor<APIResource> resourceCaptor = ArgumentCaptor.forClass(APIResource.class);
        verify(apiResourceManager, times(5)).addAPIResource(resourceCaptor.capture(), eq(TENANT_DOMAIN));
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

        DPDPConsentPortalAppProvisioningUtil.registerEventNotificationAPIs(TENANT_DOMAIN);

        verify(apiResourceManager, never()).addAPIResource(any(APIResource.class), anyString());
    }

    private static Scope mockScope(String name) {

        Scope scope = mock(Scope.class);
        when(scope.getName()).thenReturn(name);
        return scope;
    }

    private static APIResource mockResource(String id, String identifier, List<Scope> scopes) {

        APIResource resource = mock(APIResource.class);
        when(resource.getId()).thenReturn(id);
        when(resource.getIdentifier()).thenReturn(identifier);
        when(resource.getType()).thenReturn("TENANT");
        when(resource.getScopes()).thenReturn(scopes);
        return resource;
    }
}
