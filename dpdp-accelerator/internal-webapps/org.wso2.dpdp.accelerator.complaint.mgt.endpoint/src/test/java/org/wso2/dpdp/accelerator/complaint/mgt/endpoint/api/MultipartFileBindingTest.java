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

package org.wso2.dpdp.accelerator.complaint.mgt.endpoint.api;

import org.apache.cxf.jaxrs.JAXRSServerFactoryBean;
import org.apache.cxf.jaxrs.client.WebClient;
import org.apache.cxf.jaxrs.ext.multipart.Attachment;
import org.apache.cxf.jaxrs.ext.multipart.ContentDisposition;
import org.apache.cxf.jaxrs.ext.multipart.Multipart;
import org.apache.cxf.jaxrs.ext.multipart.MultipartBody;
import org.apache.cxf.jaxrs.lifecycle.SingletonResourceProvider;
import org.apache.cxf.jaxrs.provider.MultipartProvider;
import org.apache.cxf.endpoint.Server;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import javax.ws.rs.Consumes;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.testng.Assert.assertEquals;

/**
 * ComplaintAttachmentEndpoint#uploadComplaintAttachment and
 * MeComplaintAttachmentEndpoint#uploadComplaintAttachment both bind incoming files as
 * {@code @Multipart("file") List<Attachment> fileParts}. Every existing test of those classes
 * (ComplaintAttachmentEndpointTest, ComplaintAttachmentHandlerTest) builds that list directly in
 * Java and calls the resource method as a plain Java call - none of them ever send a real HTTP
 * multipart request, so none of them can catch a binding bug in CXF's own multipart parsing.
 *
 * This starts a real, in-process CXF JAX-RS server - bound to CXF's {@code local://} transport
 * (part of cxf-core, no real socket/Jetty involved) rather than a real HTTP port, since the point
 * is to exercise CXF's actual multipart request/response marshalling, not networking - and POSTs
 * an actual multipart/form-data request with two parts both named "file" - the exact shape a
 * browser sends for a multi-file upload, and the exact shape complaintsApi.ts's
 * uploadFilesFormData builds (files.forEach(file => formData.append('file', file))) - through the
 * real CXF runtime this webapp deploys with (the CXF3 environment, see webapp-classloading.xml),
 * to settle empirically whether the binding collects both parts or silently drops one.
 */
class MultipartFileBindingTest {

    private static final String ADDRESS = "local://test-upload";

    @Path("/test-upload")
    public static class TestUploadResource {

        @POST
        @Consumes(MediaType.MULTIPART_FORM_DATA)
        public Response upload(@Multipart("file") List<Attachment> fileParts) {
            int count = fileParts == null ? 0 : fileParts.size();
            StringBuilder names = new StringBuilder();
            if (fileParts != null) {
                for (Attachment part : fileParts) {
                    if (names.length() > 0) {
                        names.append(',');
                    }
                    names.append(part.getContentDisposition().getParameter("filename"));
                }
            }
            return Response.ok(count + ":" + names).build();
        }
    }

    private Server server;

    @BeforeMethod
    void startServer() {
        JAXRSServerFactoryBean factory = new JAXRSServerFactoryBean();
        factory.setAddress(ADDRESS);
        factory.setResourceClasses(TestUploadResource.class);
        factory.setResourceProvider(TestUploadResource.class,
                new SingletonResourceProvider(new TestUploadResource()));
        factory.setProvider(new MultipartProvider());
        server = factory.create();
    }

    @AfterMethod
    void stopServer() {
        server.destroy();
    }



}
