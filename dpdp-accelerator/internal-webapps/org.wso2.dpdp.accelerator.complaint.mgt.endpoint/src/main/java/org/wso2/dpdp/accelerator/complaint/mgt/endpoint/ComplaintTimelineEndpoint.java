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

import org.wso2.dpdp.accelerator.complaint.mgt.endpoint.auth.RequireScope;
import org.wso2.dpdp.accelerator.complaint.mgt.endpoint.auth.TokenIntrospectionFilter;
import org.wso2.dpdp.accelerator.complaint.mgt.endpoint.bean.TimelineListResponseBean;
import org.wso2.dpdp.accelerator.complaint.mgt.endpoint.handler.ComplaintTimelineHandler;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

/**
 * Officer/admin timeline endpoint - see complaint-server-API.yaml "Complaint Management -
 * Timeline". Every entry is returned regardless of isPublic.
 */
@Path("/complaints/{complaintId}/timeline")
@Produces(MediaType.APPLICATION_JSON)
public class ComplaintTimelineEndpoint {

    private final ComplaintTimelineHandler timelineHandler;

    @Context
    private ContainerRequestContext requestContext;

    /** Test seam - Jersey normally field-injects this via @Context. */
    void setRequestContext(ContainerRequestContext requestContext) {
        this.requestContext = requestContext;
    }

    public ComplaintTimelineEndpoint() {
        this.timelineHandler = new ComplaintTimelineHandler();
    }

    public ComplaintTimelineEndpoint(ComplaintTimelineHandler timelineHandler) {
        this.timelineHandler = timelineHandler;
    }

    @GET
    @RequireScope
    public Response getTimeline(
            @PathParam("complaintId") String complaintId,
            @QueryParam("fromTime") Long fromTime,
            @QueryParam("toTime") Long toTime,
            @QueryParam("order") String order,
            @QueryParam("limit") Integer limit,
            @QueryParam("offset") Integer offset) {
        String orgId = TokenIntrospectionFilter.currentPrincipal(requestContext).getOrgId();
        TimelineListResponseBean response =
                timelineHandler.getTimeline(orgId, complaintId, fromTime, toTime, order, limit, offset);
        return Response.ok(response).build();
    }
}
