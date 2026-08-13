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

import org.wso2.dpdp.accelerator.event.notifications.endpoint.handler.EventHandler;
import org.wso2.dpdp.accelerator.event.notifications.service.dto.EventCreateDTO;
import org.wso2.dpdp.accelerator.event.notifications.service.dto.EventDTO;
import org.wso2.dpdp.accelerator.event.notifications.service.model.PaginatedResult;

import javax.ws.rs.Consumes;
import javax.ws.rs.DefaultValue;
import javax.ws.rs.GET;
import javax.ws.rs.HeaderParam;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

/**
 * JAX-RS endpoint for event publication and search.
 *
 * <p>{@code POST /events} persists + fans out a single event. {@code GET /events}
 * performs a paginated, search-only list. The {@code GET /events/{deliveryId}/history}
 * operation is still scheduled for a follow-up once the poll-mode fan-out lands.</p>
 */
@Path("/events")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class EventEndpoint {

    private final EventHandler eventHandler;

    public EventEndpoint() {
        this.eventHandler = new EventHandler();
    }

    public EventEndpoint(EventHandler eventHandler) {
        this.eventHandler = eventHandler;
    }

    @POST
    public Response publishEvent(
            @HeaderParam("org-id") String orgId,
            @HeaderParam("group-id") String groupId,
            EventCreateDTO request) {
        EventDTO dto = eventHandler.publishEvent(orgId, groupId, request);
        return Response.status(Response.Status.CREATED).entity(dto).build();
    }

    @GET
    public Response searchEvents(
            @HeaderParam("org-id") String orgId,
            @QueryParam("search") String search,
            @QueryParam("limit") @DefaultValue("20") int limit,
            @QueryParam("offset") @DefaultValue("0") int offset) {
        PaginatedResult<EventDTO> result = eventHandler.searchEvents(orgId, search, limit, offset);
        return Response.ok(result).build();
    }
}
