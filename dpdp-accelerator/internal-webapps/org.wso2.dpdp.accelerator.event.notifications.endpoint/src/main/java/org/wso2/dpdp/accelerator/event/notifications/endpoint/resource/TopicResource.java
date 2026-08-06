/**
 * Copyright (c) 2026, WSO2 LLC. (https://www.wso2.com).
 * <p>
 * WSO2 LLC. licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 *     http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.wso2.dpdp.accelerator.event.notifications.endpoint.resource;

import org.wso2.dpdp.accelerator.event.notifications.endpoint.dto.TopicCreateRequest;
import org.wso2.dpdp.accelerator.event.notifications.endpoint.dto.TopicListResponse;
import org.wso2.dpdp.accelerator.event.notifications.endpoint.dto.TopicResponse;
import org.wso2.dpdp.accelerator.event.notifications.endpoint.handler.TopicHandler;

import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.HeaderParam;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

@Path("/topics")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class TopicResource {

    private final TopicHandler topicHandler;

    public TopicResource() {
        this.topicHandler = new TopicHandler();
    }

    public TopicResource(TopicHandler topicHandler) {
        this.topicHandler = topicHandler;
    }

    @POST
    public Response createTopic(
            @HeaderParam("X-Org-Id") String orgIdHeader,
            @QueryParam("orgId") String orgIdQuery,
            TopicCreateRequest request) {
        String orgId = (orgIdHeader != null && !orgIdHeader.isBlank()) ? orgIdHeader : orgIdQuery;
        TopicResponse response = topicHandler.createTopic(orgId, request);
        return Response.status(Response.Status.CREATED).entity(response).build();
    }

    @GET
    public Response listTopics(
            @HeaderParam("X-Org-Id") String orgIdHeader,
            @QueryParam("orgId") String orgIdQuery,
            @QueryParam("status") String status,
            @QueryParam("search") String search,
            @QueryParam("limit") Integer limit,
            @QueryParam("offset") Integer offset,
            @QueryParam("sort") String sort) {
        String effectiveOrgId = (orgIdHeader != null && !orgIdHeader.isBlank()) ? orgIdHeader : orgIdQuery;
        TopicListResponse response = topicHandler.listTopics(effectiveOrgId, status, search, limit, offset, sort);
        return Response.ok(response).build();
    }

    @DELETE
    @Path("/{topicId}")
    public Response deleteTopic(
            @HeaderParam("X-Org-Id") String orgIdHeader,
            @QueryParam("orgId") String orgIdQuery,
            @PathParam("topicId") String topicId) {
        String orgId = (orgIdHeader != null && !orgIdHeader.isBlank()) ? orgIdHeader : orgIdQuery;
        TopicResponse response = topicHandler.deleteTopic(orgId, topicId);
        return Response.ok(response).build();
    }
}
