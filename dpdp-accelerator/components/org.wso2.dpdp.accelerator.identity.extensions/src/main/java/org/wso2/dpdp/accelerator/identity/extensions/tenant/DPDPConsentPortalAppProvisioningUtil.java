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
import org.wso2.carbon.identity.api.resource.mgt.constant.APIResourceManagementConstants;
import org.wso2.carbon.identity.application.common.IdentityApplicationManagementException;
import org.wso2.carbon.identity.application.common.model.APIResource;
import org.wso2.carbon.identity.application.common.model.AssociatedRolesConfig;
import org.wso2.carbon.identity.application.common.model.AuthorizedAPI;
import org.wso2.carbon.identity.application.common.model.Claim;
import org.wso2.carbon.identity.application.common.model.ClaimConfig;
import org.wso2.carbon.identity.application.common.model.ClaimMapping;
import org.wso2.carbon.identity.application.common.model.InboundAuthenticationConfig;
import org.wso2.carbon.identity.application.common.model.InboundAuthenticationRequestConfig;
import org.wso2.carbon.identity.application.common.model.LocalAndOutboundAuthenticationConfig;
import org.wso2.carbon.identity.application.common.model.RoleV2;
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
 * Registers the DPDP Consent Portal OAuth2 application and authorizes it for the consent-mgt
 * and Event Notification APIs. Role creation is a separate concern - see
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
    private static final String[] EVENT_NOTIFICATION_API_IDENTIFIERS = {
            "/api/dpdp/event-notifications/v1/topics",
            "/api/dpdp/event-notifications/v1/subscriptions",
            "/api/dpdp/event-notifications/v1/events",
            "/api/dpdp/event-notifications/v1/events/poll",
            "/api/dpdp/event-notifications/v1/deliveries"
    };
    private static final String[][] EVENT_NOTIFICATION_API_SCOPES = {
            {"notifications:topics:read", "notifications:topics:write"},
            {"notifications:subscriptions:read", "notifications:subscriptions:write"},
            {"notifications:events:read", "notifications:events:write"},
            {"notifications:events:poll"},
            {"notifications:event-deliveries:complete"}
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

        return authorizeAPIs(applicationId, tenantDomain, CONSENT_MGT_API_IDENTIFIERS);
    }

    /**
     * Authorizes the Event Notification API resources for the tenant's portal application.
     * The operation is idempotent and preserves any existing authorization.
     */
    public static List<String> authorizeEventNotificationAPIs(String applicationId, String tenantDomain)
            throws Exception {

        return authorizeAPIs(applicationId, tenantDomain, EVENT_NOTIFICATION_API_IDENTIFIERS);
    }

    /**
     * Registers the Event Notification API resources for a tenant when they are not already
     * present. This is intentionally idempotent because tenant provisioning can be retried.
     */
    public static void registerEventNotificationAPIs(String tenantDomain) throws Exception {

        APIResourceManager apiResourceManager = DPDPIdentityExtensionDataHolder.getInstance().getApiResourceManager();
        registerAPIResources(apiResourceManager, tenantDomain, EVENT_NOTIFICATION_API_IDENTIFIERS,
                EVENT_NOTIFICATION_API_SCOPES);

    }

    private static void registerAPIResources(APIResourceManager apiResourceManager, String tenantDomain,
            String[] identifiers, String[][] scopesByIdentifier) throws Exception {

        for (int i = 0; i < identifiers.length; i++) {
            String identifier = identifiers[i];
            if (apiResourceManager.getAPIResourceByIdentifier(identifier, tenantDomain) != null) {
                continue;
            }
            List<Scope> scopes = new ArrayList<>();
            for (String scopeName : scopesByIdentifier[i]) {
                scopes.add(new Scope(null, scopeName, scopeName, "Event Notification API scope: " + scopeName));
            }
            APIResource resource = new APIResource.APIResourceBuilder()
                    .name("DPDP Event Notification API " + resourceName(identifier))
                    .identifier(identifier)
                    .type(APIResourceManagementConstants.APIResourceTypes.TENANT)
                    .description("DPDP Event Notification " + resourceName(identifier) + " API")
                    .requiresAuthorization(true)
                    .scopes(scopes)
                    .build();
            apiResourceManager.addAPIResource(resource, tenantDomain);
        }
    }

    private static String resourceName(String identifier) {

        String[] parts = identifier.split("/");
        return parts[parts.length - 1];
    }

    private static List<String> authorizeAPIs(String applicationId, String tenantDomain, String[] identifiers)
            throws Exception {

        APIResourceManager apiResourceManager = DPDPIdentityExtensionDataHolder.getInstance().getApiResourceManager();
        AuthorizedAPIManagementService authorizedAPIManagementService = DPDPIdentityExtensionDataHolder.getInstance()
                .getAuthorizedAPIManagementService();

        List<String> authorizedScopeNames = new ArrayList<>();
        for (String identifier : identifiers) {
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

    private static final String ASSOCIATED_ROLES_ALLOWED_AUDIENCE = "ORGANIZATION";

    /**
     * Configures the application to consume organization-audience roles by setting its Role Audience
     * to Organization and assigning the specified roles.
     */
    public static void associateOrganizationRoles(String tenantDomain, String username, List<RoleV2> roles)
            throws IdentityApplicationManagementException {

        ServiceProvider serviceProvider = DPDPIdentityExtensionDataHolder.getInstance()
                .getApplicationManagementService().getApplicationExcludingFileBasedSPs(APPLICATION_NAME,
                        tenantDomain);

        AssociatedRolesConfig associatedRolesConfig = new AssociatedRolesConfig();
        associatedRolesConfig.setAllowedAudience(ASSOCIATED_ROLES_ALLOWED_AUDIENCE);
        associatedRolesConfig.setRoles(roles.toArray(new RoleV2[0]));
        serviceProvider.setAssociatedRolesConfig(associatedRolesConfig);

        DPDPIdentityExtensionDataHolder.getInstance().getApplicationManagementService()
                .updateApplication(serviceProvider, tenantDomain, username);
        LOG.debug("Set the Role Audience to organization and associated " + roles.size()
                + " role(s) for application: " + APPLICATION_NAME + ", tenant: " + tenantDomain);
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
