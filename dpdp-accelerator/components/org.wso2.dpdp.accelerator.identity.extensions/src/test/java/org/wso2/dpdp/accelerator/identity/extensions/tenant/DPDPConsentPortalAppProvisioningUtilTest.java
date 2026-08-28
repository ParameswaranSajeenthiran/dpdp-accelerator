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
import org.wso2.carbon.identity.application.common.model.AssociatedRolesConfig;
import org.wso2.carbon.identity.application.common.model.RoleV2;
import org.wso2.carbon.identity.application.common.model.ServiceProvider;
import org.wso2.carbon.identity.application.mgt.ApplicationManagementService;
import org.wso2.carbon.identity.oauth.OAuthAdminServiceImpl;
import org.wso2.carbon.identity.oauth.dto.OAuthConsumerAppDTO;
import org.wso2.carbon.stratos.common.beans.TenantInfoBean;
import org.wso2.dpdp.accelerator.identity.extensions.internal.DPDPIdentityExtensionDataHolder;

import java.util.Arrays;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertTrue;

public class DPDPConsentPortalAppProvisioningUtilTest {

    private static final String TENANT_DOMAIN = "tenant-a.com";
    private static final String APPLICATION_ID = "app-1234";
    private static final String CLIENT_ID = "DPDP_CONSENT_PORTAL";

    @Mock
    private ApplicationManagementService applicationManagementService;

    @Mock
    private OAuthAdminServiceImpl oAuthAdminService;

    @BeforeMethod
    public void setUp() {

        MockitoAnnotations.openMocks(this);
        DPDPIdentityExtensionDataHolder.getInstance().setApplicationManagementService(applicationManagementService);
        DPDPIdentityExtensionDataHolder.getInstance().setOAuthAdminService(oAuthAdminService);
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
        // Opaque access token - TokenIntrospectionClient resolves the caller from Carbon's own
        // PrivilegedCarbonContext rather than decoding or introspecting the token itself, so
        // there's no need for a JWT-embedded "username" Access Token Attribute anymore either.
        assertEquals(dto.getTokenType(), "Default");
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
        // Must be the full local claim URI, not the OIDC claim URI used for
        // registerOAuthApplication's access token claims - ClaimConfig here is local-dialect
        // (setLocalClaimDialect(true)), and WSO2 IS rejects "username" alone as an unknown local
        // claim ("Local claim username is not available in the server").
        assertTrue(serviceProvider.getClaimConfig().isLocalClaimDialect());
        assertEquals(serviceProvider.getClaimConfig().getClaimMappings()[0].getLocalClaim().getClaimUri(),
                "http://wso2.org/claims/username");
    }

    @Test
    public void associateOrganizationRolesSetsOrganizationAudienceAndAttachesTheGivenRoles() throws Exception {

        ServiceProvider serviceProvider = new ServiceProvider();
        when(applicationManagementService.getApplicationExcludingFileBasedSPs(
                DPDPConsentPortalAppProvisioningUtil.APPLICATION_NAME, TENANT_DOMAIN)).thenReturn(serviceProvider);

        List<RoleV2> roles = Arrays.asList(new RoleV2("role-admin-1234", "dpdp-consent-admin"),
                new RoleV2("role-user-1234", "dpdp-consent-user"));

        DPDPConsentPortalAppProvisioningUtil.associateOrganizationRoles(TENANT_DOMAIN, "admin", roles);

        ArgumentCaptor<ServiceProvider> spCaptor = ArgumentCaptor.forClass(ServiceProvider.class);
        verify(applicationManagementService).updateApplication(spCaptor.capture(), eq(TENANT_DOMAIN), eq("admin"));
        AssociatedRolesConfig associatedRolesConfig = spCaptor.getValue().getAssociatedRolesConfig();
        assertEquals(associatedRolesConfig.getAllowedAudience(), "ORGANIZATION");
        assertEquals(associatedRolesConfig.getRoles().length, 2);
        assertEquals(associatedRolesConfig.getRoles()[0].getName(), "dpdp-consent-admin");
        assertEquals(associatedRolesConfig.getRoles()[1].getName(), "dpdp-consent-user");
    }
}
