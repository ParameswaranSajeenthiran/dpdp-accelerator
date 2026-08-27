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
import org.osgi.framework.ServiceRegistration;
import org.osgi.service.component.ComponentContext;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.component.annotations.ReferenceCardinality;
import org.osgi.service.component.annotations.ReferencePolicy;
import org.wso2.carbon.stratos.common.listeners.TenantMgtListener;
import org.wso2.dpdp.accelerator.event.notifications.service.TopicService;
import org.wso2.dpdp.accelerator.identity.extensions.tenant.EventNotificationTenantMgtListener;

/**
 * Registers the Event Notification tenant listener once TopicService is available.
 */
@Component(
        name = "org.wso2.dpdp.accelerator.identity.extensions.internal.EventNotificationTenantProvisioningComponent",
        immediate = true
)
public class EventNotificationTenantProvisioningComponent {

    private static final Log LOG = LogFactory.getLog(EventNotificationTenantProvisioningComponent.class);

    private volatile TopicService topicService;
    private ServiceRegistration<TenantMgtListener> listenerRegistration;

    @Activate
    protected void activate(ComponentContext context) {
        listenerRegistration = context.getBundleContext().registerService(TenantMgtListener.class,
                new EventNotificationTenantMgtListener(topicService), null);
        LOG.debug("Event Notification tenant provisioning listener registered.");
    }

    @Deactivate
    protected void deactivate(ComponentContext context) {
        if (listenerRegistration != null) {
            listenerRegistration.unregister();
            listenerRegistration = null;
        }
        LOG.debug("Event Notification tenant provisioning listener unregistered.");
    }

    @Reference(
            service = TopicService.class,
            cardinality = ReferenceCardinality.MANDATORY,
            policy = ReferencePolicy.DYNAMIC,
            unbind = "unsetTopicService"
    )
    protected void setTopicService(TopicService topicService) {
        this.topicService = topicService;
    }

    protected void unsetTopicService(TopicService topicService) {
        if (this.topicService == topicService) {
            this.topicService = null;
        }
    }
}
