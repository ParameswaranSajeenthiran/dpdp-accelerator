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

import org.apache.axiom.om.OMElement;
import org.apache.axiom.om.OMException;
import org.apache.axiom.om.impl.builder.StAXOMBuilder;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.wso2.carbon.utils.CarbonUtils;
import org.wso2.dpdp.accelerator.common.constant.DPDPCommonConstants;
import org.wso2.dpdp.accelerator.common.exception.DPDPCommonRuntimeException;
import org.wso2.securevault.SecretResolver;
import org.wso2.securevault.SecretResolverFactory;
import org.wso2.securevault.commons.MiscellaneousUtil;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Optional;
import java.util.Stack;

import javax.xml.stream.XMLStreamException;

/**
 * Config parser for {@code dpdp-accelerator.xml}: reads the file once into a flat,
 * dot-joined-key {@code Map<String, Object>}, then exposes typed getters with defaults over it.
 * A value can be stored in the Secure Vault instead of in plain text, using the usual
 * {@code svns:secretAlias="..."} attribute on its element.
 */
public final class DPDPConfigParser {

    private static final Log LOG = LogFactory.getLog(DPDPConfigParser.class);
    private static final Object LOCK = new Object();
    private static DPDPConfigParser parser;

    private final Map<String, Object> configuration = new HashMap<>();
    private SecretResolver secretResolver;

    private DPDPConfigParser() {

        buildConfiguration();
        LOG.debug("Loaded " + DPDPCommonConstants.CONFIG_FILE_NAME + " with " + configuration.size()
                + " configured value(s).");
    }

    public static DPDPConfigParser getInstance() {

        synchronized (LOCK) {
            if (parser == null) {
                parser = new DPDPConfigParser();
            }
        }
        return parser;
    }

    public Map<String, Object> getConfiguration() {

        return Collections.unmodifiableMap(configuration);
    }

    private void buildConfiguration() {

        File configXml = new File(CarbonUtils.getCarbonConfigDirPath(), DPDPCommonConstants.CONFIG_FILE_NAME);
        try (InputStream inStream = openConfigFile(configXml)) {
            StAXOMBuilder builder = new StAXOMBuilder(inStream);
            OMElement rootElement = builder.getDocumentElement();
            secretResolver = SecretResolverFactory.create(rootElement, true);
            readChildElements(rootElement, new Stack<>());
        } catch (IOException | XMLStreamException | OMException e) {
            LOG.error("Error occurred while building configuration from " + DPDPCommonConstants.CONFIG_FILE_NAME
                    + ". If this accelerator was upgraded in place, re-run bin/merge.sh so the template that "
                    + "renders this file is present.", e);
            throw new DPDPCommonRuntimeException("Error occurred while building configuration from "
                    + DPDPCommonConstants.CONFIG_FILE_NAME, e);
        }
    }

    private InputStream openConfigFile(File configXml) throws IOException {

        if (!configXml.exists()) {
            throw new FileNotFoundException("DPDP accelerator configuration not found at: " + configXml);
        }
        return Files.newInputStream(configXml.toPath());
    }

    private void readChildElements(OMElement parent, Stack<String> nameStack) {

        for (Iterator<OMElement> children = parent.getChildElements(); children.hasNext();) {
            OMElement element = children.next();
            nameStack.push(element.getLocalName());
            String text = element.getText();
            if (text != null && !text.trim().isEmpty()) {
                text = text.trim();
                if (secretResolver != null && secretResolver.isInitialized()) {
                    // A no-op for elements without a secretAlias attribute - returns the
                    // plain text unchanged.
                    text = MiscellaneousUtil.resolve(element, secretResolver);
                }
                configuration.put(String.join(".", nameStack), text);
            }
            readChildElements(element, nameStack);
            nameStack.pop();
        }
    }

    private Optional<String> getConfigurationAsString(String key) {

        return Optional.ofNullable((String) configuration.get(key));
    }

    public boolean isConsentPortalProvisioningEnabled() {

        return getConfigurationAsString(DPDPCommonConstants.CONSENT_PORTAL_AUTO_PROVISIONING_ENABLED)
                .map(Boolean::parseBoolean).orElse(true);
    }

    public String getConsentPortalClientId() {

        return getConfigurationAsString(DPDPCommonConstants.CONSENT_PORTAL_CLIENT_ID)
                .orElse("DPDP_CONSENT_PORTAL");
    }

    public boolean isConsentHistoryEnabled() {

        return getConfigurationAsString(DPDPCommonConstants.CONSENT_HISTORY_ENABLED)
                .map(Boolean::parseBoolean).orElse(true);
    }

    public boolean isConsentHistorySnapshotEnabled() {

        return getConfigurationAsString(DPDPCommonConstants.CONSENT_HISTORY_SNAPSHOT_ENABLED)
                .map(Boolean::parseBoolean).orElse(true);
    }

    public String getConsentHistoryDataSourceName() {

        return getConfigurationAsString(DPDPCommonConstants.CONSENT_HISTORY_DATA_SOURCE_NAME)
                .orElse(DPDPCommonConstants.DEFAULT_CONSENT_HISTORY_DATA_SOURCE_NAME);
    }
}
