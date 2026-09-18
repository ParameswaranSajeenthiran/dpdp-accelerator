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

package org.wso2.dpdp.accelerator.identity.extensions.consent.scheduler;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.wso2.dpdp.accelerator.common.config.DPDPConfigurationService;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/** Lifecycle-managed timer; database claims coordinate the work across IS instances. */
public final class ConsentExpiryJobScheduler implements AutoCloseable {

    private static final Log LOG = LogFactory.getLog(ConsentExpiryJobScheduler.class);
    private static final int SHUTDOWN_TIMEOUT_SECONDS = 30;

    private final DPDPConfigurationService configuration;
    private final Runnable sweep;
    private final ScheduledExecutorService executor;
    private final Clock clock;
    private boolean started;
    private volatile boolean closed;
    private String mode;
    private LocalTime dailyTime;
    private ZoneId zone;
    private int intervalSeconds;

    public ConsentExpiryJobScheduler(DPDPConfigurationService configuration, Runnable sweep) {

        this(configuration, sweep, Executors.newSingleThreadScheduledExecutor(task -> {
            Thread thread = new Thread(task, "consent-expiry-scheduler");
            thread.setDaemon(true);
            return thread;
        }), Clock.systemUTC());
    }

    ConsentExpiryJobScheduler(DPDPConfigurationService configuration, Runnable sweep,
            ScheduledExecutorService executor, Clock clock) {

        this.configuration = configuration;
        this.sweep = sweep;
        this.executor = executor;
        this.clock = clock;
    }

    public synchronized void start() {

        if (started || closed || !configuration.isConsentExpiryEnabled()) {
            return;
        }
        mode = configuration.getConsentExpiryScheduleMode();
        if (!"daily".equals(mode) && !"interval".equals(mode)) {
            throw new IllegalArgumentException("Consent expiry schedule_mode must be daily or interval.");
        }
        dailyTime = LocalTime.parse(configuration.getConsentExpiryDailyTime());
        zone = ZoneId.of(configuration.getConsentExpiryTimezone());
        intervalSeconds = configuration.getConsentExpiryIntervalSeconds();
        if ("interval".equals(mode) && intervalSeconds <= 0) {
            throw new IllegalArgumentException("Consent expiry interval_seconds must be positive in interval mode.");
        }
        if (configuration.getConsentExpiryBatchSize() <= 0 || configuration.getConsentExpiryMaxBatchesPerRun() <= 0
                || configuration.getConsentExpiryMaxRunSeconds() <= 0) {
            throw new IllegalArgumentException("Consent expiry batch size and run limits must be positive.");
        }
        started = true;
        if ("interval".equals(mode)) {
            executor.scheduleWithFixedDelay(this::runSafely, intervalSeconds, intervalSeconds, TimeUnit.SECONDS);
        } else {
            scheduleDaily(null);
        }
        LOG.info("Consent expiry scheduler started in " + mode + " mode.");
    }

    private void runSafely() {

        if (closed) {
            return;
        }
        try {
            sweep.run();
        } catch (Exception e) {
            LOG.error("Consent expiry firing failed; future firings remain scheduled.", e);
        }
    }

    private synchronized void scheduleDaily(LocalDate lastScheduledDate) {

        if (closed) {
            return;
        }
        ZonedDateTime now = clock.instant().atZone(zone);
        ZonedDateTime next = nextDaily(now, dailyTime, lastScheduledDate);
        long delay = Math.max(0, Duration.between(now.toInstant(), next.toInstant()).toMillis());
        executor.schedule(() -> {
            try {
                runSafely();
            } finally {
                scheduleDaily(next.toLocalDate());
            }
        }, delay, TimeUnit.MILLISECONDS);
    }

    /** atZone shifts DST gaps forward and chooses the earlier offset during overlaps. */
    static ZonedDateTime nextDaily(ZonedDateTime now, LocalTime time, LocalDate lastScheduledDate) {

        LocalDate date = now.toLocalDate();
        if (lastScheduledDate != null && !date.isAfter(lastScheduledDate)) {
            date = lastScheduledDate.plusDays(1);
        }
        ZonedDateTime next = date.atTime(time).atZone(now.getZone());
        if (!next.isAfter(now)) {
            next = date.plusDays(1).atTime(time).atZone(now.getZone());
        }
        return next;
    }

    @Override
    public void close() {

        synchronized (this) {
            closed = true;
        }
        // Interrupt the sweep between candidates; each in-progress transaction still owns its cleanup.
        executor.shutdownNow();
        try {
            if (!executor.awaitTermination(SHUTDOWN_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                LOG.error("Consent expiry scheduler did not terminate within the shutdown timeout.");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
