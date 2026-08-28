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

package org.wso2.dpdp.accelerator.complaint.mgt.service.util;

import org.testng.annotations.AfterMethod;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;
import org.wso2.dpdp.common.config.ConfigProvider;

import java.io.IOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

class AttachmentPolicyTest {

    @AfterMethod
    void resetConfigProvider() {
        ConfigProvider.resetForTesting();
        System.clearProperty("deployment.config.path");
    }

    private void useDeploymentToml(Path tempDir, String maxSizeBytes) throws IOException {
        Path tomlFile = tempDir.resolve("deployment.toml");
        try (Writer writer = Files.newBufferedWriter(tomlFile, StandardCharsets.UTF_8)) {
            writer.write("[attachment]\nmaxSizeBytes = \"" + maxSizeBytes + "\"\n");
        }
        System.setProperty("deployment.config.path", tomlFile.toString());
        ConfigProvider.resetForTesting();
    }

    @DataProvider(name = "documentedContentTypes")
    Object[][] documentedContentTypes() {
        return new Object[][] {
                { "application/pdf" },
                { "application/vnd.openxmlformats-officedocument.wordprocessingml.document" },
                { "image/png" },
                { "image/jpeg" }
        };
    }

    @Test(dataProvider = "documentedContentTypes")
    void allowsEachDocumentedContentType(String contentType) {
        assertTrue(AttachmentPolicy.isAllowedContentType(contentType));
    }

    @Test
    void rejectsUnknownOrNullContentType() {
        assertFalse(AttachmentPolicy.isAllowedContentType("application/zip"));
        assertFalse(AttachmentPolicy.isAllowedContentType(null));
    }

    @Test
    void isAllowedContentTypeTrimsWhitespace() {
        assertTrue(AttachmentPolicy.isAllowedContentType("  image/png  "));
    }

    @Test
    void defaultMaxSizeIsTenMegabytes() {
        assertEquals(10L * 1024 * 1024, AttachmentPolicy.getMaxSizeBytes());
    }

    @Test
    void usesConfiguredMaxSizeWhenDeploymentTomlSetsIt() throws IOException {
        Path tempDir = Files.createTempDirectory("attachment-policy-test");
        useDeploymentToml(tempDir, "2048");

        assertEquals(2048L, AttachmentPolicy.getMaxSizeBytes());
    }

    @Test
    void fallsBackToDefaultWhenConfiguredValueIsNotAValidNumber() throws IOException {
        Path tempDir = Files.createTempDirectory("attachment-policy-test");
        useDeploymentToml(tempDir, "not-a-number");

        assertEquals(10L * 1024 * 1024, AttachmentPolicy.getMaxSizeBytes());
    }
}
