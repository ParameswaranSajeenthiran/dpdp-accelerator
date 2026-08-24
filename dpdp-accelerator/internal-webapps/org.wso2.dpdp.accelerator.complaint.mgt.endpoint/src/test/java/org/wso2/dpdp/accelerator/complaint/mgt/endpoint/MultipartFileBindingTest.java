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

package org.wso2.dpdp.accelerator.complaint.mgt.endpoint;

import org.glassfish.grizzly.http.server.HttpServer;
import org.glassfish.jersey.client.ClientConfig;
import org.glassfish.jersey.grizzly2.httpserver.GrizzlyHttpServerFactory;
import org.glassfish.jersey.media.multipart.FormDataBodyPart;
import org.glassfish.jersey.media.multipart.FormDataContentDisposition;
import org.glassfish.jersey.media.multipart.FormDataMultiPart;
import org.glassfish.jersey.media.multipart.FormDataParam;
import org.glassfish.jersey.media.multipart.MultiPartFeature;
import org.glassfish.jersey.server.ResourceConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.ws.rs.Consumes;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.Entity;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.net.URI;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * ComplaintAttachmentEndpoint#uploadComplaintAttachment and
 * MeComplaintAttachmentEndpoint#uploadComplaintAttachment both bind incoming files as
 * {@code @FormDataParam("file") List<FormDataBodyPart> fileParts}. Every existing test of those
 * classes (ComplaintAttachmentEndpointTest, ComplaintAttachmentHandlerTest) builds that list
 * directly in Java and calls the resource method as a plain Java call - none of them ever send a
 * real HTTP multipart request, so none of them can catch a binding bug in Jersey's own multipart
 * parsing.
 *
 * This starts a real, in-memory HTTP server (Grizzly - the same HTTP engine
 * jersey-container-grizzly2-http wraps) and POSTs an actual multipart/form-data request with two
 * parts both named "file" - the exact shape a browser sends for a multi-file upload, and the
 * exact shape complaintsApi.ts's uploadFilesFormData builds
 * (files.forEach(file => formData.append('file', file))) - through the real Jersey runtime this
 * webapp deploys with, to settle empirically whether the binding collects both parts or silently
 * drops one. Driven directly via GrizzlyHttpServerFactory rather than JerseyTest - see this
 * module's pom.xml for why.
 */
class MultipartFileBindingTest {

    @Path("/test-upload")
    public static class TestUploadResource {

        @POST
        @Consumes(MediaType.MULTIPART_FORM_DATA)
        public Response upload(@FormDataParam("file") List<FormDataBodyPart> fileParts) {
            int count = fileParts == null ? 0 : fileParts.size();
            StringBuilder names = new StringBuilder();
            if (fileParts != null) {
                for (FormDataBodyPart part : fileParts) {
                    if (names.length() > 0) {
                        names.append(',');
                    }
                    names.append(part.getContentDisposition().getFileName());
                }
            }
            return Response.ok(count + ":" + names).build();
        }
    }

    private HttpServer server;
    private Client client;
    private WebTarget target;

    @BeforeEach
    void startServer() {
        ResourceConfig config = new ResourceConfig(TestUploadResource.class).register(MultiPartFeature.class);
        // Port 0 asks the OS for any free ephemeral port; getListener(...).getPort() then reads
        // back which one it actually picked - no fixed-port collisions with anything else running.
        server = GrizzlyHttpServerFactory.createHttpServer(URI.create("http://localhost:0/"), config);
        int port = server.getListeners().iterator().next().getPort();

        client = ClientBuilder.newClient(new ClientConfig().register(MultiPartFeature.class));
        target = client.target("http://localhost:" + port + "/test-upload");
    }

    @AfterEach
    void stopServer() {
        client.close();
        server.shutdownNow();
    }

    @Test
    void twoFilePartsSharingTheSameFieldNameBothBindIntoTheList() {
        FormDataMultiPart multiPart = new FormDataMultiPart();
        multiPart.bodyPart(new FormDataBodyPart(
                FormDataContentDisposition.name("file").fileName("first.jpg").build(),
                "first-file-bytes", MediaType.valueOf("image/jpeg")));
        multiPart.bodyPart(new FormDataBodyPart(
                FormDataContentDisposition.name("file").fileName("second.pdf").build(),
                "second-file-bytes", MediaType.valueOf("application/pdf")));

        Response response = target.request().post(Entity.entity(multiPart, multiPart.getMediaType()));

        assertEquals(200, response.getStatus());
        String body = response.readEntity(String.class);
        assertEquals("2:first.jpg,second.pdf", body,
                "Expected both multipart 'file' parts to bind into the List<FormDataBodyPart> - "
                        + "got: " + body);
    }

    @Test
    void aSingleFilePartBindsAsAOneElementList() {
        FormDataMultiPart multiPart = new FormDataMultiPart();
        multiPart.bodyPart(new FormDataBodyPart(
                FormDataContentDisposition.name("file").fileName("only.jpg").build(),
                "only-file-bytes", MediaType.valueOf("image/jpeg")));

        Response response = target.request().post(Entity.entity(multiPart, multiPart.getMediaType()));

        assertEquals(200, response.getStatus());
        assertEquals("1:only.jpg", response.readEntity(String.class));
    }
}
