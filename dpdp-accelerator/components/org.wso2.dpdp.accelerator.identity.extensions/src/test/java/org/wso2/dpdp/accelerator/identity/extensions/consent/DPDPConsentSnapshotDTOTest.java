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

import org.testng.annotations.Test;
import org.wso2.dpdp.accelerator.identity.extensions.consent.DPDPConsentSnapshotDTO.AuthorizationSnapshot;
import org.wso2.dpdp.accelerator.identity.extensions.consent.DPDPConsentSnapshotDTO.ElementSnapshot;
import org.wso2.dpdp.accelerator.identity.extensions.consent.DPDPConsentSnapshotDTO.PurposeSnapshot;
import org.wso2.dpdp.accelerator.identity.extensions.consent.DPDPConsentSnapshotDTO.ServiceSnapshot;

import java.util.Collections;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

/**
 * {@link DPDPConsentSnapshotBuilder} is the only production caller, and it only ever writes these
 * fields (Gson serializes {@link DPDPConsentSnapshotDTO} by reading its fields directly, never its
 * getters) - this test is what actually exercises the getter/setter contract itself.
 */
public class DPDPConsentSnapshotDTOTest {

    @Test
    public void gettersReturnWhatWasSet() {

        ElementSnapshot element = new ElementSnapshot();
        element.setName("email_address");
        element.setDisplayName("Email Address");
        element.setConsented(true);
        assertEquals(element.getName(), "email_address");
        assertEquals(element.getDisplayName(), "Email Address");
        assertTrue(element.isConsented());

        PurposeSnapshot purpose = new PurposeSnapshot();
        purpose.setPurpose("DPDP Manual Test Purpose");
        purpose.setUuid("aeed17ae-8fbd-4af6-a95a-aae35319992e");
        purpose.setPrimaryPurpose(true);
        purpose.setElements(Collections.singletonList(element));
        assertEquals(purpose.getPurpose(), "DPDP Manual Test Purpose");
        assertEquals(purpose.getUuid(), "aeed17ae-8fbd-4af6-a95a-aae35319992e");
        assertTrue(purpose.isPrimaryPurpose());
        assertEquals(purpose.getElements(), Collections.singletonList(element));

        ServiceSnapshot service = new ServiceSnapshot();
        service.setService("clientId");
        service.setSpDisplayName("clientId");
        service.setPurposes(Collections.singletonList(purpose));
        assertEquals(service.getService(), "clientId");
        assertEquals(service.getSpDisplayName(), "clientId");
        assertEquals(service.getPurposes(), Collections.singletonList(purpose));

        AuthorizationSnapshot authorization = new AuthorizationSnapshot();
        authorization.setUserId("jdoe@carbon.super");
        authorization.setType("USER");
        authorization.setStatus("APPROVED");
        authorization.setUpdatedTime(1755504000000L);
        assertEquals(authorization.getUserId(), "jdoe@carbon.super");
        assertEquals(authorization.getType(), "USER");
        assertEquals(authorization.getStatus(), "APPROVED");
        assertEquals(authorization.getUpdatedTime(), (Long) 1755504000000L);

        DPDPConsentSnapshotDTO snapshot = new DPDPConsentSnapshotDTO();
        snapshot.setState("ACTIVE");
        snapshot.setPiiPrincipalId("jdoe@carbon.super");
        snapshot.setLanguage("en");
        snapshot.setExpiryTime(1787624977824L);
        snapshot.setProperties(Collections.singletonMap("region", "IN"));
        snapshot.setServices(Collections.singletonList(service));
        snapshot.setAuthorizations(Collections.singletonList(authorization));

        assertEquals(snapshot.getState(), "ACTIVE");
        assertEquals(snapshot.getPiiPrincipalId(), "jdoe@carbon.super");
        assertEquals(snapshot.getLanguage(), "en");
        assertEquals(snapshot.getExpiryTime(), (Long) 1787624977824L);
        assertEquals(snapshot.getProperties(), Collections.singletonMap("region", "IN"));
        assertEquals(snapshot.getServices(), Collections.singletonList(service));
        assertEquals(snapshot.getAuthorizations(), Collections.singletonList(authorization));
    }
}
