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

import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

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
                // Enabled is deliberately left unset, to exercise the default-fallback path.
                + "</ConsentPortal>"
                + "</DPDPAccelerator>").getBytes());
        System.setProperty("carbon.config.dir.path", configDir.toString());
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
    public void fallsBackToConsentHistoryDefaultsWhenKeysAreAbsent() {

        DPDPConfigParser parser = DPDPConfigParser.getInstance();
        assertTrue(parser.isConsentHistoryEnabled());
        assertTrue(parser.isConsentHistorySnapshotEnabled());
        assertEquals(parser.getConsentHistoryDataSourceName(), "jdbc/WSO2DPDP_DB");
    }

    @Test
    public void configurationServiceDelegatesToTheSameParser() {

        DPDPConfigurationService service = new DPDPConfigurationServiceImpl();
        assertEquals(service.getConsentPortalClientId(), CUSTOM_CLIENT_ID);
        assertTrue(service.isConsentPortalProvisioningEnabled());
        assertTrue(service.isConsentHistoryEnabled());
        assertTrue(service.isConsentHistorySnapshotEnabled());
        assertEquals(service.getConsentHistoryDataSourceName(), "jdbc/WSO2DPDP_DB");
    }
}
