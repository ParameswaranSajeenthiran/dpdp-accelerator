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

package org.wso2.dpdp.accelerator.event.notifications.service.dispatch;

import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import org.wso2.dpdp.accelerator.event.notifications.dao.DeliveryDAO;
import java.net.http.HttpClient;
import java.sql.Timestamp;
import java.util.Collections;
import java.util.concurrent.ScheduledExecutorService;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

public class WebhookDeliveryWorkerStuckRecoveryTest {

        @Mock
        private DeliveryDAO deliveryDAO;

        @Mock
        private ScheduledExecutorService scheduler;

        @Mock
        private HttpClient httpClient;

        private WebhookDeliveryWorker worker;

        @BeforeMethod
        public void setUp() {
                MockitoAnnotations.openMocks(this);
                worker = new WebhookDeliveryWorker(deliveryDAO, scheduler, httpClient);
        }

        @Test
        public void testStuckRecoveryPassAppliesTimestampCutoff() {
                when(deliveryDAO.getPendingWebhookDispatchContexts(any(Integer.class)))
                                .thenReturn(Collections.emptyList());
                when(deliveryDAO.getStuckInFlightWebhookDispatchContexts(any(Integer.class), any(Timestamp.class)))
                                .thenReturn(Collections.emptyList());

                int[] result = worker.runTick();

                assertEquals(result[0], 0);
                assertEquals(result[1], 0);

                ArgumentCaptor<Timestamp> cutoffCaptor = ArgumentCaptor.forClass(Timestamp.class);
                verify(deliveryDAO).getStuckInFlightWebhookDispatchContexts(anyInt(), cutoffCaptor.capture());

                Timestamp cutoff = cutoffCaptor.getValue();
                long thresholdMs = org.wso2.dpdp.accelerator.event.notifications.common.config.EventNotificationConfigParser
                                .getInstance().getStuckInFlightThresholdSeconds() * 1000L;
                assertTrue(cutoff.getTime() <= System.currentTimeMillis() - (thresholdMs - 2000L),
                                "Cutoff timestamp should be past the stuck threshold");
        }
}
