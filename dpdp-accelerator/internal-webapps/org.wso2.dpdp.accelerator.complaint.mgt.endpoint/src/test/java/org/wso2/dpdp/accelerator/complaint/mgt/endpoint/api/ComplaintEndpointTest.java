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
import org.wso2.dpdp.accelerator.complaint.mgt.endpoint.dto.CategoryListResponse;
import org.wso2.dpdp.accelerator.complaint.mgt.endpoint.dto.CmComplaintCreateRequest;
import org.wso2.dpdp.accelerator.complaint.mgt.endpoint.dto.ComplaintCreateResponse;
import org.wso2.dpdp.accelerator.complaint.mgt.endpoint.dto.ComplaintListResponse;
import org.wso2.dpdp.accelerator.complaint.mgt.endpoint.dto.ComplaintQueueStatsResponse;
import org.wso2.dpdp.accelerator.complaint.mgt.endpoint.dto.ComplaintRecord;
import org.wso2.dpdp.accelerator.complaint.mgt.endpoint.dto.ComplaintStatusUpdateRequest;
import org.wso2.dpdp.accelerator.complaint.mgt.endpoint.dto.ComplaintStatusUpdateResponse;
import org.wso2.dpdp.accelerator.complaint.mgt.endpoint.handler.ComplaintHandler;

import java.io.IOException;
import javax.ws.rs.core.Response;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertSame;
import static org.mockito.Mockito.when;

class ComplaintEndpointTest {

    private static final String ORG_ID = DAOConstants.DEFAULT_ORG_ID;

    @Mock
    private ComplaintHandler complaintHandler;

    private ComplaintEndpoint endpoint;

    @BeforeClass
    static void configureCarbonEnvironment() throws IOException {
        CarbonContextTestSupport.configureMinimalCarbonEnvironment();
    }

    @BeforeMethod
    void setUp() {
        MockitoAnnotations.openMocks(this);
        endpoint = new ComplaintEndpoint(complaintHandler);
        PrivilegedCarbonContext.startTenantFlow();
        PrivilegedCarbonContext.getThreadLocalCarbonContext().setUsername("officer1");
        PrivilegedCarbonContext.getThreadLocalCarbonContext().setTenantDomain(ORG_ID);
    }

    @AfterMethod
    void tearDown() {
        PrivilegedCarbonContext.endTenantFlow();
    }

    @Test
    void createComplaintReturns201WithHandlerResponse() {
        CmComplaintCreateRequest request = new CmComplaintCreateRequest();
        ComplaintCreateResponse handlerResponse = new ComplaintCreateResponse();
        when(complaintHandler.createComplaint(ORG_ID, "officer1", "COMPLAINT_OFFICER", request))
                .thenReturn(handlerResponse);

        Response response = endpoint.createComplaint(request);

        assertEquals(201, response.getStatus());
        assertSame(handlerResponse, response.getEntity());
    }

    @Test
    void listComplaintsReturns200WithHandlerResponse() {
        ComplaintListResponse handlerResponse = new ComplaintListResponse();
        when(complaintHandler.listComplaints(ORG_ID, "OPEN", "HIGH", "user1", "acme", 10, 0, "updatedTime"))
                .thenReturn(handlerResponse);

        Response response = endpoint.listComplaints("OPEN", "HIGH", "user1", "acme", 10, 0, "updatedTime");

        assertEquals(200, response.getStatus());
        assertSame(handlerResponse, response.getEntity());
    }

    @Test
    void getQueueStatsReturns200WithHandlerResponse() {
        ComplaintQueueStatsResponse handlerResponse = new ComplaintQueueStatsResponse();
        when(complaintHandler.getQueueStats(ORG_ID)).thenReturn(handlerResponse);

        Response response = endpoint.getQueueStats();

        assertEquals(200, response.getStatus());
        assertSame(handlerResponse, response.getEntity());
    }

    @Test
    void getCategoriesReturns200WithHandlerResponse() {
        CategoryListResponse handlerResponse = new CategoryListResponse();
        when(complaintHandler.getCategories()).thenReturn(handlerResponse);

        Response response = endpoint.getCategories();

        assertEquals(200, response.getStatus());
        assertSame(handlerResponse, response.getEntity());
    }

    @Test
    void getComplaintReturns200WithHandlerResponse() {
        ComplaintRecord handlerResponse = new ComplaintRecord();
        when(complaintHandler.getComplaint(ORG_ID, "c1")).thenReturn(handlerResponse);

        Response response = endpoint.getComplaint("c1");

        assertEquals(200, response.getStatus());
        assertSame(handlerResponse, response.getEntity());
    }

    @Test
    void updateComplaintStatusReturns200WithHandlerResponse() {
        ComplaintStatusUpdateRequest request = new ComplaintStatusUpdateRequest();
        ComplaintStatusUpdateResponse handlerResponse = new ComplaintStatusUpdateResponse();
        when(complaintHandler.updateStatus(ORG_ID, "c1", "officer1", "officer1", "COMPLAINT_OFFICER", request))
                .thenReturn(handlerResponse);

        Response response = endpoint.updateComplaintStatus("c1", request);

        assertEquals(200, response.getStatus());
        assertSame(handlerResponse, response.getEntity());
    }

}
