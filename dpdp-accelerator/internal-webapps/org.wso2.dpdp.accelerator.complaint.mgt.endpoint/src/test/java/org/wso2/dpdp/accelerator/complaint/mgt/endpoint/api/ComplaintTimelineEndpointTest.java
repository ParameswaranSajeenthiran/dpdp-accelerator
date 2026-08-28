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

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.wso2.carbon.context.PrivilegedCarbonContext;
import org.wso2.dpdp.accelerator.complaint.mgt.dao.constants.DAOConstants;
import org.wso2.dpdp.accelerator.complaint.mgt.service.dto.TimelineListResponseDTO;
import org.wso2.dpdp.accelerator.complaint.mgt.endpoint.handler.ComplaintTimelineHandler;

import java.io.IOException;
import javax.ws.rs.core.Response;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ComplaintTimelineEndpointTest {

    private static final String ORG_ID = DAOConstants.DEFAULT_ORG_ID;

    @Mock
    private ComplaintTimelineHandler timelineHandler;

    private ComplaintTimelineEndpoint endpoint;

    @BeforeAll
    static void configureCarbonEnvironment() throws IOException {
        CarbonContextTestSupport.configureMinimalCarbonEnvironment();
    }

    @BeforeEach
    void setUp() {
        endpoint = new ComplaintTimelineEndpoint(timelineHandler);
        PrivilegedCarbonContext.startTenantFlow();
        PrivilegedCarbonContext.getThreadLocalCarbonContext().setUsername("officer1");
        PrivilegedCarbonContext.getThreadLocalCarbonContext().setTenantDomain(ORG_ID);
    }

    @AfterEach
    void tearDown() {
        PrivilegedCarbonContext.endTenantFlow();
    }

    @Test
    void getTimelineReturns200WithHandlerResponse() {
        TimelineListResponseDTO handlerResponse = new TimelineListResponseDTO();
        when(timelineHandler.getTimeline(ORG_ID, "c1", 1000L, null, "asc", 10, 0)).thenReturn(handlerResponse);

        Response response = endpoint.getTimeline("c1", 1000L, null, "asc", 10, 0);

        assertEquals(200, response.getStatus());
        assertSame(handlerResponse, response.getEntity());
    }

}
