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
import org.wso2.carbon.context.PrivilegedCarbonContext;
import org.wso2.carbon.identity.organization.management.service.util.OrganizationManagementUtil;
import org.wso2.carbon.stratos.common.beans.TenantInfoBean;
import org.wso2.carbon.stratos.common.exception.StratosException;
import org.wso2.carbon.stratos.common.listeners.TenantMgtListener;
import org.wso2.dpdp.accelerator.identity.extensions.internal.DPDPIdentityExtensionDataHolder;

import java.util.List;

/**
 * Registers the DPDP Consent Portal application in every newly created tenant, the same way
 * {@code org.wso2.identity.apps.common.listner.AppPortalTenantMgtListener} registers Console and
 * My Account. Runs in-process, so it never touches the management REST API layer.
 */
public class DPDPIdentityExtensionTenantMgtListener implements TenantMgtListener {

    private static final Log LOG = LogFactory.getLog(DPDPIdentityExtensionTenantMgtListener.class);

    @Override
    public void onTenantCreate(TenantInfoBean tenantInfoBean) throws StratosException {

        String tenantDomain = sanitize(tenantInfoBean.getTenantDomain());
        try {
            if (OrganizationManagementUtil.isOrganization(tenantInfoBean.getTenantId())) {
                LOG.debug("Skipping DPDP Consent Portal provisioning for organization tenant: " + tenantDomain);
                return;
            }
            provisionTenant(tenantInfoBean);
        } catch (Exception e) {
            LOG.error("Error provisioning the DPDP Consent Portal for tenant: " + tenantDomain, e);
            throw new StratosException("Error provisioning the DPDP Consent Portal for tenant: " + tenantDomain, e);
        }
    }

    @Override
    public void onTenantUpdate(TenantInfoBean tenantInfoBean) {

        // A failure here must not block the tenant update the admin actually asked for. This
        // also doubles as the recovery path: if the portal application was deleted, deleting it
        // and then updating the tenant (through this same hook) recreates it and its roles,
        // since provisionTenant()'s own existence check will now see nothing there.
        try {
            provisionTenant(tenantInfoBean);
        } catch (Exception e) {
            LOG.error("Error provisioning the DPDP Consent Portal for tenant: "
                    + sanitize(tenantInfoBean.getTenantDomain()), e);
        }
    }

    /**
     * Provisions the portal application and its roles for one tenant. Shared between
     * {@link #onTenantCreate}, {@link #onTenantUpdate}, and the service component's own
     * super-tenant bootstrap, since {@code onTenantCreate} never fires for the super tenant.
     */
    public static void provisionTenant(TenantInfoBean tenantInfoBean) throws Exception {

        String tenantDomain = sanitize(tenantInfoBean.getTenantDomain());

        if (!DPDPIdentityExtensionDataHolder.getInstance().getConfigurationService()
                .isConsentPortalProvisioningEnabled()) {
            LOG.debug("DPDP Consent Portal provisioning is disabled; skipping tenant: " + tenantDomain);
            return;
        }

        if (DPDPConsentPortalAppProvisioningUtil.applicationExists(tenantDomain)) {
            LOG.debug("The DPDP Consent Portal application already exists for tenant: " + tenantDomain
                    + "; skipping provisioning.");
            return;
        }

        PrivilegedCarbonContext.startTenantFlow();
        try {
            PrivilegedCarbonContext carbonContext = PrivilegedCarbonContext.getThreadLocalCarbonContext();
            carbonContext.setTenantId(tenantInfoBean.getTenantId());
            carbonContext.setTenantDomain(tenantDomain);
            carbonContext.setUsername(tenantInfoBean.getAdmin());

            String applicationId = DPDPConsentPortalAppProvisioningUtil.provisionApplication(tenantInfoBean);
            List<String> authorizedScopes = DPDPConsentPortalAppProvisioningUtil
                    .authorizeConsentManagementAPIs(applicationId, tenantDomain);
            DPDPConsentPortalRoleProvisioningUtil.createRoles(applicationId, tenantDomain, authorizedScopes);

            LOG.info("Provisioned the DPDP Consent Portal for tenant: " + tenantDomain);
        } finally {
            PrivilegedCarbonContext.endTenantFlow();
        }
    }

    private static String sanitize(String value) {

        return value == null ? null : value.replaceAll("[\r\n]", "");
    }

    @Override
    public void onTenantDelete(int tenantId) {

    }

    @Override
    public void onTenantRename(int tenantId, String oldDomainName, String newDomainName) {

    }

    @Override
    public void onTenantInitialActivation(int tenantId) {

    }

    @Override
    public void onTenantActivation(int tenantId) {

    }

    @Override
    public void onTenantDeactivation(int tenantId) {

    }

    @Override
    public void onSubscriptionPlanChange(int tenantId, String oldPlan, String newPlan) {

    }

    @Override
    public void onPreDelete(int tenantId) {

    }

    @Override
    public int getListenerOrder() {

        // Runs after AppPortalTenantMgtListener (order 100); no dependency between the two,
        // just avoids racing it.
        return 110;
    }
}
