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
import org.wso2.dpdp.accelerator.complaint.mgt.service.dto.ComplaintCreateResponseDTO;
import org.wso2.dpdp.accelerator.complaint.mgt.service.dto.ComplaintListResponseDTO;
import org.wso2.dpdp.accelerator.complaint.mgt.service.dto.ComplaintRecordDTO;
import org.wso2.dpdp.accelerator.complaint.mgt.service.dto.ComplaintStatusUpdateResponseDTO;
import org.wso2.dpdp.accelerator.complaint.mgt.service.dto.MeComplaintCreateRequestDTO;
import org.wso2.dpdp.accelerator.complaint.mgt.service.dto.MeComplaintStatusUpdateRequestDTO;
import org.wso2.dpdp.accelerator.complaint.mgt.endpoint.handler.ComplaintHandler;

import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Context;
import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

/**
 * "Me" (Data Principal) namespace - see complaint-server-API.yaml. Identity is always the caller's
 * own token subject (TokenIntrospectionFilter); there is no user-id header or body field anywhere
 * here, so a token belonging to one user can never be used to act on another user's complaint.
 */
@Path("/me/complaints")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class MeComplaintEndpoint {

    private final ComplaintHandler complaintHandler;

    @Context
    private ContainerRequestContext requestContext;

    /** Test seam - Jersey normally field-injects this via @Context. */
    void setRequestContext(ContainerRequestContext requestContext) {
        this.requestContext = requestContext;
    }

    public MeComplaintEndpoint() {
        this.complaintHandler = new ComplaintHandler();
    }

    public MeComplaintEndpoint(ComplaintHandler complaintHandler) {
        this.complaintHandler = complaintHandler;
    }

    @POST
    @RequireScope
    public Response createComplaint(MeComplaintCreateRequestDTO request) {
        AuthenticatedPrincipal principal = TokenIntrospectionFilter.currentPrincipal(requestContext);
        ComplaintCreateResponseDTO response = complaintHandler.createOwnComplaint(principal.getOrgId(),
                principal.getUserId(), principal.getUserName(), request);
        return Response.status(Response.Status.CREATED).entity(response).build();
    }

    @GET
    @RequireScope
    public Response listComplaints(
            @QueryParam("status") String status,
            @QueryParam("limit") Integer limit,
            @QueryParam("offset") Integer offset,
            @QueryParam("sort") String sort) {
        AuthenticatedPrincipal principal = TokenIntrospectionFilter.currentPrincipal(requestContext);
        ComplaintListResponseDTO response = complaintHandler.listOwnComplaints(principal.getOrgId(),
                principal.getUserId(), status, limit, offset, sort);
        return Response.ok(response).build();
    }

    @GET
    @Path("/categories")
    @RequireScope
    public Response getCategories() {
        return Response.ok(complaintHandler.getCategories()).build();
    }

    @GET
    @Path("/{complaintId}")
    @RequireScope
    public Response getComplaint(@PathParam("complaintId") String complaintId) {
        AuthenticatedPrincipal principal = TokenIntrospectionFilter.currentPrincipal(requestContext);
        ComplaintRecordDTO response =
                complaintHandler.getOwnComplaint(principal.getOrgId(), complaintId, principal.getUserId());
        return Response.ok(response).build();
    }

    @POST
    @Path("/{complaintId}/status")
    @RequireScope
    public Response updateComplaintStatus(
            @PathParam("complaintId") String complaintId,
            MeComplaintStatusUpdateRequestDTO request) {
        AuthenticatedPrincipal principal = TokenIntrospectionFilter.currentPrincipal(requestContext);
        ComplaintStatusUpdateResponseDTO response = complaintHandler.updateOwnStatus(principal.getOrgId(),
                complaintId, principal.getUserId(), principal.getUserName(), request);
        return Response.ok(response).build();
    }
}
