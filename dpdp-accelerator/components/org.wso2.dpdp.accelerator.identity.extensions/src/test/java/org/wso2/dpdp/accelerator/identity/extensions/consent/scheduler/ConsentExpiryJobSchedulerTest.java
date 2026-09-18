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

import org.mockito.ArgumentCaptor;
import org.testng.annotations.Test;
import org.wso2.dpdp.accelerator.common.config.DPDPConfigurationService;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.expectThrows;

public class ConsentExpiryJobSchedulerTest {

    @Test
    public void dailyUsesCalendarTimeAcrossDstGapAndOverlap() {

        ZoneId zone = ZoneId.of("Europe/Berlin");
        ZonedDateTime gap = ConsentExpiryJobScheduler.nextDaily(
                LocalDate.of(2026, 3, 29).atStartOfDay(zone), LocalTime.of(2, 30), null);
        assertEquals(gap.getHour(), 3);
        assertEquals(gap.getMinute(), 30);
        ZonedDateTime overlap = ConsentExpiryJobScheduler.nextDaily(
                LocalDate.of(2026, 10, 25).atStartOfDay(zone), LocalTime.of(2, 30), null);
        assertEquals(overlap.getOffset().getTotalSeconds(), 7200);
        ZonedDateTime following = ConsentExpiryJobScheduler.nextDaily(overlap, LocalTime.of(2, 30),
                overlap.toLocalDate());
        assertEquals(following.toLocalDate(), LocalDate.of(2026, 10, 26));
    }

    @Test
    public void dailyReschedulesAfterFailureAndDoesNotRescheduleAfterClose() throws Exception {

        DPDPConfigurationService config = configuration("daily");
        ScheduledExecutorService executor = mock(ScheduledExecutorService.class);
        Runnable sweep = mock(Runnable.class);
        doThrow(new IllegalStateException("transient failure")).when(sweep).run();
        ConsentExpiryJobScheduler scheduler = scheduler(config, sweep, executor);
        scheduler.start();
        scheduler.start();
        ArgumentCaptor<Runnable> task = ArgumentCaptor.forClass(Runnable.class);
        verify(executor).schedule(task.capture(), eq(86400000L), eq(TimeUnit.MILLISECONDS));
        task.getValue().run();
        verify(executor, times(2)).schedule(any(Runnable.class), anyLong(), eq(TimeUnit.MILLISECONDS));
        when(executor.awaitTermination(30, TimeUnit.SECONDS)).thenReturn(true);
        scheduler.close();
        task.getValue().run();
        verify(executor, times(2)).schedule(any(Runnable.class), anyLong(), eq(TimeUnit.MILLISECONDS));
        verify(executor).shutdownNow();
    }

    @Test
    public void intervalUsesFixedDelayAndSurvivesFailure() {

        ScheduledExecutorService executor = mock(ScheduledExecutorService.class);
        Runnable sweep = mock(Runnable.class);
        doThrow(new IllegalStateException("failure")).when(sweep).run();
        ConsentExpiryJobScheduler scheduler = scheduler(configuration("interval"), sweep, executor);
        scheduler.start();
        ArgumentCaptor<Runnable> task = ArgumentCaptor.forClass(Runnable.class);
        verify(executor).scheduleWithFixedDelay(task.capture(), eq(60L), eq(60L), eq(TimeUnit.SECONDS));
        task.getValue().run();
        task.getValue().run();
        verify(sweep, times(2)).run();
    }

    @Test
    public void disabledDoesNotScheduleAndInvalidSettingsFail() {

        ScheduledExecutorService executor = mock(ScheduledExecutorService.class);
        DPDPConfigurationService config = configuration("daily");
        when(config.isConsentExpiryEnabled()).thenReturn(false);
        scheduler(config, () -> { }, executor).start();
        verify(executor, never()).schedule(any(Runnable.class), anyLong(), any());
        config = configuration("unsupported");
        ConsentExpiryJobScheduler invalid = scheduler(config, () -> { }, executor);
        expectThrows(IllegalArgumentException.class, invalid::start);
        config = configuration("interval");
        when(config.getConsentExpiryIntervalSeconds()).thenReturn(0);
        ConsentExpiryJobScheduler missingInterval = scheduler(config, () -> { }, executor);
        expectThrows(IllegalArgumentException.class, missingInterval::start);
        config = configuration("daily");
        when(config.getConsentExpiryBatchSize()).thenReturn(0);
        ConsentExpiryJobScheduler invalidBatch = scheduler(config, () -> { }, executor);
        expectThrows(IllegalArgumentException.class, invalidBatch::start);
    }

    private ConsentExpiryJobScheduler scheduler(DPDPConfigurationService config, Runnable sweep,
            ScheduledExecutorService executor) {

        return new ConsentExpiryJobScheduler(config, sweep, executor,
                Clock.fixed(Instant.parse("2026-09-16T00:00:00Z"), ZoneId.of("UTC")));
    }

    private DPDPConfigurationService configuration(String mode) {

        DPDPConfigurationService config = mock(DPDPConfigurationService.class);
        when(config.isConsentExpiryEnabled()).thenReturn(true);
        when(config.getConsentExpiryScheduleMode()).thenReturn(mode);
        when(config.getConsentExpiryDailyTime()).thenReturn("00:00");
        when(config.getConsentExpiryTimezone()).thenReturn("UTC");
        when(config.getConsentExpiryIntervalSeconds()).thenReturn(60);
        when(config.getConsentExpiryBatchSize()).thenReturn(100);
        when(config.getConsentExpiryMaxBatchesPerRun()).thenReturn(1000);
        when(config.getConsentExpiryMaxRunSeconds()).thenReturn(300);
        return config;
    }
}
