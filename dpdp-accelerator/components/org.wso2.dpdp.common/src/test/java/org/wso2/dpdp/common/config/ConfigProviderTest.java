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

package org.wso2.dpdp.common.config;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * ConfigProvider caches its parsed deployment.toml on first use for the lifetime of the JVM -
 * resetForTesting() (package-private, test-only) forces every test here to reparse instead of
 * reusing whatever a previous test already cached, mirroring PriorityMapperTest's identical
 * need to make static-state tests order-independent.
 */
class ConfigProviderTest {

    @AfterEach
    void resetCache() {
        ConfigProvider.resetForTesting();
        System.clearProperty("deployment.config.path");
    }

    @Test
    void returnsTheDefaultWhenNoDeploymentConfigPathIsSet() {
        assertEquals("fallback", ConfigProvider.getString("some.unconfigured.key.for.this.test", "fallback"));
    }

    @Test
    void readsANestedDottedKeyFromTheConfiguredDeploymentToml(@TempDir Path tempDir) throws IOException {
        Path tomlFile = tempDir.resolve("deployment.toml");
        writeToml(tomlFile, "[configProviderTest]\nreadsANestedDottedKey = \"resolved-value\"\n");
        System.setProperty("deployment.config.path", tomlFile.toString());

        assertEquals("resolved-value",
                ConfigProvider.getString("configProviderTest.readsANestedDottedKey", "fallback"));
    }

    private void writeToml(Path path, String content) throws IOException {
        try (Writer writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
            writer.write(content);
        }
    }
}
