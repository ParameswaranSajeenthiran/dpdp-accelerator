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

import org.testng.annotations.Test;
import org.wso2.dpdp.accelerator.common.config.DPDPConfigurationService;

import static org.mockito.Mockito.mock;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertSame;

public class EventNotificationStoreInitializerTest {

    @Test
    public void shouldCreateOneInstanceOfEachDAO() {
        DPDPConfigurationService configurationService = mock(DPDPConfigurationService.class);

        EventNotificationStoreInitializer store =
                EventNotificationStoreInitializer.initialize(configurationService);

        assertNotNull(store.getTopicDAO());
        assertNotNull(store.getSubscriptionDAO());
        assertNotNull(store.getEventDAO());
        assertNotNull(store.getDeliveryDAO());
        assertNotNull(store.getDeliveryAckDAO());
        assertSame(store.getTopicDAO(), store.getTopicDAO());
        assertSame(store.getSubscriptionDAO(), store.getSubscriptionDAO());
        assertSame(store.getEventDAO(), store.getEventDAO());
        assertSame(store.getDeliveryDAO(), store.getDeliveryDAO());
        assertSame(store.getDeliveryAckDAO(), store.getDeliveryAckDAO());
    }

    @Test(expectedExceptions = IllegalArgumentException.class)
    public void shouldRejectMissingConfigurationService() {
        EventNotificationStoreInitializer.initialize(null);
    }
}
