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
import org.wso2.dpdp.accelerator.event.notifications.service.dto.SubscriptionDeliveryDTO;
import org.wso2.dpdp.accelerator.event.notifications.service.dto.SubscriptionEventHistoryDTO;
import org.wso2.dpdp.accelerator.event.notifications.service.model.PaginatedResult;

import javax.ws.rs.Consumes;
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

/**
 * JAX-RS endpoint for event publication, delivery listing, and delivery audit history.
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
    public Response listEvents(
            @HeaderParam("org-id") String orgId,
            @QueryParam("topic") String topic,
            @QueryParam("status") String status,
            @QueryParam("subscriptionId") String subscriptionId,
            @QueryParam("groupId") String groupId,
            @QueryParam("purposes") String purposes,
            @QueryParam("search") String search,
            @QueryParam("limit") @DefaultValue("20") int limit,
            @QueryParam("offset") @DefaultValue("0") int offset) {
        PaginatedResult<EventDTO> result = eventHandler.searchEvents(
                orgId, topic, status, groupId, purposes, search, limit, offset);
        return Response.ok(result).build();
    }

    @GET
    @Path("/{deliveryId}/history")
    public Response getDeliveryHistory(
            @HeaderParam("org-id") String orgId,
            @PathParam("deliveryId") String deliveryId) {
        SubscriptionEventHistoryDTO dto = eventHandler.getDeliveryHistory(orgId, deliveryId);
        return Response.ok(dto).build();
    }

    @GET
    @Path("/{eventId}")
    public Response getEvent(
            @HeaderParam("org-id") String orgId,
            @PathParam("eventId") String eventId) {
        EventDTO dto = eventHandler.getEventById(orgId, eventId);
        return Response.ok(dto).build();
    }

    @GET
    @Path("/{eventId}/deliveries")
    public Response getEventDeliveries(
            @HeaderParam("org-id") String orgId,
            @PathParam("eventId") String eventId,
            @QueryParam("limit") @DefaultValue("20") int limit,
            @QueryParam("offset") @DefaultValue("0") int offset) {
        PaginatedResult<SubscriptionDeliveryDTO> result = eventHandler.getEventDeliveries(
                orgId, eventId, limit, offset);
        return Response.ok(result).build();
    }
}
