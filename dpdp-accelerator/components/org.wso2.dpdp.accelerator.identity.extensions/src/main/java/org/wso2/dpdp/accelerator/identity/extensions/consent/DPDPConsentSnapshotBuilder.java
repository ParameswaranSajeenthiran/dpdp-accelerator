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
import com.google.gson.JsonObject;
import org.wso2.carbon.consent.mgt.core.model.ConsentAuthorization;
import org.wso2.carbon.consent.mgt.core.model.Receipt;

import java.util.List;

/**
 * Builds the JSON snapshot stored in {@code DPDP_CONSENT_HISTORY}. {@link Receipt} carries no
 * {@code authorizations} field of its own - {@link org.wso2.carbon.consent.mgt.core.ConsentManager#getConsentAuthorizations}
 * is a separate call, so its result is attached here as an extra field. Serializes the raw
 * {@link Receipt}/{@link ConsentAuthorization} objects field-for-field rather than reshaping them
 * to match the public REST API's response DTO - that mapping lives in a class this accelerator
 * has no dependency on and no visibility into.
 */
public final class DPDPConsentSnapshotBuilder {

    private static final Gson GSON = new Gson();

    private DPDPConsentSnapshotBuilder() {

    }

    public static String buildSnapshotJson(Receipt receipt, List<ConsentAuthorization> authorizations) {

        JsonObject snapshot = GSON.toJsonTree(receipt).getAsJsonObject();
        snapshot.add("authorizations", GSON.toJsonTree(authorizations));
        return GSON.toJson(snapshot);
    }
}
