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

import org.wso2.carbon.context.PrivilegedCarbonContext;
import org.wso2.dpdp.accelerator.complaint.mgt.service.dto.ComplaintCommentCreateResponseDTO;
import org.wso2.dpdp.accelerator.complaint.mgt.service.dto.ComplaintMessageRequestDTO;
import org.wso2.dpdp.accelerator.complaint.mgt.endpoint.handler.ComplaintCommentHandler;

import javax.ws.rs.Consumes;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

/**
 * Officer/admin comment endpoint - see complaint-server-API.yaml "Complaint Management -
 * Comments". The acting officer/system's identity is resolved from {@link PrivilegedCarbonContext}
 * - every real HTTP caller here holds an any-scope token, i.e. is a COMPLAINT_OFFICER; SYSTEM
 * actors (e.g. an automated SLA monitor) call the service layer directly, not through this HTTP
 * endpoint.
 */
@Path("/complaints/{complaintId}/comments")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class ComplaintCommentEndpoint {

    private static final String ACTOR_ROLE_COMPLAINT_OFFICER = "COMPLAINT_OFFICER";

    private final ComplaintCommentHandler commentHandler;

    public ComplaintCommentEndpoint() {
        this.commentHandler = new ComplaintCommentHandler();
    }

    public ComplaintCommentEndpoint(ComplaintCommentHandler commentHandler) {
        this.commentHandler = commentHandler;
    }

    @POST
    public Response addComplaintMessage(
            @PathParam("complaintId") String complaintId,
            ComplaintMessageRequestDTO request) {
        String callerUsername = PrivilegedCarbonContext.getThreadLocalCarbonContext().getUsername();
        String callerOrgId = PrivilegedCarbonContext.getThreadLocalCarbonContext().getTenantDomain();
        ComplaintCommentCreateResponseDTO response = commentHandler.addComment(callerOrgId, complaintId,
                callerUsername, callerUsername, ACTOR_ROLE_COMPLAINT_OFFICER, request);
        return Response.ok(response).build();
    }
}
