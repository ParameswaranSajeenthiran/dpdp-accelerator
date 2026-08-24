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
import org.wso2.carbon.identity.api.resource.mgt.constant.APIResourceManagementConstants.APIResourceTypes;
import org.wso2.carbon.identity.application.common.model.APIResource;
import org.wso2.carbon.identity.application.common.model.AuthorizedAPI;
import org.wso2.carbon.identity.application.common.model.Scope;
import org.wso2.carbon.identity.application.mgt.AuthorizedAPIManagementService;
import org.wso2.dpdp.accelerator.identity.extensions.internal.DPDPIdentityExtensionDataHolder;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Registers the complaint management API resource (the {@code portal:complaints:*} scopes) and
 * authorizes the DPDP Consent Portal application for it - the complaint management equivalent of
 * {@link DPDPConsentPortalAppProvisioningUtil#authorizeConsentManagementAPIs}.
 *
 * <p>Unlike the three consent-mgt APIs - built-in Identity Server features, already registered as
 * API resources before this code ever runs - this one does not exist until this class creates it:
 * it belongs to this accelerator's own complaint management webapp
 * ({@code org.wso2.dpdp.accelerator.complaint.mgt.endpoint}), not to Identity Server itself. Every
 * method here assumes it is already running inside the correct tenant's
 * {@code PrivilegedCarbonContext} flow, same as {@link DPDPConsentPortalAppProvisioningUtil}.
 */
public final class DPDPComplaintMgtAppProvisioningUtil {

    private static final Log LOG = LogFactory.getLog(DPDPComplaintMgtAppProvisioningUtil.class);

    static final String API_RESOURCE_IDENTIFIER = "/api/dpdp/complaints";
    private static final String API_RESOURCE_NAME = "DPDP Complaint Management API";
    private static final String AUTHORIZED_API_POLICY = "RBAC";

    static final String SCOPE_READ_SELF = "portal:complaints:read:self";
    static final String SCOPE_WRITE_SELF = "portal:complaints:write:self";
    static final String SCOPE_READ_ANY = "portal:complaints:read:any";
    static final String SCOPE_WRITE_ANY = "portal:complaints:write:any";

    private DPDPComplaintMgtAppProvisioningUtil() {

    }

    /**
     * Registers the complaint management API resource for this tenant if it doesn't already
     * exist, then authorizes {@code applicationId} for it (skipping re-authorization if already
     * done, same as {@link DPDPConsentPortalAppProvisioningUtil#authorizeConsentManagementAPIs}).
     *
     * @return the resulting scope names, in the same order as {@link #buildScopes()}.
     */
    public static List<String> authorizeComplaintManagementAPI(String applicationId, String tenantDomain)
            throws Exception {

        APIResourceManager apiResourceManager = DPDPIdentityExtensionDataHolder.getInstance().getApiResourceManager();
        AuthorizedAPIManagementService authorizedAPIManagementService = DPDPIdentityExtensionDataHolder.getInstance()
                .getAuthorizedAPIManagementService();

        APIResource apiResource = apiResourceManager.getAPIResourceByIdentifier(API_RESOURCE_IDENTIFIER,
                tenantDomain);
        if (apiResource == null) {
            apiResource = registerAPIResource(apiResourceManager, tenantDomain);
        }

        List<Scope> scopes;
        AuthorizedAPI existingAuthorization = authorizedAPIManagementService.getAuthorizedAPI(applicationId,
                apiResource.getId(), tenantDomain);
        if (existingAuthorization != null) {
            scopes = existingAuthorization.getScopes();
            LOG.debug("API '" + API_RESOURCE_IDENTIFIER + "' is already authorized for application: "
                    + applicationId);
        } else {
            scopes = apiResource.getScopes();
            AuthorizedAPI authorizedAPI = new AuthorizedAPI(applicationId, apiResource.getId(), AUTHORIZED_API_POLICY,
                    scopes, apiResource.getType());
            authorizedAPIManagementService.addAuthorizedAPI(applicationId, authorizedAPI, tenantDomain);
            LOG.debug("Authorized API '" + API_RESOURCE_IDENTIFIER + "' (" + scopes.size()
                    + " scope(s)) for application: " + applicationId);
        }

        List<String> scopeNames = new ArrayList<>();
        for (Scope scope : scopes) {
            scopeNames.add(scope.getName());
        }
        return scopeNames;
    }

    private static APIResource registerAPIResource(APIResourceManager apiResourceManager, String tenantDomain)
            throws Exception {

        APIResource apiResource = new APIResource.APIResourceBuilder()
                .name(API_RESOURCE_NAME)
                .identifier(API_RESOURCE_IDENTIFIER)
                .description("Grievance redressal API for the DPDP Consent Portal - see "
                        + "complaint-server-API.yaml in org.wso2.dpdp.accelerator.complaint.mgt.endpoint.")
                .type(APIResourceTypes.BUSINESS)
                .requiresAuthorization(true)
                .scopes(buildScopes())
                .build();

        APIResource created = apiResourceManager.addAPIResource(apiResource, tenantDomain);
        LOG.debug("Registered API resource '" + API_RESOURCE_IDENTIFIER + "' for tenant: " + tenantDomain);
        return created;
    }

    private static List<Scope> buildScopes() {

        return Arrays.asList(
                buildScope(SCOPE_READ_SELF, "Read own complaints",
                        "View the authenticated Data Principal's own complaints."),
                buildScope(SCOPE_WRITE_SELF, "Write own complaints",
                        "Create, comment on, or transition the status of the authenticated Data Principal's own "
                                + "complaints."),
                buildScope(SCOPE_READ_ANY, "Read any complaint",
                        "View any complaint in the organization (officer/admin)."),
                buildScope(SCOPE_WRITE_ANY, "Write any complaint",
                        "Create, comment on, or transition the status of any complaint in the organization "
                                + "(officer/admin)."));
    }

    // id is left null - the server assigns it when the API resource (and its nested scopes) is
    // created via addAPIResource; apiID/orgID likewise aren't known yet at this point.
    private static Scope buildScope(String name, String displayName, String description) {
        return new Scope(null, name, displayName, description);
    }
}
