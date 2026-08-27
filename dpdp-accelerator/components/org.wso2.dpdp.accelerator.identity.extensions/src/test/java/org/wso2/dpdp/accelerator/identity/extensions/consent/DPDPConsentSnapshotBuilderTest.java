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
import org.wso2.carbon.consent.mgt.core.model.ConsentPurpose;
import org.wso2.carbon.consent.mgt.core.model.PIICategoryValidity;
import org.wso2.carbon.consent.mgt.core.model.Receipt;
import org.wso2.carbon.consent.mgt.core.model.ReceiptService;

import java.sql.Timestamp;
import java.util.Collections;
import java.util.List;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

public class DPDPConsentSnapshotBuilderTest {

    @Test
    public void buildSnapshotJsonIncludesReceiptFieldsAndAuthorizations() {

        Receipt receipt = new Receipt();
        receipt.setState("ACTIVE");
        receipt.setPiiPrincipalId("jdoe@carbon.super");

        ConsentAuthorization authorization = new ConsentAuthorization("consent-1234", "jdoe@carbon.super",
                ConsentAuthorization.AuthorizationStatus.APPROVED, 1755504000000L, "primary");
        List<ConsentAuthorization> authorizations = Collections.singletonList(authorization);

        String snapshotJson = DPDPConsentSnapshotBuilder.buildSnapshotJson(receipt, authorizations);

        JsonObject snapshot = JsonParser.parseString(snapshotJson).getAsJsonObject();
        assertTrue(!snapshot.has("consentReceiptId"), "consentReceiptId is redundant with the enclosing "
                + "history entry's own consentId and must not be duplicated into the snapshot");
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

    @Test
    public void buildSnapshotJsonMapsServicesPurposesAndElementsWhilstDroppingNoiseFields() {

        PIICategoryValidity element = new PIICategoryValidity(5, "email_address", true);
        element.setName("email_address");
        element.setDisplayName("Email Address");
        element.setConsented(true);

        ConsentPurpose purpose = new ConsentPurpose();
        purpose.setPurpose("DPDP Manual Test Purpose");
        purpose.setUuid("aeed17ae-8fbd-4af6-a95a-aae35319992e");
        purpose.setPrimaryPurpose(true);
        purpose.setPiiCategory(Collections.singletonList(element));

        ReceiptService service = new ReceiptService();
        service.setService("clientId");
        service.setSpDisplayName("clientId");
        service.setPurposes(Collections.singletonList(purpose));

        Receipt receipt = new Receipt();
        receipt.setState("ACTIVE");
        receipt.setPiiPrincipalId("mark@gold.com");
        receipt.setLanguage("en");
        receipt.setExpiryTime(new Timestamp(1787624977824L));
        receipt.setServices(Collections.singletonList(service));
        receipt.setPublicKey("should-be-dropped");
        receipt.setPolicyUrl("should-be-dropped");

        String snapshotJson = DPDPConsentSnapshotBuilder.buildSnapshotJson(receipt, Collections.emptyList());

        JsonObject snapshot = JsonParser.parseString(snapshotJson).getAsJsonObject();
        assertFalse(snapshot.has("publicKey"), "publicKey carries no audit value and must be dropped");
        assertFalse(snapshot.has("policyUrl"), "policyUrl carries no audit value and must be dropped");
        assertEquals(snapshot.get("expiryTime").getAsLong(), 1787624977824L);

        JsonObject serviceJson = snapshot.getAsJsonArray("services").get(0).getAsJsonObject();
        assertEquals(serviceJson.get("service").getAsString(), "clientId");

        JsonObject purposeJson = serviceJson.getAsJsonArray("purposes").get(0).getAsJsonObject();
        assertEquals(purposeJson.get("purpose").getAsString(), "DPDP Manual Test Purpose");
        assertTrue(purposeJson.get("primaryPurpose").getAsBoolean());

        JsonObject elementJson = purposeJson.getAsJsonArray("elements").get(0).getAsJsonObject();
        assertEquals(elementJson.get("name").getAsString(), "email_address");
        assertEquals(elementJson.get("displayName").getAsString(), "Email Address");
        assertTrue(elementJson.get("consented").getAsBoolean());
    }
}
