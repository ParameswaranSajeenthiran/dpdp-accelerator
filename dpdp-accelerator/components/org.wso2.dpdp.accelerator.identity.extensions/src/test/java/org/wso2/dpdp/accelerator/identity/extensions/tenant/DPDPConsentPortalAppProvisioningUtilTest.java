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
    public void applicationExistsReturnsTrueWhenAlreadyRegistered() throws Exception {

        when(applicationManagementService.getApplicationExcludingFileBasedSPs(
                DPDPConsentPortalAppProvisioningUtil.APPLICATION_NAME, TENANT_DOMAIN))
                .thenReturn(new ServiceProvider());

        assertTrue(DPDPConsentPortalAppProvisioningUtil.applicationExists(TENANT_DOMAIN));
    }

    @Test
    public void applicationExistsReturnsFalseWhenNotRegistered() throws Exception {

        when(applicationManagementService.getApplicationExcludingFileBasedSPs(
                DPDPConsentPortalAppProvisioningUtil.APPLICATION_NAME, TENANT_DOMAIN))
                .thenReturn(null);

        assertFalse(DPDPConsentPortalAppProvisioningUtil.applicationExists(TENANT_DOMAIN));
    }

    // provisionApplication() itself is not exercised here: it computes the callback URL via
    // IdentityUtil.getServerURL(...), which needs a live OSGi-wired Carbon core component and
    // NPEs outside a running server. registerOAuthApplication/createApplication are the two
    // pieces with real logic worth testing, and neither touches that static call.

    @Test
    public void registerOAuthApplicationSetsExpectedOAuthAppFields() throws Exception {

        String callbackUrl = "https://localhost:9443/t/" + TENANT_DOMAIN + "/consent-portal,"
                + "https://localhost:9443/t/" + TENANT_DOMAIN + "/consent-portal/";

        DPDPConsentPortalAppProvisioningUtil.registerOAuthApplication(TENANT_DOMAIN, callbackUrl, CLIENT_ID);

        ArgumentCaptor<OAuthConsumerAppDTO> dtoCaptor = ArgumentCaptor.forClass(OAuthConsumerAppDTO.class);
        verify(oAuthAdminService).registerOAuthApplicationData(dtoCaptor.capture());
        OAuthConsumerAppDTO dto = dtoCaptor.getValue();
        assertEquals(dto.getApplicationName(), DPDPConsentPortalAppProvisioningUtil.APPLICATION_NAME);
        assertEquals(dto.getOauthConsumerKey(), CLIENT_ID);
        assertEquals(dto.getCallbackUrl(), callbackUrl);
        assertTrue(dto.getPkceMandatory());
        assertEquals(dto.getTokenBindingType(), "cookie");
        assertTrue(dto.isTokenBindingValidationEnabled());
        assertTrue(dto.isTokenRevocationWithIDPSessionTerminationEnabled());
        assertTrue(dto.getAllowedOrigins().isEmpty());
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
    public void authorizeConsentManagementAPIsThrowsWhenAResourceIsMissing() throws Exception {

        when(apiResourceManager.getAPIResourceByIdentifier(anyString(), eq(TENANT_DOMAIN))).thenReturn(null);

        expectThrows(IdentityApplicationManagementException.class,
                () -> DPDPConsentPortalAppProvisioningUtil.authorizeConsentManagementAPIs(APPLICATION_ID,
                        TENANT_DOMAIN));

        verify(authorizedAPIManagementService, never()).addAuthorizedAPI(anyString(), any(AuthorizedAPI.class),
                anyString());
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
