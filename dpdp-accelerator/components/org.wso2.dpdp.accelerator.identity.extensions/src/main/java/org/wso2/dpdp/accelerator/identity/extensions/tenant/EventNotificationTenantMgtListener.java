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
import org.wso2.carbon.utils.multitenancy.MultitenantConstants;
import org.wso2.dpdp.accelerator.event.notifications.service.TopicService;

/**
 * Provisions protected Event Notification topics for ordinary WSO2 tenants.
 */
public class EventNotificationTenantMgtListener implements TenantMgtListener {

    private static final Log LOG = LogFactory.getLog(EventNotificationTenantMgtListener.class);
    private static final int LISTENER_ORDER = 120;

    private final DefaultTopicProvisioner topicProvisioner;
    private final TenantFlow tenantFlow;

    public EventNotificationTenantMgtListener(TopicService topicService) {
        this(new DefaultTopicProvisioner(topicService), new CarbonTenantFlow());
    }

    EventNotificationTenantMgtListener(DefaultTopicProvisioner topicProvisioner) {
        this(topicProvisioner, new CarbonTenantFlow());
    }

    EventNotificationTenantMgtListener(DefaultTopicProvisioner topicProvisioner, TenantFlow tenantFlow) {
        if (topicProvisioner == null) {
            throw new IllegalArgumentException("DefaultTopicProvisioner cannot be null.");
        }
        if (tenantFlow == null) {
            throw new IllegalArgumentException("TenantFlow cannot be null.");
        }
        this.topicProvisioner = topicProvisioner;
        this.tenantFlow = tenantFlow;
    }

    @Override
    public void onTenantCreate(TenantInfoBean tenantInfoBean) throws StratosException {
        if (!isSupportedTenant(tenantInfoBean)) {
            return;
        }
        provisionTenantTopics(tenantInfoBean);
    }

    @Override
    public void onTenantUpdate(TenantInfoBean tenantInfoBean) {
        try {
            if (isSupportedTenant(tenantInfoBean)) {
                provisionTenantTopics(tenantInfoBean);
            }
        } catch (Exception e) {
            LOG.error("Error reconciling DPDP system topics for tenant: "
                    + sanitize(tenantInfoBean != null ? tenantInfoBean.getTenantDomain() : null), e);
        }
    }

    void provisionTenantTopics(TenantInfoBean tenantInfoBean) throws StratosException {
        String tenantDomain = tenantInfoBean.getTenantDomain().trim();
        tenantFlow.start(tenantInfoBean.getTenantId(), tenantDomain);
        try {
            topicProvisioner.provision(tenantDomain);
        } finally {
            tenantFlow.end();
        }
    }

    interface TenantFlow {
        void start(int tenantId, String tenantDomain);

        void end();
    }

    private static final class CarbonTenantFlow implements TenantFlow {

        @Override
        public void start(int tenantId, String tenantDomain) {
            PrivilegedCarbonContext.startTenantFlow();
            try {
                PrivilegedCarbonContext carbonContext = PrivilegedCarbonContext.getThreadLocalCarbonContext();
                carbonContext.setTenantId(tenantId);
                carbonContext.setTenantDomain(tenantDomain);
            } catch (RuntimeException e) {
                PrivilegedCarbonContext.endTenantFlow();
                throw e;
            }
        }

        @Override
        public void end() {
            PrivilegedCarbonContext.endTenantFlow();
        }
    }

    private boolean isSupportedTenant(TenantInfoBean tenantInfoBean) throws StratosException {
        if (tenantInfoBean == null || tenantInfoBean.getTenantDomain() == null
                || tenantInfoBean.getTenantDomain().trim().isEmpty()) {
            LOG.warn("Tenant information is incomplete; skipping DPDP system topic provisioning.");
            return false;
        }
        if (tenantInfoBean.getTenantId() == MultitenantConstants.SUPER_TENANT_ID
                || MultitenantConstants.SUPER_TENANT_DOMAIN_NAME.equalsIgnoreCase(
                        tenantInfoBean.getTenantDomain().trim())) {
            LOG.debug("Skipping DPDP system topic provisioning for the super tenant.");
            return false;
        }
        try {
            if (OrganizationManagementUtil.isOrganization(tenantInfoBean.getTenantId())) {
                LOG.debug("Skipping DPDP system topic provisioning for organization tenant: "
                        + sanitize(tenantInfoBean.getTenantDomain()));
                return false;
            }
            return true;
        } catch (Exception e) {
            throw new StratosException("Unable to determine tenant type for: "
                    + sanitize(tenantInfoBean.getTenantDomain()), e);
        }
    }

    private static String sanitize(String value) {
        return value == null ? null : value.replaceAll("[\r\n]", "");
    }

    @Override
    public int getListenerOrder() {
        return LISTENER_ORDER;
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
}
