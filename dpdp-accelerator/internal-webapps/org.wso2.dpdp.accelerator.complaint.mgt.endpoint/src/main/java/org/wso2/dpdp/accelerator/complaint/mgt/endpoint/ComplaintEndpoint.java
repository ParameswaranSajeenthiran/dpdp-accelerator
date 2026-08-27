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

import org.wso2.dpdp.accelerator.complaint.mgt.endpoint.auth.AuthenticatedPrincipal;
import org.wso2.dpdp.accelerator.complaint.mgt.endpoint.auth.RequireScope;
import org.wso2.dpdp.accelerator.complaint.mgt.endpoint.auth.TokenIntrospectionFilter;
import org.wso2.dpdp.accelerator.complaint.mgt.service.dto.CategoryListResponseDTO;
import org.wso2.dpdp.accelerator.complaint.mgt.service.dto.ComplaintCreateRequestDTO;
import org.wso2.dpdp.accelerator.complaint.mgt.service.dto.ComplaintCreateResponseDTO;
import org.wso2.dpdp.accelerator.complaint.mgt.service.dto.ComplaintListResponseDTO;
import org.wso2.dpdp.accelerator.complaint.mgt.service.dto.ComplaintQueueStatsResponseDTO;
import org.wso2.dpdp.accelerator.complaint.mgt.service.dto.ComplaintRecordDTO;
import org.wso2.dpdp.accelerator.complaint.mgt.service.dto.ComplaintStatusUpdateRequestDTO;
import org.wso2.dpdp.accelerator.complaint.mgt.service.dto.ComplaintStatusUpdateResponseDTO;
import org.wso2.dpdp.accelerator.complaint.mgt.endpoint.handler.ComplaintHandler;

import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

/**
 * Complaint Management (officer/admin) namespace - see complaint-server-API.yaml. Every operation
 * requires complaints:{read,write}:any; the acting officer/system's identity comes from the
 * validated bearer token (TokenIntrospectionFilter), never a client-supplied header.
 */
@Path("/complaints")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class ComplaintEndpoint {

    private final ComplaintHandler complaintHandler;

    @Context
    private ContainerRequestContext requestContext;

    /** Test seam - Jersey normally field-injects this via @Context. */
    void setRequestContext(ContainerRequestContext requestContext) {
        this.requestContext = requestContext;
    }

    public ComplaintEndpoint() {
        this.complaintHandler = new ComplaintHandler();
    }

    public ComplaintEndpoint(ComplaintHandler complaintHandler) {
        this.complaintHandler = complaintHandler;
    }

    private static final String ACTOR_ROLE_COMPLAINT_OFFICER = "COMPLAINT_OFFICER";

    private String currentOrgId() {
        return TokenIntrospectionFilter.currentPrincipal(requestContext).getOrgId();
    }

    @POST
    @RequireScope
    public Response createComplaint(ComplaintCreateRequestDTO request) {
        AuthenticatedPrincipal principal = TokenIntrospectionFilter.currentPrincipal(requestContext);
        ComplaintCreateResponseDTO response = complaintHandler.createComplaint(principal.getOrgId(),
                principal.getUserId(), ACTOR_ROLE_COMPLAINT_OFFICER, request);
        return Response.status(Response.Status.CREATED).entity(response).build();
    }

    @GET
    @RequireScope
    public Response listComplaints(
            @QueryParam("status") String status,
            @QueryParam("priority") String priority,
            @QueryParam("userId") String userId,
            @QueryParam("limit") Integer limit,
            @QueryParam("offset") Integer offset,
            @QueryParam("sort") String sort) {
        ComplaintListResponseDTO response =
                complaintHandler.listComplaints(currentOrgId(), status, priority, userId, limit, offset, sort);
        return Response.ok(response).build();
    }

    @GET
    @Path("/stats")
    @RequireScope
    public Response getQueueStats() {
        ComplaintQueueStatsResponseDTO response = complaintHandler.getQueueStats(currentOrgId());
        return Response.ok(response).build();
    }

    @GET
    @Path("/categories")
    @RequireScope
    public Response getCategories() {
        CategoryListResponseDTO response = complaintHandler.getCategories();
        return Response.ok(response).build();
    }

    @GET
    @Path("/{complaintId}")
    @RequireScope
    public Response getComplaint(@PathParam("complaintId") String complaintId) {
        ComplaintRecordDTO response = complaintHandler.getComplaint(currentOrgId(), complaintId);
        return Response.ok(response).build();
    }

    @POST
    @Path("/{complaintId}/status")
    @RequireScope
    public Response updateComplaintStatus(
            @PathParam("complaintId") String complaintId,
            ComplaintStatusUpdateRequestDTO request) {
        AuthenticatedPrincipal principal = TokenIntrospectionFilter.currentPrincipal(requestContext);
        ComplaintStatusUpdateResponseDTO response = complaintHandler.updateStatus(principal.getOrgId(), complaintId,
                principal.getUserId(), principal.getUserName(), ACTOR_ROLE_COMPLAINT_OFFICER, request);
        return Response.ok(response).build();
    }
}
