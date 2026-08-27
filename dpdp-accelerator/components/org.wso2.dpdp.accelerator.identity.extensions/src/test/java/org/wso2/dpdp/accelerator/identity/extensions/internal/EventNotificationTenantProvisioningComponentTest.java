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

import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceRegistration;
import org.osgi.service.component.ComponentContext;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import org.wso2.carbon.stratos.common.listeners.TenantMgtListener;
import org.wso2.dpdp.accelerator.event.notifications.service.TopicService;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class EventNotificationTenantProvisioningComponentTest {

    @Mock
    private ComponentContext componentContext;

    @Mock
    private BundleContext bundleContext;

    @Mock
    private TopicService topicService;

    @Mock
    private ServiceRegistration<TenantMgtListener> listenerRegistration;

    private EventNotificationTenantProvisioningComponent component;

    @BeforeMethod
    public void setUp() {
        MockitoAnnotations.openMocks(this);
        component = new EventNotificationTenantProvisioningComponent();
        component.setTopicService(topicService);
        when(componentContext.getBundleContext()).thenReturn(bundleContext);
        when(bundleContext.registerService(
                eq(TenantMgtListener.class), any(TenantMgtListener.class), isNull()))
                .thenReturn(listenerRegistration);
    }

    @Test
    public void testActivationRegistersAndDeactivationUnregistersListener() {
        component.activate(componentContext);

        verify(bundleContext).registerService(
                eq(TenantMgtListener.class), any(TenantMgtListener.class), isNull());

        component.deactivate(componentContext);

        verify(listenerRegistration).unregister();
    }
}
