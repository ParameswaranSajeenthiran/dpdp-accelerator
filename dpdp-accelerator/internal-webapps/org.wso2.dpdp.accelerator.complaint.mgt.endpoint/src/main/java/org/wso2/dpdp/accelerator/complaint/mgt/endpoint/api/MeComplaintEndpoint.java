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
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

/**
 * "Me" (Data Principal) namespace - see complaint-server-API.yaml. Identity is always the caller's
 * own {@link PrivilegedCarbonContext} username; there is no user-id header or body field anywhere
 * here, so a token belonging to one user can never be used to act on another user's complaint.
 */
@Path("/me/complaints")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class MeComplaintEndpoint {

    private final ComplaintHandler complaintHandler;

    public MeComplaintEndpoint() {
        this.complaintHandler = new ComplaintHandler();
    }

    public MeComplaintEndpoint(ComplaintHandler complaintHandler) {
        this.complaintHandler = complaintHandler;
    }

    @POST
    public Response createComplaint(MeComplaintCreateRequestDTO request) {
        String callerUsername = PrivilegedCarbonContext.getThreadLocalCarbonContext().getUsername();
        String callerOrgId = PrivilegedCarbonContext.getThreadLocalCarbonContext().getTenantDomain();
        ComplaintCreateResponseDTO response = complaintHandler.createOwnComplaint(callerOrgId,
                callerUsername, callerUsername, request);
        return Response.status(Response.Status.CREATED).entity(response).build();
    }

    @GET
    public Response listComplaints(
            @QueryParam("status") String status,
            @QueryParam("limit") Integer limit,
            @QueryParam("offset") Integer offset,
            @QueryParam("sort") String sort) {
        String callerUsername = PrivilegedCarbonContext.getThreadLocalCarbonContext().getUsername();
        String callerOrgId = PrivilegedCarbonContext.getThreadLocalCarbonContext().getTenantDomain();
        ComplaintListResponseDTO response = complaintHandler.listOwnComplaints(callerOrgId,
                callerUsername, status, limit, offset, sort);
        return Response.ok(response).build();
    }

    @GET
    @Path("/categories")
    public Response getCategories() {
        return Response.ok(complaintHandler.getCategories()).build();
    }

    @GET
    @Path("/{complaintId}")
    public Response getComplaint(@PathParam("complaintId") String complaintId) {
        String callerUsername = PrivilegedCarbonContext.getThreadLocalCarbonContext().getUsername();
        String callerOrgId = PrivilegedCarbonContext.getThreadLocalCarbonContext().getTenantDomain();
        ComplaintRecordDTO response =
                complaintHandler.getOwnComplaint(callerOrgId, complaintId, callerUsername);
        return Response.ok(response).build();
    }

    @POST
    @Path("/{complaintId}/status")
    public Response updateComplaintStatus(
            @PathParam("complaintId") String complaintId,
            MeComplaintStatusUpdateRequestDTO request) {
        String callerUsername = PrivilegedCarbonContext.getThreadLocalCarbonContext().getUsername();
        String callerOrgId = PrivilegedCarbonContext.getThreadLocalCarbonContext().getTenantDomain();
        ComplaintStatusUpdateResponseDTO response = complaintHandler.updateOwnStatus(callerOrgId,
                complaintId, callerUsername, callerUsername, request);
        return Response.ok(response).build();
    }
}
