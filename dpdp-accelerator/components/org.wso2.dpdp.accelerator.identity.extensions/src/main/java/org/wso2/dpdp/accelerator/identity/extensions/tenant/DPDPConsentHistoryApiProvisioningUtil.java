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
import org.wso2.carbon.identity.application.common.model.APIResource;
import org.wso2.carbon.identity.application.common.model.AuthorizedAPI;
import org.wso2.carbon.identity.application.common.model.Scope;
import org.wso2.carbon.identity.application.mgt.AuthorizedAPIManagementService;
import org.wso2.dpdp.accelerator.identity.extensions.internal.DPDPIdentityExtensionDataHolder;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Registers this accelerator's own {@code /api/dpdp/consent-mgt/v1} API resource (unlike the
 * three IS-native consent-mgt v2 APIs in {@link DPDPConsentPortalAppProvisioningUtil}, which
 * already exist and only need authorizing - nothing pre-registers ours) and authorizes the DPDP
 * Consent Portal application for it. {@code BUSINESS} is the tenant-scoped API resource type -
 * {@code SYSTEM} would force this API resource to be shared across every tenant instead.
 */
public final class DPDPConsentHistoryApiProvisioningUtil {

    private static final Log LOG = LogFactory.getLog(DPDPConsentHistoryApiProvisioningUtil.class);

    private static final String API_IDENTIFIER = "/api/dpdp/consent-mgt/v1";
    private static final String API_NAME = "DPDP Consent History";
    private static final String API_TYPE = "BUSINESS";
    private static final String AUTHORIZED_API_POLICY = "RBAC";

    public static final String STATUS_HISTORY_VIEW_ANY = "consent:status-history:view:any";
    public static final String STATUS_HISTORY_VIEW_SELF = "consent:status-history:view:self";
    public static final String HISTORY_VIEW_ANY = "consent:history:view:any";
    public static final String HISTORY_VIEW_SELF = "consent:history:view:self";

    private DPDPConsentHistoryApiProvisioningUtil() {

    }

    /**
     * Registers the API resource with its 4 scopes if it doesn't already exist for this tenant.
     */
    public static void registerApiResource(String tenantDomain) throws Exception {

        APIResourceManager apiResourceManager = DPDPIdentityExtensionDataHolder.getInstance().getApiResourceManager();
        if (apiResourceManager.getAPIResourceByIdentifier(API_IDENTIFIER, tenantDomain) != null) {
            LOG.debug("API resource '" + API_IDENTIFIER + "' already exists for tenant: " + tenantDomain);
            return;
        }

        List<Scope> scopes = Arrays.asList(
                new Scope(null, STATUS_HISTORY_VIEW_ANY, "View any consent's status-audit history",
                        "Read access to any consent's status-audit trail."),
                new Scope(null, STATUS_HISTORY_VIEW_SELF, "View your own consent's status-audit history",
                        "Read access to your own consent's status-audit trail."),
                new Scope(null, HISTORY_VIEW_ANY, "View any consent's full snapshot history",
                        "Read access to any consent's full pre/post-mutation snapshot history."),
                new Scope(null, HISTORY_VIEW_SELF, "View your own consent's full snapshot history",
                        "Read access to your own consent's full pre/post-mutation snapshot history."));

        APIResource apiResource = new APIResource.APIResourceBuilder()
                .name(API_NAME)
                .identifier(API_IDENTIFIER)
                .description("Read APIs for DPDP consent status-audit and full-snapshot history.")
                .type(API_TYPE)
                .requiresAuthorization(true)
                .scopes(scopes)
                .build();
        apiResourceManager.addAPIResource(apiResource, tenantDomain);
        LOG.debug("Registered API resource '" + API_IDENTIFIER + "' for tenant: " + tenantDomain);
    }

    /**
     * Authorizes the application for this API resource if not already authorized, and returns
     * its scope names - same shape as
     * {@link DPDPConsentPortalAppProvisioningUtil#authorizeConsentManagementAPIs}, kept separate
     * since it targets a single, accelerator-owned API resource rather than iterating IS-native
     * ones.
     */
    public static List<String> authorizeApiForApplication(String applicationId, String tenantDomain)
            throws Exception {

        APIResourceManager apiResourceManager = DPDPIdentityExtensionDataHolder.getInstance().getApiResourceManager();
        AuthorizedAPIManagementService authorizedAPIManagementService = DPDPIdentityExtensionDataHolder.getInstance()
                .getAuthorizedAPIManagementService();

        APIResource apiResource = apiResourceManager.getAPIResourceByIdentifier(API_IDENTIFIER, tenantDomain);
        AuthorizedAPI existingAuthorization = authorizedAPIManagementService.getAuthorizedAPI(applicationId,
                apiResource.getId(), tenantDomain);

        List<Scope> scopes;
        if (existingAuthorization != null) {
            scopes = existingAuthorization.getScopes();
            LOG.debug("API '" + API_IDENTIFIER + "' is already authorized for application: " + applicationId);
        } else {
            scopes = apiResource.getScopes();
            AuthorizedAPI authorizedAPI = new AuthorizedAPI(applicationId, apiResource.getId(), AUTHORIZED_API_POLICY,
                    scopes, apiResource.getType());
            authorizedAPIManagementService.addAuthorizedAPI(applicationId, authorizedAPI, tenantDomain);
            LOG.debug("Authorized API '" + API_IDENTIFIER + "' (" + scopes.size() + " scope(s)) for application: "
                    + applicationId);
        }

        List<String> scopeNames = new ArrayList<>();
        for (Scope scope : scopes) {
            scopeNames.add(scope.getName());
        }
        return scopeNames;
    }
}
