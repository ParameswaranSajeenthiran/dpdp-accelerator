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

package org.wso2.dpdp.accelerator.event.notifications.service;

import org.wso2.dpdp.accelerator.event.notifications.service.dao.impl.DeliveryAckDAOImpl;
import org.wso2.dpdp.accelerator.event.notifications.service.dao.impl.DeliveryDAOImpl;
import org.wso2.dpdp.accelerator.event.notifications.service.dao.impl.SubscriptionDAOImpl;
import org.wso2.dpdp.accelerator.event.notifications.service.dao.impl.TopicDAOImpl;
import org.wso2.dpdp.accelerator.event.notifications.service.impl.SubscriptionServiceImpl;
import org.wso2.dpdp.accelerator.event.notifications.service.impl.TopicServiceImpl;

/**
 * Factory for creating fully wired service instances with their default DAO implementations.
 *
 * <p>This is the single place in the codebase that knows about concrete DAO implementations,
 * keeping service classes and endpoint handlers free from impl-layer imports.
 */
public final class ServiceFactory {

    private ServiceFactory() {
    }

    /**
     * Creates a {@link SubscriptionService} with all default JDBC DAO implementations.
     */
    public static SubscriptionService createSubscriptionService() {
        return new SubscriptionServiceImpl(
                new SubscriptionDAOImpl(),
                new TopicDAOImpl(),
                new DeliveryDAOImpl(),
                new DeliveryAckDAOImpl()
        );
    }

    /**
     * Creates a {@link TopicService} with the default JDBC DAO implementation.
     */
    public static TopicService createTopicService() {
        return new TopicServiceImpl(new TopicDAOImpl());
    }
}
