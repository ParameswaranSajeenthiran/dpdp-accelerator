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
import org.quartz.Scheduler;
import org.quartz.SchedulerException;
import org.quartz.impl.StdSchedulerFactory;
import org.wso2.carbon.utils.CarbonUtils;

import java.io.File;
import java.nio.file.Paths;

/**
 * Initializes and holds the {@link Scheduler} instance for the consent expiry job. Mirrors the
 * WSO2 Financial Services accelerator's {@code PeriodicalConsentJobScheduler}: if
 * {@code quartz.properties} exists in the carbon config directory, it is used - typically to turn
 * on {@code org.quartz.jobStore.isClustered = true} against a shared JDBC job store so exactly
 * one node in a cluster runs each scheduled firing. Absent that file, Quartz's own zero-config
 * default applies - an in-memory, single-node scheduler, which is all a non-clustered install
 * needs.
 */
public final class ConsentExpiryJobScheduler {

    private static final String QUARTZ_PROPERTY_FILE = "quartz.properties";
    private static final Log LOG = LogFactory.getLog(ConsentExpiryJobScheduler.class);

    private static volatile ConsentExpiryJobScheduler instance;
    private static volatile Scheduler scheduler;

    private ConsentExpiryJobScheduler() {

        initScheduler();
    }

    public static ConsentExpiryJobScheduler getInstance() {

        if (instance == null) {
            synchronized (ConsentExpiryJobScheduler.class) {
                if (instance == null) {
                    instance = new ConsentExpiryJobScheduler();
                }
            }
        }
        return instance;
    }

    private void initScheduler() {

        try {
            String quartzConfigFile = Paths.get(CarbonUtils.getCarbonConfigDirPath()).toString() + File.separator
                    + QUARTZ_PROPERTY_FILE;
            if (new File(quartzConfigFile).exists()) {
                StdSchedulerFactory schedulerFactory = new StdSchedulerFactory();
                schedulerFactory.initialize(quartzConfigFile);
                scheduler = schedulerFactory.getScheduler();
            } else {
                scheduler = StdSchedulerFactory.getDefaultScheduler();
            }
            scheduler.start();
        } catch (SchedulerException e) {
            LOG.error("Error while initializing the consent expiry job scheduler.", e);
        }
    }

    public Scheduler getScheduler() {

        return scheduler;
    }
}
