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

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.testng.annotations.Test;
import org.wso2.carbon.consent.mgt.core.model.ConsentAuthorization;
import org.wso2.carbon.consent.mgt.core.model.Receipt;

import java.util.Collections;
import java.util.List;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

public class DPDPConsentSnapshotBuilderTest {

    @Test
    public void buildSnapshotJsonIncludesReceiptFieldsAndAuthorizations() {

        Receipt receipt = new Receipt();
        receipt.setConsentReceiptId("consent-1234");
        receipt.setState("ACTIVE");
        receipt.setPiiPrincipalId("jdoe@carbon.super");
        receipt.setTenantDomain("tenant-a.com");

        ConsentAuthorization authorization = new ConsentAuthorization("consent-1234", "jdoe@carbon.super",
                ConsentAuthorization.AuthorizationStatus.APPROVED, 1755504000000L, "primary");
        List<ConsentAuthorization> authorizations = Collections.singletonList(authorization);

        String snapshotJson = DPDPConsentSnapshotBuilder.buildSnapshotJson(receipt, authorizations);

        JsonObject snapshot = JsonParser.parseString(snapshotJson).getAsJsonObject();
        assertEquals(snapshot.get("consentReceiptId").getAsString(), "consent-1234");
        assertEquals(snapshot.get("state").getAsString(), "ACTIVE");
        assertEquals(snapshot.get("piiPrincipalId").getAsString(), "jdoe@carbon.super");
        assertTrue(snapshot.has("authorizations"));
        assertEquals(snapshot.getAsJsonArray("authorizations").size(), 1);
        assertEquals(snapshot.getAsJsonArray("authorizations").get(0).getAsJsonObject().get("userId").getAsString(),
                "jdoe@carbon.super");
    }

    @Test
    public void buildSnapshotJsonHandlesEmptyAuthorizations() {

        Receipt receipt = new Receipt();
        receipt.setConsentReceiptId("consent-5678");
        receipt.setState("PENDING");

        String snapshotJson = DPDPConsentSnapshotBuilder.buildSnapshotJson(receipt, Collections.emptyList());

        JsonObject snapshot = JsonParser.parseString(snapshotJson).getAsJsonObject();
        assertEquals(snapshot.getAsJsonArray("authorizations").size(), 0);
    }
}
