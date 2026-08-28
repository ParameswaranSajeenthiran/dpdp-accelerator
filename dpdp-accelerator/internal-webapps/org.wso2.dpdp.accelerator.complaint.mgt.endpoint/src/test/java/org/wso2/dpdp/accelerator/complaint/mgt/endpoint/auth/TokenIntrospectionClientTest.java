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

package org.wso2.dpdp.accelerator.complaint.mgt.endpoint.auth;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.wso2.carbon.context.PrivilegedCarbonContext;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The access token is opaque - there is nothing to decode or introspect locally. Identity comes
 * entirely from {@link PrivilegedCarbonContext}, which Carbon's own valve pipeline populates
 * before this webapp ever sees the request - exercised here the same way {@code
 * DPDPTenantContextTest} does (org.wso2.dpdp.accelerator.common), via a real tenant flow rather
 * than a mock. PrivilegedCarbonContext needs a minimal carbon.xml on disk to even class-init in a
 * plain JUnit JVM - see {@code CarbonTestEnvironment} in that same module, replicated inline here
 * since test-scope classes aren't shared across modules.
 */
class TokenIntrospectionClientTest {

    private final TokenIntrospectionClient client = new TokenIntrospectionClient();

    @BeforeAll
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

    @Test
    void resolvesTheCallerFromThePrivilegedCarbonContext() {
        PrivilegedCarbonContext.startTenantFlow();
        try {
            PrivilegedCarbonContext.getThreadLocalCarbonContext().setUsername("complaint-officer");
            PrivilegedCarbonContext.getThreadLocalCarbonContext().setTenantDomain("example.com");

            AuthenticatedPrincipal principal = client.introspect("opaque-token");

            assertEquals("complaint-officer", principal.getUserId());
            assertEquals("complaint-officer", principal.getUserName());
            assertEquals("example.com", principal.getOrgId());
        } finally {
            PrivilegedCarbonContext.endTenantFlow();
        }
    }

    @Test
    void returnsNullWhenTheTokenIsNull() {
        assertNull(client.introspect(null));
    }

    @Test
    void returnsNullWhenTheTokenIsBlank() {
        assertNull(client.introspect("  "));
    }

    @Test
    void returnsNullWhenThePrivilegedCarbonContextHasNoUsername() {
        PrivilegedCarbonContext.startTenantFlow();
        try {
            assertNull(client.introspect("opaque-token"));
        } finally {
            PrivilegedCarbonContext.endTenantFlow();
        }
    }
}
