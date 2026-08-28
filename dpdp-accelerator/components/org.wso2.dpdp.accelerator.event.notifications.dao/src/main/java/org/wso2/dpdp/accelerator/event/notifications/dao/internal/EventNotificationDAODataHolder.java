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

import org.wso2.dpdp.accelerator.common.config.DPDPConfigurationService;
import org.wso2.dpdp.accelerator.event.notifications.dao.DeliveryAckDAO;
import org.wso2.dpdp.accelerator.event.notifications.dao.DeliveryDAO;
import org.wso2.dpdp.accelerator.event.notifications.dao.EventDAO;
import org.wso2.dpdp.accelerator.event.notifications.dao.SubscriptionDAO;
import org.wso2.dpdp.accelerator.event.notifications.dao.TopicDAO;

/**
 * Holds the DAO instances and injected services owned by the Event Notification DAO component.
 */
public final class EventNotificationDAODataHolder {

    private static final EventNotificationDAODataHolder INSTANCE = new EventNotificationDAODataHolder();

    private volatile TopicDAO topicDAO;
    private volatile SubscriptionDAO subscriptionDAO;
    private volatile EventDAO eventDAO;
    private volatile DeliveryDAO deliveryDAO;
    private volatile DeliveryAckDAO deliveryAckDAO;
    private volatile DPDPConfigurationService configurationService;

    private EventNotificationDAODataHolder() {
    }

    public static EventNotificationDAODataHolder getInstance() {

        return INSTANCE;
    }

    public TopicDAO getTopicDAO() {

        return topicDAO;
    }

    public void setTopicDAO(TopicDAO topicDAO) {

        this.topicDAO = topicDAO;
    }

    public SubscriptionDAO getSubscriptionDAO() {

        return subscriptionDAO;
    }

    public void setSubscriptionDAO(SubscriptionDAO subscriptionDAO) {

        this.subscriptionDAO = subscriptionDAO;
    }

    public EventDAO getEventDAO() {

        return eventDAO;
    }

    public void setEventDAO(EventDAO eventDAO) {

        this.eventDAO = eventDAO;
    }

    public DeliveryDAO getDeliveryDAO() {

        return deliveryDAO;
    }

    public void setDeliveryDAO(DeliveryDAO deliveryDAO) {

        this.deliveryDAO = deliveryDAO;
    }

    public DeliveryAckDAO getDeliveryAckDAO() {

        return deliveryAckDAO;
    }

    public void setDeliveryAckDAO(DeliveryAckDAO deliveryAckDAO) {

        this.deliveryAckDAO = deliveryAckDAO;
    }

    public DPDPConfigurationService getConfigurationService() {

        return configurationService;
    }

    public void setConfigurationService(DPDPConfigurationService configurationService) {

        this.configurationService = configurationService;
    }

    public void clear() {

        topicDAO = null;
        subscriptionDAO = null;
        eventDAO = null;
        deliveryDAO = null;
        deliveryAckDAO = null;
        configurationService = null;
    }
}
