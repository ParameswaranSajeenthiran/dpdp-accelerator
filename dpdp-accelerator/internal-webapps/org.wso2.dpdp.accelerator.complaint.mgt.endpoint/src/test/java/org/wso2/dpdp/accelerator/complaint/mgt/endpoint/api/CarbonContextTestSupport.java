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

package org.wso2.dpdp.accelerator.complaint.mgt.endpoint.api;

import org.wso2.carbon.context.PrivilegedCarbonContext;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * {@link PrivilegedCarbonContext} needs a minimal carbon.xml on disk to even class-init in a plain
 * JUnit JVM - see {@code CarbonTestEnvironment} in org.wso2.dpdp.accelerator.common, replicated
 * here since test-scope classes aren't shared across modules. Shared by every endpoint test in
 * this module that resolves the caller via PrivilegedCarbonContext.
 */
final class CarbonContextTestSupport {

    private CarbonContextTestSupport() {
    }

    static void configureMinimalCarbonEnvironment() throws IOException {
        String configuredPath = System.getProperty("carbon.config.dir.path");
        if (configuredPath != null && !configuredPath.trim().isEmpty()) {
            return;
        }
        Path configDir = Files.createTempDirectory("dpdp-carbon-test");
        Files.write(configDir.resolve("carbon.xml"), ("<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                + "<Server xmlns=\"http://wso2.org/projects/carbon/carbon.xml\">"
                + "<Name>WSO2 Identity Server</Name>"
                + "<ServerKey>IS</ServerKey>"
                + "<Version>7.3.0</Version>"
                + "<HostName>localhost</HostName>"
                + "<MgtHostName>localhost</MgtHostName>"
                + "<Ports><Offset>0</Offset></Ports>"
                + "<Security><NetworkAuthenticatorConfig/></Security>"
                + "</Server>").getBytes(StandardCharsets.UTF_8));
        System.setProperty("carbon.config.dir.path", configDir.toString());
    }
}
