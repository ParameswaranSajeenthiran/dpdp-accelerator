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

package org.wso2.dpdp.accelerator.identity.extensions.consent;

import java.util.List;
import java.util.Map;

/**
 * The shape stored (as JSON) in {@code DPDP_CONSENT_HISTORY}. Deliberately a trimmed-down,
 * hand-written view of {@link org.wso2.carbon.consent.mgt.core.model.Receipt} rather than a
 * field-for-field dump of it - fields like {@code publicKey}, the placeholder {@code piiController}
 * block, {@code cursorKey}, {@code jurisdiction}, and internal DB IDs carry no audit value and only
 * bloat every stored row. {@code consentId} is intentionally absent - the enclosing history entry
 * already carries it.
 */
public class DPDPConsentSnapshotDTO {

    private String state;
    private String piiPrincipalId;
    private String language;
    private Long expiryTime;
    private Map<String, String> properties;
    private List<ServiceSnapshot> services;
    private List<AuthorizationSnapshot> authorizations;

    public String getState() {

        return state;
    }

    public void setState(String state) {

        this.state = state;
    }

    public String getPiiPrincipalId() {

        return piiPrincipalId;
    }

    public void setPiiPrincipalId(String piiPrincipalId) {

        this.piiPrincipalId = piiPrincipalId;
    }

    public String getLanguage() {

        return language;
    }

    public void setLanguage(String language) {

        this.language = language;
    }

    public Long getExpiryTime() {

        return expiryTime;
    }

    public void setExpiryTime(Long expiryTime) {

        this.expiryTime = expiryTime;
    }

    public Map<String, String> getProperties() {

        return properties;
    }

    public void setProperties(Map<String, String> properties) {

        this.properties = properties;
    }

    public List<ServiceSnapshot> getServices() {

        return services;
    }

    public void setServices(List<ServiceSnapshot> services) {

        this.services = services;
    }

    public List<AuthorizationSnapshot> getAuthorizations() {

        return authorizations;
    }

    public void setAuthorizations(List<AuthorizationSnapshot> authorizations) {

        this.authorizations = authorizations;
    }

    public static class ServiceSnapshot {

        private String service;
        private String spDisplayName;
        private List<PurposeSnapshot> purposes;

        public String getService() {

            return service;
        }

        public void setService(String service) {

            this.service = service;
        }

        public String getSpDisplayName() {

            return spDisplayName;
        }

        public void setSpDisplayName(String spDisplayName) {

            this.spDisplayName = spDisplayName;
        }

        public List<PurposeSnapshot> getPurposes() {

            return purposes;
        }

        public void setPurposes(List<PurposeSnapshot> purposes) {

            this.purposes = purposes;
        }
    }

    public static class PurposeSnapshot {

        private String purpose;
        private String uuid;
        private boolean primaryPurpose;
        private List<ElementSnapshot> elements;

        public String getPurpose() {

            return purpose;
        }

        public void setPurpose(String purpose) {

            this.purpose = purpose;
        }

        public String getUuid() {

            return uuid;
        }

        public void setUuid(String uuid) {

            this.uuid = uuid;
        }

        public boolean isPrimaryPurpose() {

            return primaryPurpose;
        }

        public void setPrimaryPurpose(boolean primaryPurpose) {

            this.primaryPurpose = primaryPurpose;
        }

        public List<ElementSnapshot> getElements() {

            return elements;
        }

        public void setElements(List<ElementSnapshot> elements) {

            this.elements = elements;
        }
    }

    public static class ElementSnapshot {

        private String name;
        private String displayName;
        private boolean consented;

        public String getName() {

            return name;
        }

        public void setName(String name) {

            this.name = name;
        }

        public String getDisplayName() {

            return displayName;
        }

        public void setDisplayName(String displayName) {

            this.displayName = displayName;
        }

        public boolean isConsented() {

            return consented;
        }

        public void setConsented(boolean consented) {

            this.consented = consented;
        }
    }

    public static class AuthorizationSnapshot {

        private String userId;
        private String type;
        private String status;
        private Long updatedTime;

        public String getUserId() {

            return userId;
        }

        public void setUserId(String userId) {

            this.userId = userId;
        }

        public String getType() {

            return type;
        }

        public void setType(String type) {

            this.type = type;
        }

        public String getStatus() {

            return status;
        }

        public void setStatus(String status) {

            this.status = status;
        }

        public Long getUpdatedTime() {

            return updatedTime;
        }

        public void setUpdatedTime(Long updatedTime) {

            this.updatedTime = updatedTime;
        }
    }
}
