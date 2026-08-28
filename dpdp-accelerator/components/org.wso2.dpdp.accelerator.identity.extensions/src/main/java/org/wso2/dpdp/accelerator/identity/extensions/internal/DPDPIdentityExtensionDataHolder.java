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

package org.wso2.dpdp.accelerator.identity.extensions.internal;

import org.wso2.carbon.consent.mgt.core.PrivilegedConsentManager;
import org.wso2.carbon.identity.api.resource.mgt.APIResourceManager;
import org.wso2.carbon.identity.application.mgt.ApplicationManagementService;
import org.wso2.carbon.identity.application.mgt.AuthorizedAPIManagementService;
import org.wso2.carbon.identity.oauth.OAuthAdminServiceImpl;
import org.wso2.carbon.identity.organization.management.service.OrganizationManager;
import org.wso2.carbon.identity.role.v2.mgt.core.RoleManagementService;
import org.wso2.carbon.user.core.service.RealmService;
import org.wso2.dpdp.accelerator.common.config.DPDPConfigurationService;
import org.wso2.dpdp.accelerator.consent.extensions.service.ConsentExpiryService;
import org.wso2.dpdp.accelerator.consent.extensions.service.ConsentHistoryService;
import org.wso2.dpdp.accelerator.event.notifications.service.TopicService;

/**
 * Singleton holder for the OSGi services this module's listener needs, populated by
 * {@link DPDPIdentityExtensionServiceComponent}.
 */
public final class DPDPIdentityExtensionDataHolder {

    private static final DPDPIdentityExtensionDataHolder INSTANCE = new DPDPIdentityExtensionDataHolder();

    private ApplicationManagementService applicationManagementService;
    private OAuthAdminServiceImpl oAuthAdminService;
    private AuthorizedAPIManagementService authorizedAPIManagementService;
    private APIResourceManager apiResourceManager;
    private RoleManagementService roleManagementService;
    private OrganizationManager organizationManager;
    private RealmService realmService;
    private DPDPConfigurationService configurationService;
    private PrivilegedConsentManager privilegedConsentManager;
    private ConsentHistoryService consentHistoryService;
    private ConsentExpiryService consentExpiryService;
    private TopicService topicService;

    private DPDPIdentityExtensionDataHolder() {

    }

    public static DPDPIdentityExtensionDataHolder getInstance() {

        return INSTANCE;
    }

    public ApplicationManagementService getApplicationManagementService() {

        return applicationManagementService;
    }

    public void setApplicationManagementService(ApplicationManagementService applicationManagementService) {

        this.applicationManagementService = applicationManagementService;
    }

    public OAuthAdminServiceImpl getOAuthAdminService() {

        return oAuthAdminService;
    }

    public void setOAuthAdminService(OAuthAdminServiceImpl oAuthAdminService) {

        this.oAuthAdminService = oAuthAdminService;
    }

    public AuthorizedAPIManagementService getAuthorizedAPIManagementService() {

        return authorizedAPIManagementService;
    }

    public void setAuthorizedAPIManagementService(AuthorizedAPIManagementService authorizedAPIManagementService) {

        this.authorizedAPIManagementService = authorizedAPIManagementService;
    }

    public APIResourceManager getApiResourceManager() {

        return apiResourceManager;
    }

    public void setApiResourceManager(APIResourceManager apiResourceManager) {

        this.apiResourceManager = apiResourceManager;
    }

    public RoleManagementService getRoleManagementService() {

        return roleManagementService;
    }

    public void setRoleManagementService(RoleManagementService roleManagementService) {

        this.roleManagementService = roleManagementService;
    }

    public OrganizationManager getOrganizationManager() {

        return organizationManager;
    }

    public void setOrganizationManager(OrganizationManager organizationManager) {

        this.organizationManager = organizationManager;
    }

    public RealmService getRealmService() {

        return realmService;
    }

    public void setRealmService(RealmService realmService) {

        this.realmService = realmService;
    }

    public DPDPConfigurationService getConfigurationService() {

        return configurationService;
    }

    public void setConfigurationService(DPDPConfigurationService configurationService) {

        this.configurationService = configurationService;
    }

    public PrivilegedConsentManager getPrivilegedConsentManager() {

        return privilegedConsentManager;
    }

    public void setPrivilegedConsentManager(PrivilegedConsentManager privilegedConsentManager) {

        this.privilegedConsentManager = privilegedConsentManager;
    }

    public ConsentHistoryService getConsentHistoryService() {

        return consentHistoryService;
    }

    public void setConsentHistoryService(ConsentHistoryService consentHistoryService) {

        this.consentHistoryService = consentHistoryService;
    }

    public ConsentExpiryService getConsentExpiryService() {

        return consentExpiryService;
    }

    public void setConsentExpiryService(ConsentExpiryService consentExpiryService) {

        this.consentExpiryService = consentExpiryService;
    }

    public TopicService getTopicService() {

        return topicService;
    }

    public void setTopicService(TopicService topicService) {

        this.topicService = topicService;
    }
}
