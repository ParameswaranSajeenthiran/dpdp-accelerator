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

import org.apache.cxf.jaxrs.ext.multipart.Attachment;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.wso2.carbon.context.PrivilegedCarbonContext;
import org.wso2.dpdp.accelerator.complaint.mgt.dao.constants.DAOConstants;
import org.wso2.dpdp.accelerator.complaint.mgt.service.dto.ComplaintAttachmentDownloadResponseDTO;
import org.wso2.dpdp.accelerator.complaint.mgt.service.dto.ComplaintAttachmentResponseDTO;
import org.wso2.dpdp.accelerator.complaint.mgt.endpoint.handler.ComplaintAttachmentHandler;

import java.io.IOException;
import javax.ws.rs.core.Response;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ComplaintAttachmentEndpointTest {

    private static final String ORG_ID = DAOConstants.DEFAULT_ORG_ID;

    @Mock
    private ComplaintAttachmentHandler attachmentHandler;
    @Mock
    private Attachment filePart;

    private ComplaintAttachmentEndpoint endpoint;

    @BeforeAll
    static void configureCarbonEnvironment() throws IOException {
        CarbonContextTestSupport.configureMinimalCarbonEnvironment();
    }

    @BeforeEach
    void setUp() {
        endpoint = new ComplaintAttachmentEndpoint(attachmentHandler);
        PrivilegedCarbonContext.startTenantFlow();
        PrivilegedCarbonContext.getThreadLocalCarbonContext().setUsername("officer1");
        PrivilegedCarbonContext.getThreadLocalCarbonContext().setTenantDomain(ORG_ID);
    }

    @AfterEach
    void tearDown() {
        PrivilegedCarbonContext.endTenantFlow();
    }

    @Test
    void uploadComplaintAttachmentReturns201WithHandlerResponse() {
        List<ComplaintAttachmentResponseDTO> handlerResponse = List.of();
        when(attachmentHandler.uploadComplaintAttachments(ORG_ID, "c1", List.of(filePart), true, "officer1",
                "officer1")).thenReturn(handlerResponse);

        Response response = endpoint.uploadComplaintAttachment("c1", List.of(filePart), true);

        assertEquals(201, response.getStatus());
        assertSame(handlerResponse, response.getEntity());
    }

    @Test
    void downloadComplaintAttachmentReturns200WithHandlerResponse() {
        ComplaintAttachmentDownloadResponseDTO handlerResponse =
                new ComplaintAttachmentDownloadResponseDTO("att1", "a.pdf", "application/pdf", new byte[]{1});
        when(attachmentHandler.downloadAttachment(ORG_ID, "c1", "att1")).thenReturn(handlerResponse);

        Response response = endpoint.downloadComplaintAttachment("c1", "att1");

        assertEquals(200, response.getStatus());
        assertSame(handlerResponse, response.getEntity());
    }

}
