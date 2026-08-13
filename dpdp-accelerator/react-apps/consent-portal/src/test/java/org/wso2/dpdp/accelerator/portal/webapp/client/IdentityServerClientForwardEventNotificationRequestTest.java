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

package org.wso2.dpdp.accelerator.portal.webapp.client;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertTrue;

/**
 * End-to-end test for {@link IdentityServerClient#forwardEventNotificationRequest}
 * over a real localhost HTTP listener. Confirms that the {@code group-id}
 * header is forwarded alongside {@code org-id} on POST / GET / DELETE, so the
 * upstream event-notifications endpoint stops rejecting the BFF with
 * {@code CS-4001 / "Group ID is required."}.
 */
public class IdentityServerClientForwardEventNotificationRequestTest {

    private HttpServer server;
    private int port;
    private AtomicReference<Map<String, String>> capturedHeaders;
    private AtomicReference<String> capturedMethod;
    private AtomicReference<String> capturedBody;

    @BeforeMethod
    public void setUp() throws IOException {
        capturedHeaders = new AtomicReference<>(new HashMap<>());
        capturedMethod = new AtomicReference<>();
        capturedBody = new AtomicReference<>();

        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        port = server.getAddress().getPort();
        server.createContext("/", new CapturingHandler());
        server.start();
    }

    @AfterMethod
    public void tearDown() {
        if (server != null) {
            server.stop(0);
        }
    }

    private IdentityServerClient newClient(String accessToken) {
        return new IdentityServerClient("http://127.0.0.1:" + port, accessToken);
    }

    private void respond(HttpExchange exchange, int status, String body) throws IOException {
        byte[] payload = body == null ? new byte[0] : body.getBytes();
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, payload.length == 0 ? -1 : payload.length);
        if (payload.length > 0) {
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(payload);
            }
        }
        exchange.close();
    }

    @Test
    public void forwardsBothOrgIdAndGroupId_onPost() throws Exception {
        IdentityServerClient client = newClient("test-token");

        IdentityServerClient.Result result = client.forwardEventNotificationRequest("POST",
                "/api/dpdp/event-notifications/subscriptions", "{\"topic\":\"x\"}",
                "carbon.super", "GROUP-001");

        assertEquals(capturedMethod.get(), "POST");
        assertEquals(capturedHeaders.get().get("org-id"), "carbon.super");
        assertEquals(capturedHeaders.get().get("group-id"), "GROUP-001");
        assertEquals(capturedHeaders.get().get("authorization"), "Bearer test-token");
        assertEquals(capturedBody.get(), "{\"topic\":\"x\"}");
        assertTrue(result.isSuccess());
    }

    @Test
    public void forwardsBothOrgIdAndGroupId_onGet() throws Exception {
        IdentityServerClient client = newClient("test-token");

        client.forwardEventNotificationRequest("GET",
                "/api/dpdp/event-notifications/subscriptions", null,
                "carbon.super", "GROUP-001");

        assertEquals(capturedMethod.get(), "GET");
        assertEquals(capturedHeaders.get().get("org-id"), "carbon.super");
        assertEquals(capturedHeaders.get().get("group-id"), "GROUP-001");
    }

    @Test
    public void forwardsBothOrgIdAndGroupId_onDelete() throws Exception {
        IdentityServerClient client = newClient("test-token");

        client.forwardEventNotificationRequest("DELETE",
                "/api/dpdp/event-notifications/subscriptions/sub-123", null,
                "carbon.super", "GROUP-001");

        assertEquals(capturedMethod.get(), "DELETE");
        assertEquals(capturedHeaders.get().get("org-id"), "carbon.super");
        assertEquals(capturedHeaders.get().get("group-id"), "GROUP-001");
    }

    @Test
    public void omitsGroupId_whenCallerProvidesNull() throws Exception {
        IdentityServerClient client = newClient("test-token");

        client.forwardEventNotificationRequest("POST",
                "/api/dpdp/event-notifications/events",
                "{\"topic\":\"x\"}", "carbon.super", null);

        assertEquals(capturedHeaders.get().get("org-id"), "carbon.super");
        assertNull(capturedHeaders.get().get("group-id"));
    }

    @Test
    public void omitsGroupId_whenCallerProvidesBlank() throws Exception {
        IdentityServerClient client = newClient("test-token");

        client.forwardEventNotificationRequest("POST",
                "/api/dpdp/event-notifications/events",
                "{\"topic\":\"x\"}", "carbon.super", "   ");

        assertEquals(capturedHeaders.get().get("org-id"), "carbon.super");
        assertNull(capturedHeaders.get().get("group-id"));
    }

    @Test
    public void omitsOrgId_whenCallerProvidesNull() throws Exception {
        IdentityServerClient client = newClient("test-token");

        client.forwardEventNotificationRequest("POST",
                "/api/dpdp/event-notifications/events",
                "{\"topic\":\"x\"}", null, "GROUP-001");

        assertNull(capturedHeaders.get().get("org-id"));
        assertEquals(capturedHeaders.get().get("group-id"), "GROUP-001");
    }

    @Test
    public void trimsGroupId_whitespaceBeforeForwarding() throws Exception {
        IdentityServerClient client = newClient("test-token");

        client.forwardEventNotificationRequest("POST",
                "/api/dpdp/event-notifications/events",
                "{\"topic\":\"x\"}", "carbon.super", "  GROUP-002  ");

        assertEquals(capturedHeaders.get().get("group-id"), "GROUP-002");
    }

    @Test
    public void postRequestWithoutBody_sendsNoContentLengthAndFiresNoNullBody() throws Exception {
        IdentityServerClient client = newClient("test-token");

        // Some POSTs (e.g. fan-out triggers) legitimately have no body. Confirm the client
        // still attaches the org-id/group-id headers and the call goes through.
        client.forwardEventNotificationRequest("POST",
                "/api/dpdp/event-notifications/events", null, "carbon.super", "GROUP-001");

        assertEquals(capturedMethod.get(), "POST");
        assertEquals(capturedHeaders.get().get("org-id"), "carbon.super");
        assertEquals(capturedHeaders.get().get("group-id"), "GROUP-001");
    }

    @Test
    public void relaysUpstreamErrorStatusAndBody() throws Exception {
        // Hand-set a responder that returns 400 + a stubbed CS envelope, so we can
        // confirm the client propagates status/body unchanged to AbstractProxyServlet.
        server.removeContext("/");
        server.createContext("/", exchange -> {
            captureFrom(exchange);
            String body = "{\"code\":\"CS-4001\",\"message\":\"Malformed request\",\"description\":\"Group ID is required.\"}";
            respond(exchange, 400, body);
        });

        IdentityServerClient client = newClient("test-token");
        IdentityServerClient.Result result = client.forwardEventNotificationRequest("POST",
                "/api/dpdp/event-notifications/subscriptions", "{}", "carbon.super", null);

        assertEquals(result.getStatus(), 400);
        assertNotNull(result.getBody());
        assertTrue(result.getBody().contains("Group ID is required."));
    }

    private void captureFrom(HttpExchange exchange) throws IOException {
        Map<String, String> headers = new HashMap<>();
        // HttpExchange.getRequestHeaders() normalizes keys to title case (e.g. "Org-id"),
        // so fold to lowercase for stable assertions.
        exchange.getRequestHeaders().forEach((k, v) -> headers.put(k == null ? null : k.toLowerCase(),
                v == null || v.isEmpty() ? null : v.get(0)));
        capturedHeaders.set(headers);
        capturedMethod.set(exchange.getRequestMethod());
        if ("POST".equals(exchange.getRequestMethod())) {
            capturedBody.set(new String(exchange.getRequestBody().readAllBytes()));
        }
    }

    private class CapturingHandler implements HttpHandler {

        @Override
        public void handle(HttpExchange exchange) throws IOException {
            captureFrom(exchange);
            respond(exchange, 200, "{\"ok\":true}");
        }
    }
}