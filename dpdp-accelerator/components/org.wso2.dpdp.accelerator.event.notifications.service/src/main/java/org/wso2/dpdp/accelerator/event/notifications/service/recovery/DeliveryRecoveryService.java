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

package org.wso2.dpdp.accelerator.event.notifications.service.recovery;

import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Reference;
import org.wso2.dpdp.accelerator.event.notifications.common.config.EventNotificationConfigParser;
import org.wso2.dpdp.accelerator.event.notifications.dao.DeliveryDAO;
import org.wso2.dpdp.accelerator.event.notifications.dao.SubscriptionDAO;
import org.wso2.dpdp.accelerator.event.notifications.dao.model.Subscription;
import org.wso2.dpdp.accelerator.event.notifications.service.SubscriptionService;
import org.wso2.dpdp.accelerator.event.notifications.service.dispatch.WebhookDeliveryWorker;

import java.sql.Timestamp;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Dedicated OSGi background recovery service for recovering overdue webhook
 * retries
 * and stuck pending subscriptions across JVM server restarts.
 */
@Component(service = DeliveryRecoveryService.class, immediate = true)
public class DeliveryRecoveryService {

    private static final Logger LOG = Logger.getLogger(DeliveryRecoveryService.class.getName());

    @Reference
    private SubscriptionDAO subscriptionDAO;

    @Reference
    private DeliveryDAO deliveryDAO;

    @Reference
    private SubscriptionService subscriptionService;

    private ScheduledExecutorService scheduler;
    private ExecutorService workerPool;

    public DeliveryRecoveryService() {
    }

    public DeliveryRecoveryService(SubscriptionDAO subscriptionDAO,
            DeliveryDAO deliveryDAO, SubscriptionService subscriptionService) {
        this.subscriptionDAO = subscriptionDAO;
        this.deliveryDAO = deliveryDAO;
        this.subscriptionService = subscriptionService;
    }

    @Activate
    protected void activate() {
        int poolSize = EventNotificationConfigParser.getInstance().getThreadPoolSize();
        this.scheduler = Executors.newScheduledThreadPool(2, r -> {
            Thread t = new Thread(r, "delivery-recovery-scheduler");
            t.setDaemon(true);
            return t;
        });
        this.workerPool = Executors.newFixedThreadPool(Math.max(1, poolSize), r -> {
            Thread t = new Thread(r, "webhook-delivery-worker");
            t.setDaemon(true);
            return t;
        });

        this.scheduler.scheduleWithFixedDelay(new PendingDeliveryRecoveryTask(), 10, 30, TimeUnit.SECONDS);

        int deliveryPollSeconds = EventNotificationConfigParser.getInstance().getDeliveryWorkerPollSeconds();
        this.scheduler.scheduleWithFixedDelay(
                new WebhookDeliveryWorker(deliveryDAO, this.workerPool),
                10,
                deliveryPollSeconds,
                TimeUnit.SECONDS);

        LOG.info("Delivery Recovery Service activated with background recovery worker and webhook "
                + "delivery worker (poll every " + deliveryPollSeconds + "s).");
    }

    @Deactivate
    protected void deactivate() {
        shutdownGracefully("delivery-recovery-scheduler", scheduler);
        shutdownGracefully("webhook-delivery-worker-pool", workerPool);
        LOG.info("Delivery Recovery Service deactivated cleanly.");
    }

    private static void shutdownGracefully(String name, java.util.concurrent.ExecutorService pool) {
        if (pool == null || pool.isShutdown()) {
            return;
        }
        pool.shutdown();
        try {
            if (!pool.awaitTermination(5, TimeUnit.SECONDS)) {
                LOG.warning(name + " did not terminate within 5 s; forcing interrupt.");
                pool.shutdownNow();
            }
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            pool.shutdownNow();
        }
    }

    private class PendingDeliveryRecoveryTask implements Runnable {
        @Override
        public void run() {
            try {
                recoverPendingSubscriptions();
            } catch (Exception e) {
                LOG.log(Level.WARNING, "Error during pending subscription recovery run: " + e.getMessage(), e);
            }
        }

        private void recoverPendingSubscriptions() {
            Timestamp threshold = new Timestamp(System.currentTimeMillis()
                    - EventNotificationConfigParser.getInstance().getPendingSubscriptionRecoveryThresholdSeconds()
                    * 1000L);
            List<Subscription> pendingSubs = subscriptionDAO.getPendingSubscriptionsForRecovery(threshold, 20);
            for (Subscription sub : pendingSubs) {
                if (sub.getCallbackUrl() != null && !sub.getCallbackUrl().trim().isEmpty()) {
                    try {
                        subscriptionService.retryVerification(sub.getOrgId(), sub.getSubscriptionId());
                        LOG.info("Recovered and re-verified pending subscription [" + sub.getSubscriptionId() + "].");
                    } catch (Exception e) {
                        LOG.log(Level.FINE, "Recovery retry verification for subscription [" + sub.getSubscriptionId()
                                + "] deferred: " + e.getMessage());
                    }
                }
            }
        }
    }
}
