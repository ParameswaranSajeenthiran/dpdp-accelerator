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

package org.wso2.dpdp.accelerator.identity.extensions.consent;

import org.testng.annotations.Test;
import org.wso2.dpdp.accelerator.consent.extensions.dao.models.ConsentExpiryRecord;
import org.wso2.dpdp.accelerator.consent.extensions.service.ConsentExpiryService;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.expectThrows;

public class DPDPConsentExpiryReconcilerTest {

    @Test
    public void drains250CandidatesInThreePagesDespiteFailuresAndLostClaims() throws Exception {

        ConsentExpiryService expiry = mock(ConsentExpiryService.class);
        List<ConsentExpiryRecord> first = page(0, 100);
        List<ConsentExpiryRecord> second = page(100, 100);
        List<ConsentExpiryRecord> third = page(200, 50);
        when(expiry.findDueExpiries(5000, 100, null)).thenReturn(first);
        when(expiry.findDueExpiries(5000, 100, first.get(99))).thenReturn(second);
        when(expiry.findDueExpiries(5000, 100, second.get(99))).thenReturn(third);
        AtomicInteger attempts = new AtomicInteger();
        DPDPConsentExpiryReconciler.sweep(expiry, (candidate, cutoff) -> {
            assertEquals(cutoff, 5000);
            int count = attempts.incrementAndGet();
            if (count == 1) {
                throw new IllegalStateException("Failed candidate must not block later pages");
            }
            return count % 2 == 0;
        }, 100, 1000, 300, 5000, () -> 0);
        assertEquals(attempts.get(), 250);
        verify(expiry, times(3)).findDueExpiries(eq(5000L), eq(100), any());
    }

    @Test
    public void exactMultipleFetchesOneEmptyPage() throws Exception {

        ConsentExpiryService expiry = mock(ConsentExpiryService.class);
        List<ConsentExpiryRecord> page = page(0, 100);
        when(expiry.findDueExpiries(5000, 100, null)).thenReturn(page);
        when(expiry.findDueExpiries(5000, 100, page.get(99))).thenReturn(Collections.emptyList());
        DPDPConsentExpiryReconciler.sweep(expiry, (candidate, cutoff) -> true,
                100, 1000, 300, 5000, () -> 0);
        verify(expiry, times(2)).findDueExpiries(eq(5000L), eq(100), any());
    }

    @Test
    public void iterationCapStopsEvenWhenEveryCandidateFails() throws Exception {

        ConsentExpiryService expiry = mock(ConsentExpiryService.class);
        when(expiry.findDueExpiries(anyLong(), anyInt(), any())).thenReturn(page(0, 2));
        AtomicInteger attempts = new AtomicInteger();
        DPDPConsentExpiryReconciler.sweep(expiry, (candidate, cutoff) -> {
            attempts.incrementAndGet();
            throw new IllegalStateException("failure");
        }, 2, 3, 300, 5000, () -> 0);
        assertEquals(attempts.get(), 6);
        verify(expiry, times(3)).findDueExpiries(eq(5000L), eq(2), any());
    }

    @Test
    public void elapsedCapStopsBetweenCandidates() throws Exception {

        ConsentExpiryService expiry = mock(ConsentExpiryService.class);
        when(expiry.findDueExpiries(anyLong(), anyInt(), isNull())).thenReturn(page(0, 100));
        AtomicLong elapsed = new AtomicLong();
        AtomicInteger attempts = new AtomicInteger();
        DPDPConsentExpiryReconciler.sweep(expiry, (candidate, cutoff) -> {
            attempts.incrementAndGet();
            elapsed.set(TimeUnit.SECONDS.toNanos(300));
            return true;
        }, 100, 1000, 300, 5000, elapsed::get);
        assertEquals(attempts.get(), 1);
    }

    @Test
    public void interruptionStopsWithoutFetching() throws Exception {

        ConsentExpiryService expiry = mock(ConsentExpiryService.class);
        Thread.currentThread().interrupt();
        try {
            DPDPConsentExpiryReconciler.sweep(expiry, (candidate, cutoff) -> true,
                    100, 1000, 300, 5000, () -> 0);
            verify(expiry, times(0)).findDueExpiries(anyLong(), anyInt(), any());
        } finally {
            Thread.interrupted();
        }
    }

    @Test
    public void fetchFailureEndsRunAndInvalidLimitsAreRejected() throws Exception {

        ConsentExpiryService expiry = mock(ConsentExpiryService.class);
        when(expiry.findDueExpiries(anyLong(), anyInt(), any())).thenThrow(new IllegalStateException("DB down"));
        DPDPConsentExpiryReconciler.sweep(expiry, (candidate, cutoff) -> true,
                100, 1000, 300, 5000, () -> 0);
        verify(expiry).findDueExpiries(eq(5000L), eq(100), isNull());
        expectThrows(IllegalArgumentException.class, () -> DPDPConsentExpiryReconciler.sweep(expiry,
                (candidate, cutoff) -> true, 0, 1000, 300, 5000, () -> 0));
    }

    private List<ConsentExpiryRecord> page(int start, int size) {

        List<ConsentExpiryRecord> records = new ArrayList<>();
        for (int i = start; i < start + size; i++) {
            ConsentExpiryRecord record = new ConsentExpiryRecord();
            record.setConsentId("consent-" + i);
            record.setOrgId("carbon.super");
            record.setExpiryTime(1000);
            records.add(record);
        }
        return records;
    }
}
