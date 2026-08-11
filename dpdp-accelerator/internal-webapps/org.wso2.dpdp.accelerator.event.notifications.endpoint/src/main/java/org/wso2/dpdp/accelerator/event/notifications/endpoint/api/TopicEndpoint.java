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

package org.wso2.dpdp.accelerator.event.notifications.endpoint.api;

import org.wso2.carbon.context.PrivilegedCarbonContext;
import org.wso2.dpdp.accelerator.event.notifications.service.TopicService;
import org.wso2.dpdp.accelerator.event.notifications.service.dto.TopicDTO;
import org.wso2.dpdp.accelerator.event.notifications.service.model.PaginatedResult;

import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.DefaultValue;
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
public class TopicEndpoint {

    private final TopicService topicService;

    public TopicEndpoint() {
        TopicService svc = (TopicService) PrivilegedCarbonContext
                .getThreadLocalCarbonContext()
                .getOSGiService(TopicService.class, null);
        if (svc == null) {
            throw new IllegalStateException("TopicService OSGi service not available");
        }
        this.topicService = svc;
    }

    public TopicEndpoint(TopicService topicService) {
        this.topicService = topicService;
    }

    @POST
    public Response createTopic(@HeaderParam("org-id") String orgId, TopicDTO request) {
        String name = request != null ? request.getName() : null;
        String description = request != null ? request.getDescription() : null;
        TopicDTO dto = topicService.createTopic(orgId, name, description);
        return Response.status(Response.Status.CREATED).entity(dto).build();
    }

    @GET
    public Response listTopics(
            @HeaderParam("org-id") String orgId,
            @QueryParam("status") String status,
            @QueryParam("search") String search,
            @QueryParam("limit") @DefaultValue("20") int limit,
            @QueryParam("offset") @DefaultValue("0") int offset,
            @QueryParam("sort") String sort) {
        PaginatedResult<TopicDTO> result = topicService.listTopics(orgId, status, search, limit, offset, sort);
        return Response.ok(result).build();
    }

    @DELETE
    @Path("/{topicId}")
    public Response deleteTopic(
            @HeaderParam("org-id") String orgId,
            @PathParam("topicId") String topicId) {
        TopicDTO dto = topicService.deleteTopic(orgId, topicId);
        return Response.ok(dto).build();
    }
}
