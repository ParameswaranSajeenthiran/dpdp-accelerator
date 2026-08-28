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

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceRegistration;
import org.osgi.service.component.ComponentContext;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.component.annotations.ReferenceCardinality;
import org.osgi.service.component.annotations.ReferencePolicy;
import org.wso2.carbon.core.ServerStartupObserver;
import org.wso2.carbon.identity.api.resource.mgt.APIResourceManager;
import org.wso2.carbon.identity.application.mgt.ApplicationManagementService;
import org.wso2.carbon.identity.application.mgt.AuthorizedAPIManagementService;
import org.wso2.carbon.identity.oauth.OAuthAdminServiceImpl;
import org.wso2.carbon.identity.role.v2.mgt.core.RoleManagementService;
import org.wso2.carbon.stratos.common.listeners.TenantMgtListener;
import org.wso2.carbon.user.core.service.RealmService;
import org.wso2.dpdp.accelerator.common.config.DPDPConfigurationService;
import org.wso2.dpdp.accelerator.identity.extensions.tenant.DPDPIdentityExtensionTenantMgtListener;

/**
 * Registers {@link DPDPIdentityExtensionTenantMgtListener} for future tenants, and a
 * {@link DPDPServerStartupObserver} that provisions the super tenant once the whole server has
 * finished starting - since {@code onTenantCreate} never fires for it, and provisioning it
 * directly here in {@code @Activate} would race the consent-mgt v2 API-resource registration that
 * a mandatory {@code @Reference} on {@link APIResourceManager} does not wait for. See
 * {@link DPDPServerStartupObserver} for the full explanation.
 */
@Component(
        name = "org.wso2.dpdp.accelerator.identity.extensions.internal.DPDPIdentityExtensionServiceComponent",
        immediate = true
)
public class DPDPIdentityExtensionServiceComponent {

    private static final Log LOG = LogFactory.getLog(DPDPIdentityExtensionServiceComponent.class);

    // Tracked so deactivate() can unregister them and avoid duplicates on reactivation.
    private ServiceRegistration<TenantMgtListener> tenantMgtListenerRegistration;
    private ServiceRegistration<ServerStartupObserver> serverStartupObserverRegistration;

    @Activate
    protected void activate(ComponentContext context) {

        BundleContext bundleContext = context.getBundleContext();
        tenantMgtListenerRegistration = bundleContext.registerService(TenantMgtListener.class,
                new DPDPIdentityExtensionTenantMgtListener(), null);
        serverStartupObserverRegistration = bundleContext.registerService(ServerStartupObserver.class,
                new DPDPServerStartupObserver(), null);
        LOG.debug("DPDP Identity Extensions component activated; tenant management listener and "
                + "server startup observer registered.");
    }

    @Deactivate
    protected void deactivate(ComponentContext context) {

        if (tenantMgtListenerRegistration != null) {
            tenantMgtListenerRegistration.unregister();
            tenantMgtListenerRegistration = null;
        }
        if (serverStartupObserverRegistration != null) {
            serverStartupObserverRegistration.unregister();
            serverStartupObserverRegistration = null;
        }
        LOG.debug("DPDP Identity Extensions component deactivated.");
    }

    @Reference(
            service = ApplicationManagementService.class,
            cardinality = ReferenceCardinality.MANDATORY,
            policy = ReferencePolicy.DYNAMIC,
            unbind = "unsetApplicationManagementService"
    )
    protected void setApplicationManagementService(ApplicationManagementService applicationManagementService) {

        LOG.debug("Setting the Application Management Service.");
        DPDPIdentityExtensionDataHolder.getInstance().setApplicationManagementService(applicationManagementService);
    }

    protected void unsetApplicationManagementService(ApplicationManagementService applicationManagementService) {

        LOG.debug("Unsetting the Application Management Service.");
        DPDPIdentityExtensionDataHolder.getInstance().setApplicationManagementService(null);
    }

    @Reference(
            service = OAuthAdminServiceImpl.class,
            cardinality = ReferenceCardinality.MANDATORY,
            policy = ReferencePolicy.DYNAMIC,
            unbind = "unsetOAuthAdminService"
    )
    protected void setOAuthAdminService(OAuthAdminServiceImpl oAuthAdminService) {

        LOG.debug("Setting the OAuth Admin Service.");
        DPDPIdentityExtensionDataHolder.getInstance().setOAuthAdminService(oAuthAdminService);
    }

    protected void unsetOAuthAdminService(OAuthAdminServiceImpl oAuthAdminService) {

        LOG.debug("Unsetting the OAuth Admin Service.");
        DPDPIdentityExtensionDataHolder.getInstance().setOAuthAdminService(null);
    }

    @Reference(
            service = AuthorizedAPIManagementService.class,
            cardinality = ReferenceCardinality.MANDATORY,
            policy = ReferencePolicy.DYNAMIC,
            unbind = "unsetAuthorizedAPIManagementService"
    )
    protected void setAuthorizedAPIManagementService(AuthorizedAPIManagementService authorizedAPIManagementService) {

        LOG.debug("Setting the Authorized API Management Service.");
        DPDPIdentityExtensionDataHolder.getInstance()
                .setAuthorizedAPIManagementService(authorizedAPIManagementService);
    }

    protected void unsetAuthorizedAPIManagementService(
            AuthorizedAPIManagementService authorizedAPIManagementService) {

        LOG.debug("Unsetting the Authorized API Management Service.");
        DPDPIdentityExtensionDataHolder.getInstance().setAuthorizedAPIManagementService(null);
    }

    @Reference(
            service = APIResourceManager.class,
            cardinality = ReferenceCardinality.MANDATORY,
            policy = ReferencePolicy.DYNAMIC,
            unbind = "unsetAPIResourceManager"
    )
    protected void setAPIResourceManager(APIResourceManager apiResourceManager) {

        LOG.debug("Setting the API Resource Manager.");
        DPDPIdentityExtensionDataHolder.getInstance().setApiResourceManager(apiResourceManager);
    }

    protected void unsetAPIResourceManager(APIResourceManager apiResourceManager) {

        LOG.debug("Unsetting the API Resource Manager.");
        DPDPIdentityExtensionDataHolder.getInstance().setApiResourceManager(null);
    }

    @Reference(
            service = RoleManagementService.class,
            cardinality = ReferenceCardinality.MANDATORY,
            policy = ReferencePolicy.DYNAMIC,
            unbind = "unsetRoleManagementService"
    )
    protected void setRoleManagementService(RoleManagementService roleManagementService) {

        LOG.debug("Setting the Role Management Service.");
        DPDPIdentityExtensionDataHolder.getInstance().setRoleManagementService(roleManagementService);
    }

    protected void unsetRoleManagementService(RoleManagementService roleManagementService) {

        LOG.debug("Unsetting the Role Management Service.");
        DPDPIdentityExtensionDataHolder.getInstance().setRoleManagementService(null);
    }

    @Reference(
            name = "realm.service",
            service = RealmService.class,
            cardinality = ReferenceCardinality.MANDATORY,
            policy = ReferencePolicy.DYNAMIC,
            unbind = "unsetRealmService"
    )
    protected void setRealmService(RealmService realmService) {

        LOG.debug("Setting the Realm Service.");
        DPDPIdentityExtensionDataHolder.getInstance().setRealmService(realmService);
    }

    protected void unsetRealmService(RealmService realmService) {

        LOG.debug("Unsetting the Realm Service.");
        DPDPIdentityExtensionDataHolder.getInstance().setRealmService(null);
    }

    @Reference(
            service = DPDPConfigurationService.class,
            cardinality = ReferenceCardinality.MANDATORY,
            policy = ReferencePolicy.DYNAMIC,
            unbind = "unsetConfigurationService"
    )
    protected void setConfigurationService(DPDPConfigurationService configurationService) {

        LOG.debug("Setting the DPDP Configuration Service.");
        DPDPIdentityExtensionDataHolder.getInstance().setConfigurationService(configurationService);
    }

    protected void unsetConfigurationService(DPDPConfigurationService configurationService) {

        LOG.debug("Unsetting the DPDP Configuration Service.");
        DPDPIdentityExtensionDataHolder.getInstance().setConfigurationService(null);
    }
}
