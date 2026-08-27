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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.wso2.dpdp.accelerator.complaint.mgt.dao.constants.DAOConstants;
import org.wso2.dpdp.accelerator.complaint.mgt.endpoint.auth.AuthenticatedPrincipal;
import org.wso2.dpdp.accelerator.complaint.mgt.endpoint.auth.TokenIntrospectionFilter;
import org.wso2.dpdp.accelerator.complaint.mgt.endpoint.bean.CategoryListResponseBean;
import org.wso2.dpdp.accelerator.complaint.mgt.endpoint.bean.ComplaintCreateRequestBean;
import org.wso2.dpdp.accelerator.complaint.mgt.endpoint.bean.ComplaintCreateResponseBean;
import org.wso2.dpdp.accelerator.complaint.mgt.endpoint.bean.ComplaintListResponseBean;
import org.wso2.dpdp.accelerator.complaint.mgt.endpoint.bean.ComplaintQueueStatsResponseBean;
import org.wso2.dpdp.accelerator.complaint.mgt.endpoint.bean.ComplaintRecordBean;
import org.wso2.dpdp.accelerator.complaint.mgt.endpoint.bean.ComplaintStatusUpdateRequestBean;
import org.wso2.dpdp.accelerator.complaint.mgt.endpoint.bean.ComplaintStatusUpdateResponseBean;
import org.wso2.dpdp.accelerator.complaint.mgt.endpoint.handler.ComplaintHandler;

import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.core.Response;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ComplaintEndpointTest {

    private static final String ORG_ID = DAOConstants.DEFAULT_ORG_ID;

    @Mock
    private ComplaintHandler complaintHandler;
    @Mock
    private ContainerRequestContext requestContext;

    private ComplaintEndpoint endpoint;

    @BeforeEach
    void setUp() {
        endpoint = new ComplaintEndpoint(complaintHandler);
        endpoint.setRequestContext(requestContext);
    }

    private void stubPrincipal() {
        when(requestContext.getProperty(TokenIntrospectionFilter.PRINCIPAL_PROPERTY))
                .thenReturn(new AuthenticatedPrincipal("officer1", "Officer One", ORG_ID,
                        Set.of("complaints:read:any")));
    }

    @Test
    void createComplaintReturns201WithHandlerResponse() {
        stubPrincipal();
        ComplaintCreateRequestBean request = new ComplaintCreateRequestBean();
        ComplaintCreateResponseBean handlerResponse = new ComplaintCreateResponseBean();
        when(complaintHandler.createComplaint(ORG_ID, "officer1", "COMPLAINT_OFFICER", request))
                .thenReturn(handlerResponse);

        Response response = endpoint.createComplaint(request);

        assertEquals(201, response.getStatus());
        assertSame(handlerResponse, response.getEntity());
    }

    @Test
    void listComplaintsReturns200WithHandlerResponse() {
        stubPrincipal();
        ComplaintListResponseBean handlerResponse = new ComplaintListResponseBean();
        when(complaintHandler.listComplaints(ORG_ID, "OPEN", "HIGH", "user1", 10, 0, "updatedTime"))
                .thenReturn(handlerResponse);

        Response response = endpoint.listComplaints("OPEN", "HIGH", "user1", 10, 0, "updatedTime");

        assertEquals(200, response.getStatus());
        assertSame(handlerResponse, response.getEntity());
    }

    @Test
    void getQueueStatsReturns200WithHandlerResponse() {
        stubPrincipal();
        ComplaintQueueStatsResponseBean handlerResponse = new ComplaintQueueStatsResponseBean();
        when(complaintHandler.getQueueStats(ORG_ID)).thenReturn(handlerResponse);

        Response response = endpoint.getQueueStats();

        assertEquals(200, response.getStatus());
        assertSame(handlerResponse, response.getEntity());
    }

    @Test
    void getCategoriesReturns200WithHandlerResponse() {
        CategoryListResponseBean handlerResponse = new CategoryListResponseBean();
        when(complaintHandler.getCategories()).thenReturn(handlerResponse);

        Response response = endpoint.getCategories();

        assertEquals(200, response.getStatus());
        assertSame(handlerResponse, response.getEntity());
    }

    @Test
    void getComplaintReturns200WithHandlerResponse() {
        stubPrincipal();
        ComplaintRecordBean handlerResponse = new ComplaintRecordBean();
        when(complaintHandler.getComplaint(ORG_ID, "c1")).thenReturn(handlerResponse);

        Response response = endpoint.getComplaint("c1");

        assertEquals(200, response.getStatus());
        assertSame(handlerResponse, response.getEntity());
    }

    @Test
    void updateComplaintStatusReturns200WithHandlerResponse() {
        stubPrincipal();
        ComplaintStatusUpdateRequestBean request = new ComplaintStatusUpdateRequestBean();
        ComplaintStatusUpdateResponseBean handlerResponse = new ComplaintStatusUpdateResponseBean();
        when(complaintHandler.updateStatus(ORG_ID, "c1", "officer1", "Officer One", "COMPLAINT_OFFICER", request))
                .thenReturn(handlerResponse);

        Response response = endpoint.updateComplaintStatus("c1", request);

        assertEquals(200, response.getStatus());
        assertSame(handlerResponse, response.getEntity());
    }

    @Test
    void noArgsConstructorWiresARealHandler() {
        assertNotNull(new ComplaintEndpoint());
    }
}
