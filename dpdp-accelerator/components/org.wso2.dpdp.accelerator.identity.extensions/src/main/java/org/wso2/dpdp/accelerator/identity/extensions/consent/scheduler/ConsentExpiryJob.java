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
import org.quartz.DisallowConcurrentExecution;
import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.wso2.dpdp.accelerator.identity.extensions.consent.DPDPConsentExpiryReconciler;
import org.wso2.dpdp.accelerator.identity.extensions.internal.DPDPIdentityExtensionDataHolder;

/**
 * Quartz job body for the periodical consent expiry sweep. {@code @DisallowConcurrentExecution}
 * stops Quartz itself from starting a second execution while a prior one is still running - under
 * a clustered, DB-backed job store this also prevents two nodes from running this job at once.
 */
@DisallowConcurrentExecution
public class ConsentExpiryJob implements Job {

    private static final Log LOG = LogFactory.getLog(ConsentExpiryJob.class);

    @Override
    public void execute(JobExecutionContext jobExecutionContext) {

        int batchSize = DPDPIdentityExtensionDataHolder.getInstance().getConfigurationService()
                .getConsentExpiryBatchSize();
        LOG.debug("Consent expiry job executing with batch size: " + batchSize);
        DPDPConsentExpiryReconciler.expireDueConsents(batchSize);
    }
}
