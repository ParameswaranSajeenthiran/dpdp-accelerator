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

package org.wso2.dpdp.accelerator.common.internal;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.osgi.framework.ServiceRegistration;
import org.osgi.service.component.ComponentContext;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;
import org.wso2.dpdp.accelerator.common.config.DPDPConfigurationService;
import org.wso2.dpdp.accelerator.common.config.DPDPConfigurationServiceImpl;

/**
 * Registers {@link DPDPConfigurationService} as an OSGi service.
 */
@Component(
        name = "org.wso2.dpdp.accelerator.common.internal.DPDPCommonServiceComponent",
        immediate = true
)
public class DPDPCommonServiceComponent {

    private static final Log LOG = LogFactory.getLog(DPDPCommonServiceComponent.class);

    // Tracked so deactivate() can unregister it and avoid a duplicate on reactivation.
    private ServiceRegistration<DPDPConfigurationService> configurationServiceRegistration;

    @Activate
    protected void activate(ComponentContext context) {

        configurationServiceRegistration = context.getBundleContext().registerService(
                DPDPConfigurationService.class, new DPDPConfigurationServiceImpl(), null);
        LOG.debug("DPDP common component is activated successfully.");
    }

    @Deactivate
    protected void deactivate(ComponentContext context) {

        if (configurationServiceRegistration != null) {
            configurationServiceRegistration.unregister();
            configurationServiceRegistration = null;
        }
        LOG.debug("DPDP common component is deactivated.");
    }
}
