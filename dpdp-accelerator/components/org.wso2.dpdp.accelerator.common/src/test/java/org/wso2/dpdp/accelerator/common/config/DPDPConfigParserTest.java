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

package org.wso2.dpdp.accelerator.common.config;

import org.wso2.dpdp.accelerator.common.test.CarbonTestEnvironment;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.expectThrows;

/**
 * Writes a real {@code dpdp-accelerator.xml} to a temp "carbon config dir" and parses it for
 * real - {@link DPDPConfigParser} is a singleton read once per JVM, so this class exercises
 * both the configured-value and default-fallback paths from the same instance rather than
 * trying to re-initialize it under different content.
 */
public class DPDPConfigParserTest {

    private static final String CUSTOM_CLIENT_ID = "CUSTOM_TEST_CLIENT_ID";

    @BeforeClass
    public void writeConfigFileAndSetCarbonConfigDir() throws IOException {

        Path configDir = Files.createTempDirectory("dpdp-config-test");
        Path configFile = configDir.resolve("dpdp-accelerator.xml");
        Files.write(configFile, ("<DPDPAccelerator xmlns=\"http://wso2.org/projects/carbon/dpdp-accelerator.xml\">"
                + "<ConsentPortal>"
                + "<ClientId>" + CUSTOM_CLIENT_ID + "</ClientId>"
                + "</ConsentPortal>"
                + "<EventNotifications>"
                + "<ThreadPoolSize>8</ThreadPoolSize>"
                + "<BaseBackoffSeconds>12</BaseBackoffSeconds>"
                + "<MaxRetries>7</MaxRetries>"
                + "<AllowHttpCallbackUrl>false</AllowHttpCallbackUrl>"
                + "<DeliveryWorkerBatchSize>25</DeliveryWorkerBatchSize>"
                + "<DeliveryWorkerPollSeconds>9</DeliveryWorkerPollSeconds>"
                + "<StuckInFlightThresholdSeconds>15</StuckInFlightThresholdSeconds>"
                + "<MaxVerificationResponseBodyBytes>8192</MaxVerificationResponseBodyBytes>"
                + "<PendingSubscriptionRecoveryThresholdSeconds>90</PendingSubscriptionRecoveryThresholdSeconds>"
                + "<BackgroundWorkerInitialDelaySeconds>11</BackgroundWorkerInitialDelaySeconds>"
                + "<PendingSubscriptionRecoveryIntervalSeconds>31</PendingSubscriptionRecoveryIntervalSeconds>"
                + "<PendingSubscriptionRecoveryBatchSize>21</PendingSubscriptionRecoveryBatchSize>"
                + "<WorkerShutdownTimeoutSeconds>6</WorkerShutdownTimeoutSeconds>"
                + "</EventNotifications>"
                + "</DPDPAccelerator>").getBytes());
        CarbonTestEnvironment.configure(configDir);
    }

    @Test
    public void readsConfiguredValueFromXml() {

        assertEquals(DPDPConfigParser.getInstance().getConsentPortalClientId(), CUSTOM_CLIENT_ID);
    }

    @Test
    public void fallsBackToDefaultWhenKeyIsAbsent() {

        assertTrue(DPDPConfigParser.getInstance().isConsentPortalProvisioningEnabled());
    }

    @Test
    public void configurationServiceDelegatesToTheSameParser() {

        DPDPConfigurationService service = new DPDPConfigurationServiceImpl();
        assertEquals(service.getConsentPortalClientId(), CUSTOM_CLIENT_ID);
        assertTrue(service.isConsentPortalProvisioningEnabled());
        assertEquals(service.getEventNotificationThreadPoolSize(), 8);
        assertEquals(service.getEventNotificationBaseBackoffSeconds(), 12L);
        assertEquals(service.getEventNotificationMaxRetries(), 7);
        assertTrue(!service.isEventNotificationHttpCallbackUrlAllowed());
        assertEquals(service.getEventNotificationDeliveryWorkerBatchSize(), 25);
        assertEquals(service.getEventNotificationDeliveryWorkerPollSeconds(), 9);
        assertEquals(service.getEventNotificationStuckInFlightThresholdSeconds(), 15);
        assertEquals(service.getEventNotificationMaxVerificationResponseBodyBytes(), 8192);
        assertEquals(service.getEventNotificationPendingSubscriptionRecoveryThresholdSeconds(), 90);
        assertEquals(service.getEventNotificationBackgroundWorkerInitialDelaySeconds(), 11);
        assertEquals(service.getEventNotificationPendingSubscriptionRecoveryIntervalSeconds(), 31);
        assertEquals(service.getEventNotificationPendingSubscriptionRecoveryBatchSize(), 21);
        assertEquals(service.getEventNotificationWorkerShutdownTimeoutSeconds(), 6);
    }

    @Test
    public void configurationServiceUsesTypedDefaultsWhenParserIsUnavailable() {

        DPDPConfigurationService service = new DPDPConfigurationServiceImpl(false);
        assertEquals(service.getConfigurations().size(), 0);
        assertEquals(service.getEventNotificationThreadPoolSize(), 4);
        assertEquals(service.getEventNotificationBaseBackoffSeconds(), 5L);
        assertEquals(service.getEventNotificationMaxRetries(), 5);
        assertTrue(service.isEventNotificationHttpCallbackUrlAllowed());
        assertEquals(service.getEventNotificationDeliveryWorkerBatchSize(), 50);
        assertEquals(service.getEventNotificationDeliveryWorkerPollSeconds(), 5);
        assertEquals(service.getEventNotificationStuckInFlightThresholdSeconds(), 10);
        assertEquals(service.getEventNotificationMaxVerificationResponseBodyBytes(), 4096);
        assertEquals(service.getEventNotificationPendingSubscriptionRecoveryThresholdSeconds(), 60);
        assertEquals(service.getEventNotificationBackgroundWorkerInitialDelaySeconds(), 10);
        assertEquals(service.getEventNotificationPendingSubscriptionRecoveryIntervalSeconds(), 30);
        assertEquals(service.getEventNotificationPendingSubscriptionRecoveryBatchSize(), 20);
        assertEquals(service.getEventNotificationWorkerShutdownTimeoutSeconds(), 5);
    }

    @Test
    public void configurationServiceValidatesConfiguredTypesAndRanges() throws Exception {
        DPDPConfigurationServiceImpl service = new DPDPConfigurationServiceImpl();
        DPDPConfigParser parser = DPDPConfigParser.getInstance();
        Field configurationField = DPDPConfigParser.class.getDeclaredField("configuration");
        configurationField.setAccessible(true);
        @SuppressWarnings("unchecked")
        Map<String, Object> values = (Map<String, Object>) configurationField.get(parser);
        Map<String, Object> backup = new HashMap<>(values);
        values.clear();
        values.put("EventNotifications.ThreadPoolSize", "8");
        values.put("EventNotifications.BaseBackoffSeconds", "12");
        values.put("EventNotifications.MaxRetries", "3");
        values.put("EventNotifications.AllowHttpCallbackUrl", "false");
        values.put("EventNotifications.DeliveryWorkerBatchSize", "10");
        values.put("EventNotifications.DeliveryWorkerPollSeconds", "2");
        values.put("EventNotifications.StuckInFlightThresholdSeconds", "0");
        values.put("EventNotifications.MaxVerificationResponseBodyBytes", "2048");
        values.put("EventNotifications.PendingSubscriptionRecoveryThresholdSeconds", "0");
        values.put("EventNotifications.BackgroundWorkerInitialDelaySeconds", "0");
        values.put("EventNotifications.PendingSubscriptionRecoveryIntervalSeconds", "30");
        values.put("EventNotifications.PendingSubscriptionRecoveryBatchSize", "20");
        values.put("EventNotifications.WorkerShutdownTimeoutSeconds", "5");
        try {
            assertEquals(service.getEventNotificationThreadPoolSize(), 8);
        assertEquals(service.getEventNotificationBaseBackoffSeconds(), 12L);
        assertEquals(service.getEventNotificationMaxRetries(), 3);
        assertTrue(!service.isEventNotificationHttpCallbackUrlAllowed());
        assertEquals(service.getEventNotificationDeliveryWorkerBatchSize(), 10);
        assertEquals(service.getEventNotificationDeliveryWorkerPollSeconds(), 2);
        assertEquals(service.getEventNotificationStuckInFlightThresholdSeconds(), 0);
        assertEquals(service.getEventNotificationMaxVerificationResponseBodyBytes(), 2048);
        assertEquals(service.getEventNotificationPendingSubscriptionRecoveryThresholdSeconds(), 0);
        assertEquals(service.getEventNotificationBackgroundWorkerInitialDelaySeconds(), 0);
        assertEquals(service.getEventNotificationPendingSubscriptionRecoveryIntervalSeconds(), 30);
        assertEquals(service.getEventNotificationPendingSubscriptionRecoveryBatchSize(), 20);
        assertEquals(service.getEventNotificationWorkerShutdownTimeoutSeconds(), 5);

        values.put("EventNotifications.ThreadPoolSize", "0");
        expectThrows(IllegalStateException.class, service::getEventNotificationThreadPoolSize);
            values.put("EventNotifications.ThreadPoolSize", "bad");
            expectThrows(IllegalStateException.class, service::getEventNotificationThreadPoolSize);
            values.put("EventNotifications.AllowHttpCallbackUrl", "bad");
            expectThrows(IllegalStateException.class, service::isEventNotificationHttpCallbackUrlAllowed);
            values.put("EventNotifications.PendingSubscriptionRecoveryIntervalSeconds", "0");
            expectThrows(IllegalStateException.class,
                    service::getEventNotificationPendingSubscriptionRecoveryIntervalSeconds);
        } finally {
            values.clear();
            values.putAll(backup);
        }
    }
}
