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

        try {
            if (OrganizationManagementUtil.isOrganization(tenantInfoBean.getTenantId())) {
                return;
            }
            provisionTenant(tenantInfoBean);
        } catch (Exception e) {
            throw new StratosException("Error provisioning the DPDP Consent Portal for tenant: "
                    + tenantInfoBean.getTenantDomain(), e);
        }
    }

    /**
     * Provisions the portal application and its roles for one tenant. Shared between
     * {@link #onTenantCreate} and the service component's own super-tenant bootstrap, since
     * {@code onTenantCreate} never fires for the super tenant.
     */
    public static void provisionTenant(TenantInfoBean tenantInfoBean) throws Exception {

        String tenantDomain = tenantInfoBean.getTenantDomain();
        if (DPDPConsentPortalAppProvisioningUtil.applicationExists(tenantDomain)) {
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

            LOG.debug("Provisioned the DPDP Consent Portal for tenant: " + tenantDomain);
        } finally {
            PrivilegedCarbonContext.endTenantFlow();
        }
    }

    @Override
    public void onTenantUpdate(TenantInfoBean tenantInfoBean) {

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
