/*
 * Copyright (c) 2026, WSO2 LLC. (https://www.wso2.com).
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.wso2.dpdp.accelerator.event.notifications.service.dispatch;

import org.mockito.ArgumentCaptor;
import org.testng.annotations.Test;
import org.wso2.dpdp.accelerator.event.notifications.common.util.HmacSigner;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertEquals;

public class SignedEventPayloadFactoryTest {

    @Test
    public void signsTheSameNestedEnvelopeUsedByWebhookDelivery() throws Exception {
        EventPayloadSigner signer = mock(EventPayloadSigner.class);
        when(signer.sign(any(EventPayloadSigningContext.class))).thenReturn("header.claims.signature");
        SignedEventPayloadFactory factory = new SignedEventPayloadFactory(signer);

        String signed = factory.sign("tenant.example", "group-1", "subscription-1", "delivery-1",
                "event-1", "accounts", "{\"balance\":10}", "shared-secret", "poll-audience");

        assertEquals(signed, "header.claims.signature");
        ArgumentCaptor<EventPayloadSigningContext> captor =
                ArgumentCaptor.forClass(EventPayloadSigningContext.class);
        verify(signer).sign(captor.capture());
        EventPayloadSigningContext context = captor.getValue();
        assertEquals(context.getTenantDomain(), "tenant.example");
        assertEquals(context.getSubject(), "group-1");
        assertEquals(context.getAudience(), "poll-audience");
        assertEquals(context.getDeliveryId(), "delivery-1");
        assertEquals(context.getEventId(), "event-1");
        assertEquals(context.getPayload().get("subscriptionId").asText(), "subscription-1");
        assertEquals(context.getPayload().get("eventPayload").get("balance").asInt(), 10);
        assertEquals(context.getPayloadHash(), HmacSigner.sign("shared-secret",
                SignedEventPayloadFactory.buildEnvelopeJson("tenant.example", "group-1", "subscription-1",
                        "delivery-1", "event-1", "accounts", "{\"balance\":10}")));
    }
}
