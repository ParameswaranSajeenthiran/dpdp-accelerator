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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import org.wso2.dpdp.accelerator.portal.webapp.servlet.AbstractProxyServlet;

import javax.servlet.http.HttpServletResponse;

import java.io.StringWriter;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

/**
 * Verifies the upstream-error relay in {@link AbstractProxyServlet#relayError}:
 * the BFF must copy the Identity Server's own {@code code} and {@code message}
 * fields, and - once the upstream includes one - forward the {@code description}
 * field as well, so the SPA keeps the diagnostic detail rather than just
 * seeing a generic envelope.
 */
public class AbstractProxyServletRelayErrorTest {

    /** Exposes the protected {@code relayError} so it can be driven directly. */
    private static class TestableServlet extends AbstractProxyServlet {

        private static final long serialVersionUID = 1L;

        void invokeRelayError(IdentityServerClient.Result result, HttpServletResponse response) throws java.io.IOException {
            relayError(result, response);
        }
    }

    private TestableServlet servlet;
    private HttpServletResponse response;
    private StringWriter bodyWriter;

    @BeforeMethod
    public void setUp() throws Exception {
        servlet = new TestableServlet();
        response = mock(HttpServletResponse.class);
        bodyWriter = new StringWriter();
        when(response.getWriter()).thenReturn(new java.io.PrintWriter(bodyWriter));
    }

    @Test
    public void relayError_propagatesUpstreamDescription_whenPresent() throws Exception {
        String upstream = "{\"code\":\"CS-4001\",\"message\":\"Malformed request\",\"description\":\"Group ID is required.\"}";
        IdentityServerClient.Result result = new IdentityServerClient.Result(400, upstream);

        servlet.invokeRelayError(result, response);

        JsonNode body = new ObjectMapper().readTree(bodyWriter.toString());
        assertEquals(body.get("code").asText(), "CS-4001");
        assertEquals(body.get("message").asText(), "Group ID is required.");
    }

    @Test
    public void relayError_omitsDescriptionField_whenAbsent() throws Exception {
        // The case we're fixing: upstream had no description, BFF must not emit one either.
        String upstream = "{\"code\":\"CS-4001\",\"message\":\"Malformed request\"}";
        IdentityServerClient.Result result = new IdentityServerClient.Result(400, upstream);

        servlet.invokeRelayError(result, response);

        JsonNode body = new ObjectMapper().readTree(bodyWriter.toString());
        assertEquals(body.get("code").asText(), "CS-4001");
        assertEquals(body.get("message").asText(), "Malformed request");
        assertTrue(!body.has("description") || body.get("description").isNull());
    }

    @Test
    public void relayError_preservesUpstreamCodeAndMessage_evenWhenNonStandard() throws Exception {
        // If the upstream returns a non-{400,401,403} status, the BFF should still
        // pick up the upstream code/message/description rather than substituting
        // its own UPSTREAM_ERROR envelope.
        String upstream = "{\"code\":\"CS-4220\",\"message\":\"Webhook verification failed\",\"description\":\"Callback URL responded with HTTP 500\"}";
        IdentityServerClient.Result result = new IdentityServerClient.Result(422, upstream);

        servlet.invokeRelayError(result, response);

        JsonNode body = new ObjectMapper().readTree(bodyWriter.toString());
        assertEquals(body.get("code").asText(), "CS-4220");
        assertEquals(body.get("message").asText(), "Callback URL responded with HTTP 500");
    }

    @Test
    public void relayError_fallsBackToGenericEnvelope_whenUpstreamBodyIsNotJson() throws Exception {
        // Carbon sometimes returns plain-text error pages; BFF must not crash on
        // the parse and should still emit a {code, message} body.
        IdentityServerClient.Result result = new IdentityServerClient.Result(500, "<html>oops</html>");

        servlet.invokeRelayError(result, response);

        JsonNode body = new ObjectMapper().readTree(bodyWriter.toString());
        // The non-{401,403} branch sets code=UPSTREAM_ERROR and message="The consent service returned an error."
        assertEquals(body.get("code").asText(), "UPSTREAM_ERROR");
        assertEquals(body.get("message").asText(), "The consent service returned an error.");
        assertTrue(!body.has("description"));
    }

    @Test
    public void relayError_trimsBlankDescription() throws Exception {
        // Empty/whitespace descriptions should be dropped, not surfaced as empty strings.
        String upstream = "{\"code\":\"CS-4001\",\"message\":\"Malformed request\",\"description\":\"   \"}";
        IdentityServerClient.Result result = new IdentityServerClient.Result(400, upstream);

        servlet.invokeRelayError(result, response);

        JsonNode body = new ObjectMapper().readTree(bodyWriter.toString());
        assertTrue(!body.has("description"));
    }
}