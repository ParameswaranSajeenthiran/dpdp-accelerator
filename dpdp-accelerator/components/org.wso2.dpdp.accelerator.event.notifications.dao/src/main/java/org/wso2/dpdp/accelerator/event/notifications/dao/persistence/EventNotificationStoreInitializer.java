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

package org.wso2.dpdp.accelerator.event.notifications.dao.persistence;

import org.wso2.dpdp.accelerator.common.config.DPDPConfigurationService;
import org.wso2.dpdp.accelerator.event.notifications.dao.DeliveryAckDAO;
import org.wso2.dpdp.accelerator.event.notifications.dao.DeliveryDAO;
import org.wso2.dpdp.accelerator.event.notifications.dao.EventDAO;
import org.wso2.dpdp.accelerator.event.notifications.dao.SubscriptionDAO;
import org.wso2.dpdp.accelerator.event.notifications.dao.TopicDAO;
import org.wso2.dpdp.accelerator.event.notifications.dao.impl.DeliveryAckDAOImpl;
import org.wso2.dpdp.accelerator.event.notifications.dao.impl.DeliveryDAOImpl;
import org.wso2.dpdp.accelerator.event.notifications.dao.impl.EventDAOImpl;
import org.wso2.dpdp.accelerator.event.notifications.dao.impl.SubscriptionDAOImpl;
import org.wso2.dpdp.accelerator.event.notifications.dao.impl.TopicDAOImpl;

/**
 * Creates the Event Notification persistence objects used by the service layer.
 *
 * <p>The DAO implementations are deliberately plain Java objects. Their shared
 * JDBC lifecycle remains owned by the common {@code JDBCPersistenceManager}.</p>
 */
public final class EventNotificationStoreInitializer {

    private final TopicDAO topicDAO;
    private final SubscriptionDAO subscriptionDAO;
    private final EventDAO eventDAO;
    private final DeliveryDAO deliveryDAO;
    private final DeliveryAckDAO deliveryAckDAO;

    private EventNotificationStoreInitializer(DPDPConfigurationService configurationService) {
        this.topicDAO = new TopicDAOImpl();
        this.subscriptionDAO = new SubscriptionDAOImpl();
        this.eventDAO = new EventDAOImpl();
        this.deliveryDAO = new DeliveryDAOImpl(configurationService);
        this.deliveryAckDAO = new DeliveryAckDAOImpl();
    }

    public static EventNotificationStoreInitializer initialize(
            DPDPConfigurationService configurationService) {

        if (configurationService == null) {
            throw new IllegalArgumentException("DPDP configuration service cannot be null.");
        }
        return new EventNotificationStoreInitializer(configurationService);
    }

    public TopicDAO getTopicDAO() {
        return topicDAO;
    }

    public SubscriptionDAO getSubscriptionDAO() {
        return subscriptionDAO;
    }

    public EventDAO getEventDAO() {
        return eventDAO;
    }

    public DeliveryDAO getDeliveryDAO() {
        return deliveryDAO;
    }

    public DeliveryAckDAO getDeliveryAckDAO() {
        return deliveryAckDAO;
    }
}
