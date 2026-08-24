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

import org.tomlj.Toml;
import org.tomlj.TomlParseResult;

import java.io.File;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Read-only access to deployment.toml by dotted key path (e.g. "datasource.ComplaintDB.url"),
 * shared across every accelerator module so each one doesn't reparse the file on its own. Parsed
 * lazily, once, on first use, and cached for the lifetime of the JVM - deployment.toml is read at
 * startup in every real deployment, never hot-reloaded.
 *
 * <p>Looks for deployment.toml in, in order: an explicit -Ddeployment.config.path override, the
 * CO_DEPLOYMENT_CONFIG_PATH env var, the current working directory (repo root during local dev),
 * the Identity Server's own repository/conf/deployment.toml (found via the carbon.home system
 * property wso2server.sh always sets), and finally $CATALINA_BASE or $CATALINA_HOME conf/
 * directories so a plain Tomcat deployment can pick it up from a mounted volume.
 */
public final class ConfigProvider {

    private static final Logger LOGGER = Logger.getLogger(ConfigProvider.class.getName());

    private static volatile boolean loaded;
    private static volatile TomlParseResult config;

    private ConfigProvider() {
    }

    /**
     * Returns the value at {@code dottedKey} in deployment.toml (tomlj resolves a dotted key
     * through nested tables directly), or {@code defaultValue} if deployment.toml could not be
     * found/parsed, or the key isn't present.
     */
    public static String getString(String dottedKey, String defaultValue) {
        TomlParseResult toml = getConfig();
        if (toml == null || dottedKey == null || dottedKey.isEmpty()) {
            return defaultValue;
        }
        String value = toml.getString(dottedKey);
        return value != null ? value : defaultValue;
    }

    /** Test-only seam: forces the next getString() call to reparse rather than reuse the cached result. */
    static void resetForTesting() {
        loaded = false;
        config = null;
    }

    private static TomlParseResult getConfig() {
        if (!loaded) {
            synchronized (ConfigProvider.class) {
                if (!loaded) {
                    config = loadDeploymentConfig();
                    loaded = true;
                }
            }
        }
        return config;
    }

    private static TomlParseResult loadDeploymentConfig() {
        File file = resolveDeploymentConfigFile();
        if (file == null) {
            LOGGER.info("No deployment.toml found; ConfigProvider will return every caller's own default.");
            return null;
        }

        try {
            TomlParseResult result = Toml.parse(file.toPath());
            if (result.hasErrors()) {
                result.errors().forEach(error -> LOGGER.warning("deployment.toml parse error: " + error));
                return null;
            }
            LOGGER.info("ConfigProvider loaded configuration from: " + file.getAbsolutePath());
            return result;
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Could not read deployment.toml: " + e.getMessage(), e);
            return null;
        }
    }

    private static File resolveDeploymentConfigFile() {
        String explicitPath = System.getProperty("deployment.config.path", System.getenv("CO_DEPLOYMENT_CONFIG_PATH"));
        if (explicitPath != null) {
            File file = new File(explicitPath);
            if (file.isFile()) {
                return file;
            }
        }

        File cwdFile = new File("deployment.toml");
        if (cwdFile.isFile()) {
            return cwdFile;
        }

        String carbonHome = System.getProperty("carbon.home");
        if (carbonHome != null) {
            File file = new File(carbonHome, "repository/conf/deployment.toml");
            if (file.isFile()) {
                return file;
            }
        }

        for (String catalinaDir : new String[]{System.getenv("CATALINA_BASE"), System.getenv("CATALINA_HOME")}) {
            if (catalinaDir == null) {
                continue;
            }
            File file = new File(catalinaDir, "conf/deployment.toml");
            if (file.isFile()) {
                return file;
            }
        }
        return null;
    }
}
