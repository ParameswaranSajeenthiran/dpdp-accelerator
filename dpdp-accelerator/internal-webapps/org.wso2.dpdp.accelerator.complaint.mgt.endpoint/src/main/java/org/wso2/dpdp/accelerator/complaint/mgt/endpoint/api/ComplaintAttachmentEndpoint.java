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

import org.glassfish.jersey.media.multipart.FormDataBodyPart;
import org.glassfish.jersey.media.multipart.FormDataParam;
import org.wso2.carbon.context.PrivilegedCarbonContext;
import org.wso2.dpdp.accelerator.complaint.mgt.service.dto.ComplaintAttachmentDownloadResponseDTO;
import org.wso2.dpdp.accelerator.complaint.mgt.service.dto.ComplaintAttachmentResponseDTO;
import org.wso2.dpdp.accelerator.complaint.mgt.endpoint.handler.ComplaintAttachmentHandler;

import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.util.List;

/**
 * Officer/admin attachment endpoints - see complaint-server-API.yaml "Complaint Management -
 * Attachments". The acting officer's identity is resolved from {@link PrivilegedCarbonContext},
 * the same as {@link ComplaintCommentEndpoint} - every real HTTP caller here holds an any-scope
 * token, i.e. is a COMPLAINT_OFFICER.
 */
@Path("/complaints/{complaintId}/attachments")
@Produces(MediaType.APPLICATION_JSON)
public class ComplaintAttachmentEndpoint {

    private final ComplaintAttachmentHandler attachmentHandler;

    public ComplaintAttachmentEndpoint() {
        this.attachmentHandler = new ComplaintAttachmentHandler();
    }

    public ComplaintAttachmentEndpoint(ComplaintAttachmentHandler attachmentHandler) {
        this.attachmentHandler = attachmentHandler;
    }

    /** Officers may mark evidence isPublic=false to keep it hidden from the Data Principal; defaults to true. */
    @POST
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    public Response uploadComplaintAttachment(
            @PathParam("complaintId") String complaintId,
            @FormDataParam("file") List<FormDataBodyPart> fileParts,
            @FormDataParam("isPublic") Boolean isPublic) {
        String callerUsername = PrivilegedCarbonContext.getThreadLocalCarbonContext().getUsername();
        String callerOrgId = PrivilegedCarbonContext.getThreadLocalCarbonContext().getTenantDomain();
        List<ComplaintAttachmentResponseDTO> response = attachmentHandler.uploadComplaintAttachments(
                callerOrgId, complaintId, fileParts, isPublic, callerUsername, callerUsername);
        return Response.status(Response.Status.CREATED).entity(response).build();
    }

    /** Officers see every attachment regardless of isPublic. */
    @GET
    @Path("/{attachmentId}")
    public Response downloadComplaintAttachment(
            @PathParam("complaintId") String complaintId,
            @PathParam("attachmentId") String attachmentId) {
        String orgId = PrivilegedCarbonContext.getThreadLocalCarbonContext().getTenantDomain();
        ComplaintAttachmentDownloadResponseDTO response =
                attachmentHandler.downloadAttachment(orgId, complaintId, attachmentId);
        return Response.ok(response).build();
    }
}
