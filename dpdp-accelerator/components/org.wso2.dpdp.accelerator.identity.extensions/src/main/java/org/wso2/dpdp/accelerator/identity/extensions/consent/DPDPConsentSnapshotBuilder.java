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

import com.google.gson.Gson;
import org.wso2.carbon.consent.mgt.core.model.ConsentAuthorization;
import org.wso2.carbon.consent.mgt.core.model.ConsentPurpose;
import org.wso2.carbon.consent.mgt.core.model.PIICategoryValidity;
import org.wso2.carbon.consent.mgt.core.model.Receipt;
import org.wso2.carbon.consent.mgt.core.model.ReceiptService;
import org.wso2.dpdp.accelerator.identity.extensions.consent.DPDPConsentSnapshotDTO.AuthorizationSnapshot;
import org.wso2.dpdp.accelerator.identity.extensions.consent.DPDPConsentSnapshotDTO.ElementSnapshot;
import org.wso2.dpdp.accelerator.identity.extensions.consent.DPDPConsentSnapshotDTO.PurposeSnapshot;
import org.wso2.dpdp.accelerator.identity.extensions.consent.DPDPConsentSnapshotDTO.ServiceSnapshot;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Builds the JSON snapshot stored in {@code DPDP_CONSENT_HISTORY}
 */
public final class DPDPConsentSnapshotBuilder {

    private static final Gson GSON = new Gson();

    private DPDPConsentSnapshotBuilder() {

    }

    public static String buildSnapshotJson(Receipt receipt, List<ConsentAuthorization> authorizations) {

        DPDPConsentSnapshotDTO snapshot = new DPDPConsentSnapshotDTO();
        snapshot.setState(receipt.getState());
        snapshot.setPiiPrincipalId(receipt.getPiiPrincipalId());
        snapshot.setLanguage(receipt.getLanguage());
        snapshot.setExpiryTime(receipt.getExpiryTime() == null ? null : receipt.getExpiryTime().getTime());
        snapshot.setProperties(receipt.getProperties());
        snapshot.setServices(toServiceSnapshots(receipt.getServices()));
        snapshot.setAuthorizations(toAuthorizationSnapshots(authorizations));
        return GSON.toJson(snapshot);
    }

    private static List<ServiceSnapshot> toServiceSnapshots(List<ReceiptService> services) {

        if (services == null) {
            return null;
        }
        return services.stream().map(service -> {
            ServiceSnapshot serviceSnapshot = new ServiceSnapshot();
            serviceSnapshot.setService(service.getService());
            serviceSnapshot.setSpDisplayName(service.getSpDisplayName());
            serviceSnapshot.setPurposes(toPurposeSnapshots(service.getPurposes()));
            return serviceSnapshot;
        }).collect(Collectors.toList());
    }

    private static List<PurposeSnapshot> toPurposeSnapshots(List<ConsentPurpose> purposes) {

        if (purposes == null) {
            return null;
        }
        return purposes.stream().map(purpose -> {
            PurposeSnapshot purposeSnapshot = new PurposeSnapshot();
            purposeSnapshot.setPurpose(purpose.getPurpose());
            purposeSnapshot.setUuid(purpose.getUuid());
            purposeSnapshot.setPrimaryPurpose(purpose.isPrimaryPurpose());
            purposeSnapshot.setElements(toElementSnapshots(purpose.getPiiCategory()));
            return purposeSnapshot;
        }).collect(Collectors.toList());
    }

    private static List<ElementSnapshot> toElementSnapshots(List<PIICategoryValidity> elements) {

        if (elements == null) {
            return null;
        }
        return elements.stream().map(element -> {
            ElementSnapshot elementSnapshot = new ElementSnapshot();
            elementSnapshot.setName(element.getName());
            elementSnapshot.setDisplayName(element.getDisplayName());
            elementSnapshot.setConsented(element.isConsented());
            return elementSnapshot;
        }).collect(Collectors.toList());
    }

    private static List<AuthorizationSnapshot> toAuthorizationSnapshots(List<ConsentAuthorization> authorizations) {

        if (authorizations == null) {
            return null;
        }
        return authorizations.stream().map(authorization -> {
            AuthorizationSnapshot authorizationSnapshot = new AuthorizationSnapshot();
            authorizationSnapshot.setUserId(authorization.getUserId());
            authorizationSnapshot.setType(authorization.getType());
            authorizationSnapshot.setStatus(
                    authorization.getStatus() == null ? null : authorization.getStatus().toString());
            authorizationSnapshot.setUpdatedTime(authorization.getUpdatedTime());
            return authorizationSnapshot;
        }).collect(Collectors.toList());
    }
}
