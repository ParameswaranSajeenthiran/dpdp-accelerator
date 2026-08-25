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

import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import org.wso2.carbon.stratos.common.beans.TenantInfoBean;
import org.wso2.carbon.stratos.common.exception.StratosException;
import org.wso2.carbon.utils.multitenancy.MultitenantConstants;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.testng.Assert.assertEquals;

public class EventNotificationTenantMgtListenerTest {

    @Mock
    private DefaultTopicProvisioner provisioner;

    @Mock
    private EventNotificationTenantMgtListener.TenantFlow tenantFlow;

    private EventNotificationTenantMgtListener listener;

    @BeforeMethod
    public void setUp() {
        MockitoAnnotations.openMocks(this);
        listener = new EventNotificationTenantMgtListener(provisioner, tenantFlow);
    }

    @Test
    public void testProvisionUsesTrimmedTenantDomainAsOrgId() throws Exception {
        TenantInfoBean tenantInfoBean = new TenantInfoBean();
        tenantInfoBean.setTenantId(12);
        tenantInfoBean.setTenantDomain(" tenant.example ");

        listener.provisionTenantTopics(tenantInfoBean);

        verify(tenantFlow).start(12, "tenant.example");
        verify(provisioner).provision("tenant.example");
        verify(tenantFlow).end();
    }

    @Test
    public void testTenantFlowEndsWhenProvisioningFails() throws Exception {
        TenantInfoBean tenantInfoBean = new TenantInfoBean();
        tenantInfoBean.setTenantId(12);
        tenantInfoBean.setTenantDomain("tenant.example");
        doThrow(new StratosException("failed")).when(provisioner).provision("tenant.example");

        try {
            listener.provisionTenantTopics(tenantInfoBean);
        } catch (StratosException ignored) {
            // Expected: the assertion is that the tenant flow still closes.
        }

        verify(tenantFlow).end();
    }

    @Test
    public void testListenerRunsAfterPortalProvisioningListener() {
        assertEquals(listener.getListenerOrder(), 120);
    }

    @Test
    public void testSuperTenantIsNotProvisioned() throws Exception {
        TenantInfoBean tenantInfoBean = new TenantInfoBean();
        tenantInfoBean.setTenantId(MultitenantConstants.SUPER_TENANT_ID);
        tenantInfoBean.setTenantDomain(MultitenantConstants.SUPER_TENANT_DOMAIN_NAME);

        listener.onTenantCreate(tenantInfoBean);

        verifyNoInteractions(provisioner);
    }

    @Test(expectedExceptions = IllegalArgumentException.class)
    public void testConstructorRejectsMissingProvisioner() {
        new EventNotificationTenantMgtListener((DefaultTopicProvisioner) null);
    }
}
