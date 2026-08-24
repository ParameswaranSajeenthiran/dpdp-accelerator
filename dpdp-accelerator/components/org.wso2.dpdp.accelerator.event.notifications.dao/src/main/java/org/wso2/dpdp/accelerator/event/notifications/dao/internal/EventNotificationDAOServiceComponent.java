/**
 * Copyright (c) 2026, WSO2 LLC. (https://www.wso2.com).
 * <p>
 * WSO2 LLC. licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 *     http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.wso2.dpdp.accelerator.event.notifications.dao.internal;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;

/**
 * Activation marker for the Event Notification DAO bundle.
 */
@Component(
        name = "org.wso2.dpdp.accelerator.event.notifications.dao.internal.EventNotificationDAOServiceComponent",
        immediate = true
)
public class EventNotificationDAOServiceComponent {

    private static final Log LOG = LogFactory.getLog(EventNotificationDAOServiceComponent.class);

    @Activate
    protected void activate() {
        LOG.debug("Event Notification DAO component is activated successfully.");
    }

    @Deactivate
    protected void deactivate() {
        LOG.debug("Event Notification DAO component is deactivated successfully.");
    }
}
