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

import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.wso2.carbon.context.PrivilegedCarbonContext;
import org.wso2.dpdp.accelerator.complaint.mgt.dao.constants.DAOConstants;
import org.wso2.dpdp.accelerator.complaint.mgt.service.dto.ComplaintCommentCreateResponseDTO;
import org.wso2.dpdp.accelerator.complaint.mgt.service.dto.ComplaintMessageRequestDTO;
import org.wso2.dpdp.accelerator.complaint.mgt.endpoint.handler.ComplaintCommentHandler;

import java.io.IOException;
import javax.ws.rs.core.Response;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertSame;
import static org.mockito.Mockito.when;

class ComplaintCommentEndpointTest {

    private static final String ORG_ID = DAOConstants.DEFAULT_ORG_ID;

    @Mock
    private ComplaintCommentHandler commentHandler;

    private ComplaintCommentEndpoint endpoint;

    @BeforeClass
    static void configureCarbonEnvironment() throws IOException {
        CarbonContextTestSupport.configureMinimalCarbonEnvironment();
    }

    @BeforeMethod
    void setUp() {
        MockitoAnnotations.openMocks(this);
        endpoint = new ComplaintCommentEndpoint(commentHandler);
        PrivilegedCarbonContext.startTenantFlow();
        PrivilegedCarbonContext.getThreadLocalCarbonContext().setUsername("officer1");
        PrivilegedCarbonContext.getThreadLocalCarbonContext().setTenantDomain(ORG_ID);
    }

    @AfterMethod
    void tearDown() {
        PrivilegedCarbonContext.endTenantFlow();
    }

    @Test
    void addComplaintMessageResolvesActorFromTokenAndReturns200WithHandlerResponse() {
        ComplaintMessageRequestDTO request = new ComplaintMessageRequestDTO();
        ComplaintCommentCreateResponseDTO handlerResponse = new ComplaintCommentCreateResponseDTO();
        when(commentHandler.addComment(ORG_ID, "c1", "officer1", "officer1", "COMPLAINT_OFFICER", request))
                .thenReturn(handlerResponse);

        Response response = endpoint.addComplaintMessage("c1", request);

        assertEquals(200, response.getStatus());
        assertSame(handlerResponse, response.getEntity());
    }

}
