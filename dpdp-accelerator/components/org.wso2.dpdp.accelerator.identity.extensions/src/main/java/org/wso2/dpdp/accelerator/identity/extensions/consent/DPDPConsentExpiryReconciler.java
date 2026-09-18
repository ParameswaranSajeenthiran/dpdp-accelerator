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

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.wso2.carbon.context.PrivilegedCarbonContext;
import org.wso2.dpdp.accelerator.common.config.DPDPConfigurationService;
import org.wso2.dpdp.accelerator.common.util.LogSanitizer;
import org.wso2.dpdp.accelerator.consent.extensions.dao.models.ConsentExpiryRecord;
import org.wso2.dpdp.accelerator.consent.extensions.service.ConsentExpiryService;
import org.wso2.dpdp.accelerator.identity.extensions.internal.DPDPIdentityExtensionDataHolder;

import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.LongSupplier;

/** Reconciles due consents from listener hooks and bounded, cursor-based scheduled sweeps. */
public final class DPDPConsentExpiryReconciler {

    private static final Log LOG = LogFactory.getLog(DPDPConsentExpiryReconciler.class);

    private DPDPConsentExpiryReconciler() {

    }

    public static void expireConsentIfDue(String orgId, String consentId) {

        DPDPIdentityExtensionDataHolder holder = DPDPIdentityExtensionDataHolder.getInstance();
        if (!holder.getConfigurationService().isConsentExpiryEnabled()) {
            return;
        }
        try {
            ConsentExpiryRecord candidate = holder.getConsentExpiryService().findExpiry(orgId, consentId);
            processingService(holder).process(candidate, System.currentTimeMillis());
        } catch (Exception e) {
            LOG.error("Error expiring consent: " + LogSanitizer.sanitize(consentId), e);
        }
    }

    public static void expireDueConsents(int batchSize) {

        DPDPIdentityExtensionDataHolder holder = DPDPIdentityExtensionDataHolder.getInstance();
        DPDPConfigurationService configuration = holder.getConfigurationService();
        if (!configuration.isConsentExpiryEnabled()) {
            return;
        }
        ConsentExpiryProcessingService processing = processingService(holder);
        sweep(holder.getConsentExpiryService(), (candidate, cutoff) -> {
            PrivilegedCarbonContext.startTenantFlow();
            try {
                PrivilegedCarbonContext.getThreadLocalCarbonContext().setTenantDomain(candidate.getOrgId());
                return processing.process(candidate, cutoff);
            } finally {
                PrivilegedCarbonContext.endTenantFlow();
            }
        }, batchSize, configuration.getConsentExpiryMaxBatchesPerRun(), configuration.getConsentExpiryMaxRunSeconds(),
                System.currentTimeMillis(), System::nanoTime);
    }

    private static ConsentExpiryProcessingService processingService(DPDPIdentityExtensionDataHolder holder) {

        return new ConsentExpiryProcessingService(holder.getConsentExpiryService(), holder.getConsentHistoryService(),
                holder.getPrivilegedConsentManager(), holder.getConfigurationService(),
                holder::getLifecycleEventListener);
    }

    /** Test seam keeps tenant setup at the boundary and time deterministic without a live IS instance. */
    static void sweep(ConsentExpiryService expiry, CandidateProcessor processor, int batchSize, int maxBatches,
            int maxSeconds, long cutoff, LongSupplier nanoTime) {

        if (batchSize <= 0 || maxBatches <= 0 || maxSeconds <= 0) {
            throw new IllegalArgumentException("Consent expiry batch size and run limits must be positive.");
        }
        long start = nanoTime.getAsLong();
        long budget = TimeUnit.SECONDS.toNanos(maxSeconds);
        ConsentExpiryRecord cursor = null;
        int batches = 0;
        long fetched = 0;
        long completed = 0;
        long skipped = 0;
        long failed = 0;
        String stopReason = "batch limit";
        try {
            scan:
            while (batches < maxBatches) {
                if (Thread.currentThread().isInterrupted()) {
                    stopReason = "shutdown";
                    break;
                }
                if (nanoTime.getAsLong() - start >= budget) {
                    stopReason = "elapsed-time limit";
                    break;
                }
                List<ConsentExpiryRecord> candidates = expiry.findDueExpiries(cutoff, batchSize, cursor);
                batches++;
                fetched += candidates.size();
                for (ConsentExpiryRecord candidate : candidates) {
                    if (Thread.currentThread().isInterrupted()) {
                        stopReason = "shutdown";
                        break scan;
                    }
                    if (nanoTime.getAsLong() - start >= budget) {
                        stopReason = "elapsed-time limit";
                        break scan;
                    }
                    try {
                        if (processor.process(candidate, cutoff)) {
                            completed++;
                        } else {
                            skipped++;
                        }
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        stopReason = "shutdown";
                        break scan;
                    } catch (Exception e) {
                        failed++;
                        LOG.error("Error expiring consent: " + LogSanitizer.sanitize(candidate.getConsentId()), e);
                    }
                    // Advance even after failure: leave the restored tracker for a later firing.
                    cursor = candidate;
                }
                if (candidates.size() < batchSize) {
                    stopReason = "end of scan";
                    break;
                }
            }
        } catch (Exception e) {
            stopReason = "fetch failure";
            LOG.error("Error fetching due consent expiries.", e);
        }
        String summary = "Consent expiry sweep: batches=" + batches + ", fetched=" + fetched + ", completed="
                + completed + ", skipped=" + skipped + ", failed=" + failed + ", stopped=" + stopReason + ".";
        if ("end of scan".equals(stopReason)) {
            LOG.info(summary);
        } else {
            LOG.warn(summary);
        }
    }

    @FunctionalInterface
    interface CandidateProcessor {

        boolean process(ConsentExpiryRecord candidate, long cutoff) throws Exception;
    }
}
