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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.wso2.dpdp.accelerator.event.notifications.common.util.HmacSigner;

import java.util.LinkedHashMap;
import java.util.Map;

/** Builds and signs the event envelope shared by webhook and polling delivery. */
public class SignedEventPayloadFactory {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private final EventPayloadSigner payloadSigner;

    public SignedEventPayloadFactory() {
        this(new IdentityServerPayloadSigner());
    }

    SignedEventPayloadFactory(EventPayloadSigner payloadSigner) {
        this.payloadSigner = payloadSigner;
    }

    public String sign(String orgId, String groupId, String subscriptionId, String deliveryId,
            String eventId, String topic, String rawPayload, String sharedSecret, String audience) throws Exception {
        String envelope = buildEnvelopeJson(orgId, groupId, subscriptionId, deliveryId, eventId, topic, rawPayload);
        JsonNode eventPayload = MAPPER.readTree(envelope);
        String payloadHash = HmacSigner.sign(sharedSecret, envelope);
        EventPayloadSigningContext context = new EventPayloadSigningContext(
                orgId, groupId, audience, deliveryId, eventId,
                System.currentTimeMillis() / 1000L, payloadHash, eventPayload);
        return payloadSigner.sign(context);
    }

    static String buildEnvelopeJson(String orgId, String groupId, String subscriptionId, String deliveryId,
            String eventId, String topic, String rawPayload) throws Exception {
        if (rawPayload == null) {
            throw new IllegalArgumentException("Event payload is null.");
        }
        JsonNode eventPayload = MAPPER.readTree(rawPayload);
        if (eventPayload == null || eventPayload.isNull()) {
            throw new IllegalArgumentException("Event payload is null.");
        }
        Map<String, Object> envelope = new LinkedHashMap<>();
        envelope.put("deliveryId", deliveryId);
        envelope.put("eventId", eventId);
        envelope.put("subscriptionId", subscriptionId);
        envelope.put("orgId", orgId);
        envelope.put("groupId", groupId);
        envelope.put("topic", topic);
        envelope.put("eventPayload", eventPayload);
        return MAPPER.writeValueAsString(envelope);
    }
}
