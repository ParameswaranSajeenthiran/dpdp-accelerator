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

package org.wso2.dpdp.accelerator.common.test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/** Creates the minimum Carbon configuration required by Carbon context unit tests. */
public final class CarbonTestEnvironment {

    private static final String CARBON_CONFIG_DIR_PROPERTY = "carbon.config.dir.path";
    private static final String MINIMAL_CARBON_XML =
            "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                    + "<Server xmlns=\"http://wso2.org/projects/carbon/carbon.xml\">"
                    + "<Name>WSO2 Identity Server</Name>"
                    + "<ServerKey>IS</ServerKey>"
                    + "<Version>7.3.0</Version>"
                    + "<HostName>localhost</HostName>"
                    + "<MgtHostName>localhost</MgtHostName>"
                    + "<Ports><Offset>0</Offset></Ports>"
                    + "<Security><NetworkAuthenticatorConfig/></Security>"
                    + "</Server>";

    private CarbonTestEnvironment() {
    }

    public static Path configure() throws IOException {

        String configuredPath = System.getProperty(CARBON_CONFIG_DIR_PROPERTY);
        Path configDir = configuredPath == null || configuredPath.trim().isEmpty()
                ? Files.createTempDirectory("dpdp-carbon-test")
                : Paths.get(configuredPath);
        return configure(configDir);
    }

    public static Path configure(Path configDir) throws IOException {

        Files.createDirectories(configDir);
        Files.write(configDir.resolve("carbon.xml"), MINIMAL_CARBON_XML.getBytes(StandardCharsets.UTF_8));
        System.setProperty(CARBON_CONFIG_DIR_PROPERTY, configDir.toString());
        return configDir;
    }
}
