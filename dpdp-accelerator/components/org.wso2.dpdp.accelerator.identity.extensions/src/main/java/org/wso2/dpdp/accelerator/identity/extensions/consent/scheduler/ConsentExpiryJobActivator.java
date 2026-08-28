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
import org.quartz.CronScheduleBuilder;
import org.quartz.JobDetail;
import org.quartz.Scheduler;
import org.quartz.SchedulerException;
import org.quartz.Trigger;
import org.wso2.dpdp.accelerator.identity.extensions.internal.DPDPIdentityExtensionDataHolder;

import static org.quartz.JobBuilder.newJob;
import static org.quartz.TriggerBuilder.newTrigger;

/**
 * Builds and schedules the {@link ConsentExpiryJob} trigger from {@code ConsentExpiry.CronValue},
 * mirroring the WSO2 Financial Services accelerator's {@code PeriodicalConsentJobActivator}.
 */
public class ConsentExpiryJobActivator {

    private static final Log LOG = LogFactory.getLog(ConsentExpiryJobActivator.class);

    public void activate() {

        if (!DPDPIdentityExtensionDataHolder.getInstance().getConfigurationService().isConsentExpiryEnabled()) {
            LOG.debug("Consent expiry job is disabled; not scheduling it.");
            return;
        }

        String cronValue = DPDPIdentityExtensionDataHolder.getInstance().getConfigurationService()
                .getConsentExpiryCronValue();
        JobDetail job = newJob(ConsentExpiryJob.class)
                .withIdentity("DPDPConsentExpiryJob", "dpdp-accelerator")
                .build();
        Trigger trigger = newTrigger()
                .withIdentity("DPDPConsentExpiryTrigger", "dpdp-accelerator")
                .withSchedule(CronScheduleBuilder.cronSchedule(cronValue))
                .build();

        try {
            Scheduler scheduler = ConsentExpiryJobScheduler.getInstance().getScheduler();
            // Removes a job left over from a previous activation under a clustered, DB-backed
            // job store - the job/trigger otherwise persists across restarts.
            if (scheduler.checkExists(job.getKey())) {
                scheduler.deleteJob(job.getKey());
            }
            scheduler.scheduleJob(job, trigger);
            LOG.info("Consent expiry job scheduled with cron: " + sanitize(cronValue));
        } catch (SchedulerException e) {
            LOG.error("Error while scheduling the consent expiry job.", e);
        }
    }

    private static String sanitize(String value) {

        return value == null ? null : value.replaceAll("[\r\n]", "");
    }
}
