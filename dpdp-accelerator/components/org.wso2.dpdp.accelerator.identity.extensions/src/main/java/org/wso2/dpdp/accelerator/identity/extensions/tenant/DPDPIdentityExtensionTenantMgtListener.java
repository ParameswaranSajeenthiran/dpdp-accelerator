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
import org.wso2.dpdp.accelerator.identity.extensions.notification.EmailTemplateProvisioningUtil;

import java.util.ArrayList;
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

        // Log and continue - a provisioning failure shouldn't block the tenant update itself.
        // This is also the recovery path: re-run to fix whatever's missing.
        try {
            if (OrganizationManagementUtil.isOrganization(tenantInfoBean.getTenantId())) {
                LOG.debug("Skipping DPDP Consent Portal provisioning for organization tenant: "
                        + sanitize(tenantInfoBean.getTenantDomain()));
                return;
            }
            provisionTenant(tenantInfoBean);
        } catch (Exception e) {
            LOG.error("Error provisioning the DPDP Consent Portal for tenant: "
                    + sanitize(tenantInfoBean.getTenantDomain()), e);
        }
    }

    /**
     * Creates the portal app, its API authorization and its roles for one tenant, or repairs
     * whatever's missing if the app already exists. Safe to re-run since every step is idempotent.
     */
    public static void provisionTenant(TenantInfoBean tenantInfoBean) throws Exception {

        String tenantDomain = sanitize(tenantInfoBean.getTenantDomain());

        if (!DPDPIdentityExtensionDataHolder.getInstance().getConfigurationService()
                .isConsentPortalProvisioningEnabled()) {
            LOG.debug("DPDP Consent Portal provisioning is disabled; skipping tenant: " + tenantDomain);
            return;
        }

        PrivilegedCarbonContext.startTenantFlow();
        try {
            PrivilegedCarbonContext carbonContext = PrivilegedCarbonContext.getThreadLocalCarbonContext();
            carbonContext.setTenantId(tenantInfoBean.getTenantId());
            carbonContext.setTenantDomain(tenantDomain);
            carbonContext.setUsername(tenantInfoBean.getAdmin());

            String applicationId = DPDPConsentPortalAppProvisioningUtil.getApplicationId(tenantDomain);
            if (applicationId == null) {
                applicationId = DPDPConsentPortalAppProvisioningUtil.provisionApplication(tenantInfoBean);
            } else {
                LOG.debug("The DPDP Consent Portal application already exists for tenant: " + tenantDomain
                        + "; reconciling its API authorization and roles.");
            }

            List<String> authorizedConsentScopes = DPDPConsentPortalAppProvisioningUtil
                    .authorizeConsentManagementAPIs(applicationId, tenantDomain);
            List<String> authorizedComplaintScopes = DPDPComplaintMgtAppProvisioningUtil
                    .authorizeComplaintManagementAPI(applicationId, tenantDomain);

            // No dedicated complaint role - the complaint scopes are folded into dpdp-consent-admin
            // alongside the consent-mgt ones; dpdp-consent-user gets only the :self-suffixed
            // complaint scopes (see DPDPConsentPortalRoleProvisioningUtil).
            List<String> adminScopes = new ArrayList<>(authorizedConsentScopes);
            adminScopes.addAll(authorizedComplaintScopes);
            DPDPConsentPortalRoleProvisioningUtil.createRoles(applicationId, tenantDomain, adminScopes,
                    tenantInfoBean);
            EmailTemplateProvisioningUtil.provisionTemplates(tenantDomain);

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
