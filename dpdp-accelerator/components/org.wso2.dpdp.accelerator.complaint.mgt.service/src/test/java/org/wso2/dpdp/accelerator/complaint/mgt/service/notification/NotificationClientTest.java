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

package org.wso2.dpdp.accelerator.complaint.mgt.service.notification;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.wso2.dpdp.accelerator.complaint.mgt.dao.model.Complaint;
import org.wso2.dpdp.accelerator.complaint.mgt.dao.model.ComplaintEvent;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Exercises {@link NotificationClient} against a real embedded HTTP server (no mocking of the
 * HTTP layer itself), since its whole job is the wire contract with {@code DPDPNotificationServlet}
 * on the other end.
 */
class NotificationClientTest {

    private HttpServer server;

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop(0);
        }
        System.clearProperty("CO_NOTIFY_INTERNAL_URL");
    }

    private CompletableFuture<Map<String, String>> startCapturingServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        CompletableFuture<Map<String, String>> received = new CompletableFuture<>();
        server.createContext("/notify", exchange -> {
            byte[] body = exchange.getRequestBody().readAllBytes();
            received.complete(decodeForm(new String(body, StandardCharsets.UTF_8)));
            exchange.sendResponseHeaders(202, -1);
            exchange.close();
        });
        server.start();
        System.setProperty("CO_NOTIFY_INTERNAL_URL", "http://localhost:" + server.getAddress().getPort() + "/notify");
        return received;
    }

    private static Map<String, String> decodeForm(String body) {
        Map<String, String> fields = new LinkedHashMap<>();
        if (body.isEmpty()) {
            return fields;
        }
        for (String pair : body.split("&")) {
            String[] parts = pair.split("=", 2);
            fields.put(URLDecoder.decode(parts[0], StandardCharsets.UTF_8),
                    parts.length > 1 ? URLDecoder.decode(parts[1], StandardCharsets.UTF_8) : "");
        }
        return fields;
    }

    private static Map<String, String> await(CompletableFuture<Map<String, String>> future) throws Exception {
        try {
            return future.get(5, TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            throw new AssertionError("Notification client never called the bridge server.", e);
        }
    }

    @Test
    void notifyComplaintCreatedPostsExpectedFields() throws Exception {
        CompletableFuture<Map<String, String>> received = startCapturingServer();
        Complaint complaint = new Complaint("c1", "org1", "user1", "User One", "CMP-2026-00001", "DATA_BREACH",
                "CRITICAL", "OPEN", "desc", 1L, 2L, 3L);

        new NotificationClient().notifyComplaintCreated(complaint);

        Map<String, String> fields = await(received);
        assertEquals("ComplaintCreated", fields.get("notification-type"));
        assertEquals("org1", fields.get("tenant-domain"));
        assertEquals("c1", fields.get("complaint-id"));
        assertEquals("CMP-2026-00001", fields.get("reference-id"));
        assertEquals("DATA_BREACH", fields.get("category"));
    }

    @Test
    void notifyCommentAddedPostsActorRoleAndExcerpt() throws Exception {
        CompletableFuture<Map<String, String>> received = startCapturingServer();
        Complaint complaint = new Complaint("c1", "org1", "user1", "User One", "CMP-2026-00001", "DATA_BREACH",
                "CRITICAL", "OPEN", "desc", 1L, 2L, 3L);
        ComplaintEvent event = new ComplaintEvent("e1", "org1", "c1", "officer1", "Officer One", "COMPLAINT_OFFICER",
                true, "hello there", null, null, 100L);

        new NotificationClient().notifyCommentAdded(complaint, event);

        Map<String, String> fields = await(received);
        assertEquals("ComplaintCommentAdded", fields.get("notification-type"));
        assertEquals("COMPLAINT_OFFICER", fields.get("actor-role"));
        assertEquals("hello there", fields.get("message-excerpt"));
        assertEquals("user1", fields.get("creator-user-id"));
        assertEquals("User One", fields.get("creator-user-name"));
    }

    @Test
    void notifyCommentAddedTruncatesLongMessages() throws Exception {
        CompletableFuture<Map<String, String>> received = startCapturingServer();
        Complaint complaint = new Complaint("c1", "org1", "user1", "User One", "CMP-2026-00001", "DATA_BREACH",
                "CRITICAL", "OPEN", "desc", 1L, 2L, 3L);
        String longMessage = "a".repeat(500);
        ComplaintEvent event = new ComplaintEvent("e1", "org1", "c1", "user1", "User One", "USER", true, longMessage,
                null, null, 100L);

        new NotificationClient().notifyCommentAdded(complaint, event);

        Map<String, String> fields = await(received);
        String excerpt = fields.get("message-excerpt");
        assertTrue(excerpt.length() < longMessage.length());
        assertTrue(excerpt.endsWith("..."));
    }

    @Test
    void omitsCreatorUserNameFieldWhenComplaintHasNone() throws Exception {
        CompletableFuture<Map<String, String>> received = startCapturingServer();
        Complaint complaint = new Complaint("c1", "org1", "user1", null, "CMP-2026-00001", "DATA_BREACH", "CRITICAL",
                "OPEN", "desc", 1L, 2L, 3L);
        ComplaintEvent event = new ComplaintEvent("e1", "org1", "c1", "officer1", "Officer One", "COMPLAINT_OFFICER",
                true, "hi", null, null, 100L);

        new NotificationClient().notifyCommentAdded(complaint, event);

        Map<String, String> fields = await(received);
        assertFalse(fields.containsKey("creator-user-name"));
        assertEquals("user1", fields.get("creator-user-id"));
    }

    @Test
    void neverThrowsWhenTheBridgeIsUnreachable() {
        // No server started - CO_NOTIFY_INTERNAL_URL keeps its unreachable default. A notification
        // failure must never propagate to the caller (see NotificationClient's class javadoc).
        System.setProperty("CO_NOTIFY_INTERNAL_URL", "https://localhost:1/dpdp-internal/notify");
        Complaint complaint = new Complaint("c1", "org1", "user1", "User One", "CMP-2026-00001", "DATA_BREACH",
                "CRITICAL", "OPEN", "desc", 1L, 2L, 3L);

        new NotificationClient().notifyComplaintCreated(complaint);
    }
}
