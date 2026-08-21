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

package org.wso2.dpdp.accelerator.consent.history.internal;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.osgi.framework.BundleContext;
import org.osgi.service.component.ComponentContext;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.component.annotations.ReferenceCardinality;
import org.osgi.service.component.annotations.ReferencePolicy;
import org.wso2.dpdp.accelerator.common.config.DPDPConfigurationService;
import org.wso2.dpdp.accelerator.consent.history.service.ConsentHistoryService;
import org.wso2.dpdp.accelerator.consent.history.service.impl.ConsentHistoryServiceImpl;

@Component(
        name = "org.wso2.dpdp.accelerator.consent.history.internal.ConsentHistoryServiceComponent",
        immediate = true
)
public class ConsentHistoryServiceComponent {

    private static final Log LOG = LogFactory.getLog(ConsentHistoryServiceComponent.class);

    @Activate
    protected void activate(ComponentContext context) {

        BundleContext bundleContext = context.getBundleContext();
        bundleContext.registerService(ConsentHistoryService.class.getName(), new ConsentHistoryServiceImpl(), null);
        LOG.debug("DPDP Consent History component activated; service registered.");
    }

    @Deactivate
    protected void deactivate(ComponentContext context) {

        LOG.debug("DPDP Consent History component deactivated.");
    }

    @Reference(
            service = DPDPConfigurationService.class,
            cardinality = ReferenceCardinality.MANDATORY,
            policy = ReferencePolicy.DYNAMIC,
            unbind = "unsetConfigurationService"
    )
    protected void setConfigurationService(DPDPConfigurationService configurationService) {

        LOG.debug("Setting the DPDP Configuration Service.");
        ConsentHistoryDataHolder.getInstance().setConfigurationService(configurationService);
    }

    protected void unsetConfigurationService(DPDPConfigurationService configurationService) {

        LOG.debug("Unsetting the DPDP Configuration Service.");
        ConsentHistoryDataHolder.getInstance().setConfigurationService(null);
    }
}
