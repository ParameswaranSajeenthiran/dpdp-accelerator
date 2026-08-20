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
import org.wso2.carbon.identity.api.resource.mgt.APIResourceManager;
import org.wso2.carbon.identity.application.common.IdentityApplicationManagementException;
import org.wso2.carbon.identity.application.common.model.APIResource;
import org.wso2.carbon.identity.application.common.model.AuthorizedAPI;
import org.wso2.carbon.identity.application.common.model.Claim;
import org.wso2.carbon.identity.application.common.model.ClaimConfig;
import org.wso2.carbon.identity.application.common.model.ClaimMapping;
import org.wso2.carbon.identity.application.common.model.InboundAuthenticationConfig;
import org.wso2.carbon.identity.application.common.model.InboundAuthenticationRequestConfig;
import org.wso2.carbon.identity.application.common.model.LocalAndOutboundAuthenticationConfig;
import org.wso2.carbon.identity.application.common.model.Scope;
import org.wso2.carbon.identity.application.common.model.ServiceProvider;
import org.wso2.carbon.identity.application.mgt.ApplicationManagementService;
import org.wso2.carbon.identity.application.mgt.AuthorizedAPIManagementService;
import org.wso2.carbon.identity.core.util.IdentityUtil;
import org.wso2.carbon.identity.oauth.IdentityOAuthAdminException;
import org.wso2.carbon.identity.oauth.OAuthUtil;
import org.wso2.carbon.identity.oauth.dto.OAuthConsumerAppDTO;
import org.wso2.carbon.stratos.common.beans.TenantInfoBean;
import org.wso2.carbon.utils.multitenancy.MultitenantConstants;
import org.wso2.dpdp.accelerator.identity.extensions.internal.DPDPIdentityExtensionDataHolder;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Registers the DPDP Consent Portal OAuth2 application and authorizes it for the three
 * consent-mgt APIs. Role creation is a separate concern - see
 * {@link DPDPConsentPortalRoleProvisioningUtil}. Every method here assumes it is already
 * running inside the correct tenant's {@code PrivilegedCarbonContext} flow; that setup lives in
 * the caller ({@link DPDPIdentityExtensionTenantMgtListener}), not here, so this class stays
 * plain service calls with no static Carbon-context handling to make it directly unit-testable.
 */
public final class DPDPConsentPortalAppProvisioningUtil {

    private static final Log LOG = LogFactory.getLog(DPDPConsentPortalAppProvisioningUtil.class);
    static final String APPLICATION_NAME = "DPDP Consent Portal";
    private static final String USERNAME_CLAIM_URI = "http://wso2.org/claims/username";
    private static final String AUTHORIZED_API_POLICY = "RBAC";
    private static final String[] GRANT_TYPES = {"authorization_code", "refresh_token"};
    private static final String[] CONSENT_MGT_API_IDENTIFIERS = {
            "/api/identity/consent-mgt/v2.0/consents",
            "/api/identity/consent-mgt/v2.0/purposes",
            "/api/identity/consent-mgt/v2.0/elements"
    };

    private DPDPConsentPortalAppProvisioningUtil() {

    }

    /**
     * @return the existing application's resource ID, or {@code null} if it has not been
     * created yet for this tenant.
     */
    public static String getApplicationId(String tenantDomain) throws IdentityApplicationManagementException {

        ServiceProvider serviceProvider = DPDPIdentityExtensionDataHolder.getInstance()
                .getApplicationManagementService().getApplicationExcludingFileBasedSPs(APPLICATION_NAME,
                        tenantDomain);
        return serviceProvider == null ? null : serviceProvider.getApplicationResourceId();
    }

    public static String provisionApplication(TenantInfoBean tenantInfoBean) throws IdentityOAuthAdminException,
            IdentityApplicationManagementException {

        String tenantDomain = tenantInfoBean.getTenantDomain();
        String clientId = DPDPIdentityExtensionDataHolder.getInstance().getConfigurationService()
                .getConsentPortalClientId();
        registerOAuthApplication(tenantDomain, buildCallbackUrl(tenantDomain), clientId);
        return createApplication(tenantInfoBean, clientId);
    }

    /**
     * Authorizes whichever of the three consent-mgt APIs aren't already authorized, and returns
     * all their scope names. Checks first because re-authorizing an already-authorized API throws.
     */
    public static List<String> authorizeConsentManagementAPIs(String applicationId, String tenantDomain)
            throws Exception {

        APIResourceManager apiResourceManager = DPDPIdentityExtensionDataHolder.getInstance().getApiResourceManager();
        AuthorizedAPIManagementService authorizedAPIManagementService = DPDPIdentityExtensionDataHolder.getInstance()
                .getAuthorizedAPIManagementService();

        List<String> authorizedScopeNames = new ArrayList<>();
        for (String identifier : CONSENT_MGT_API_IDENTIFIERS) {
            APIResource apiResource = apiResourceManager.getAPIResourceByIdentifier(identifier, tenantDomain);
            if (apiResource == null) {
                throw new IdentityApplicationManagementException("API resource not registered: " + identifier
                        + ". Confirm [consent_mgt] enable_v2_api = true in this tenant's deployment.");
            }

            AuthorizedAPI existingAuthorization = authorizedAPIManagementService.getAuthorizedAPI(applicationId,
                    apiResource.getId(), tenantDomain);
            List<Scope> scopes;
            if (existingAuthorization != null) {
                scopes = existingAuthorization.getScopes();
                LOG.debug("API '" + identifier + "' is already authorized for application: " + applicationId);
            } else {
                scopes = apiResource.getScopes();
                AuthorizedAPI authorizedAPI = new AuthorizedAPI(applicationId, apiResource.getId(),
                        AUTHORIZED_API_POLICY, scopes, apiResource.getType());
                authorizedAPIManagementService.addAuthorizedAPI(applicationId, authorizedAPI, tenantDomain);
                LOG.debug("Authorized API '" + identifier + "' (" + scopes.size() + " scope(s)) for application: "
                        + applicationId);
            }

            for (Scope scope : scopes) {
                authorizedScopeNames.add(scope.getName());
            }
        }
        return authorizedScopeNames;
    }

    static void registerOAuthApplication(String tenantDomain, String callbackUrl, String clientId)
            throws IdentityOAuthAdminException {

        LOG.debug("Registering the OAuth2 application '" + clientId + "' for tenant: " + tenantDomain);
        OAuthConsumerAppDTO dto = new OAuthConsumerAppDTO();
        dto.setApplicationName(APPLICATION_NAME);
        dto.setOauthConsumerKey(clientId);
        dto.setOauthConsumerSecret(OAuthUtil.getRandomNumber());
        dto.setCallbackUrl(callbackUrl);
        dto.setGrantTypes(String.join(" ", GRANT_TYPES));
        // Same-origin only, matching Console/My Account (confirmed empty on the live server) -
        // must be set explicitly, not left null, or later code dereferencing it NPEs.
        dto.setAllowedOrigins(Collections.emptyList());
        dto.setBypassClientCredentials(true);
        dto.setPkceMandatory(true);
        dto.setTokenBindingType("cookie");
        dto.setTokenBindingValidationEnabled(true);
        dto.setTokenRevocationWithIDPSessionTerminationEnabled(true);

        DPDPIdentityExtensionDataHolder.getInstance().getOAuthAdminService().registerOAuthApplicationData(dto);
    }

    private static String buildCallbackUrl(String tenantDomain) {

        String path = MultitenantConstants.SUPER_TENANT_DOMAIN_NAME.equals(tenantDomain)
                ? "/consent-portal"
                : "/t/" + tenantDomain + "/consent-portal";
        return toRegexCallback(IdentityUtil.getServerURL(path, true, false));
    }

    /**
     * Wraps a portal URL as a "regexp=(...)" callback matching it with or without a
     * trailing slash.
     */
    static String toRegexCallback(String portalUrl) {

        String escapedUrl = portalUrl.replace(".", "\\.");
        return "regexp=(" + escapedUrl + "/?)";
    }

    static String createApplication(TenantInfoBean tenantInfoBean, String clientId)
            throws IdentityApplicationManagementException {

        LOG.debug("Creating the DPDP Consent Portal service provider for tenant: "
                + tenantInfoBean.getTenantDomain());
        ServiceProvider serviceProvider = new ServiceProvider();
        serviceProvider.setApplicationName(APPLICATION_NAME);
        serviceProvider.setDescription("Self-service and administrative portal for DPDP consent management.");

        InboundAuthenticationRequestConfig requestConfig = new InboundAuthenticationRequestConfig();
        requestConfig.setInboundAuthKey(clientId);
        requestConfig.setInboundAuthType("oauth2");
        requestConfig.setInboundConfigType("standardAPP");
        InboundAuthenticationConfig inboundAuthenticationConfig = new InboundAuthenticationConfig();
        inboundAuthenticationConfig.setInboundAuthenticationRequestConfigs(
                new InboundAuthenticationRequestConfig[]{requestConfig});
        serviceProvider.setInboundAuthenticationConfig(inboundAuthenticationConfig);

        LocalAndOutboundAuthenticationConfig localAndOutboundAuthenticationConfig =
                new LocalAndOutboundAuthenticationConfig();
        localAndOutboundAuthenticationConfig.setSkipConsent(true);
        localAndOutboundAuthenticationConfig.setSkipLogoutConsent(true);
        serviceProvider.setLocalAndOutBoundAuthenticationConfig(localAndOutboundAuthenticationConfig);

        Claim usernameClaim = new Claim();
        usernameClaim.setClaimUri(USERNAME_CLAIM_URI);
        ClaimMapping usernameClaimMapping = new ClaimMapping();
        usernameClaimMapping.setRequested(true);
        usernameClaimMapping.setLocalClaim(usernameClaim);
        usernameClaimMapping.setRemoteClaim(usernameClaim);
        ClaimConfig claimConfig = new ClaimConfig();
        claimConfig.setClaimMappings(new ClaimMapping[]{usernameClaimMapping});
        claimConfig.setLocalClaimDialect(true);
        serviceProvider.setClaimConfig(claimConfig);

        return DPDPIdentityExtensionDataHolder.getInstance().getApplicationManagementService()
                .createApplication(serviceProvider, tenantInfoBean.getTenantDomain(), tenantInfoBean.getAdmin());
    }
}
