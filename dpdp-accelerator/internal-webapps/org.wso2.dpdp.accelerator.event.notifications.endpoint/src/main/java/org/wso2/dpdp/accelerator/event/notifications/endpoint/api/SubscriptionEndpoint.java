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
import org.wso2.dpdp.accelerator.event.notifications.service.SubscriptionService;
import org.wso2.dpdp.accelerator.event.notifications.service.dto.DeliveryConfigDTO;
import org.wso2.dpdp.accelerator.event.notifications.service.dto.FilterDTO;
import org.wso2.dpdp.accelerator.event.notifications.service.dto.SubscriptionDTO;
import org.wso2.dpdp.accelerator.event.notifications.service.dto.SubscriptionDeliveryDTO;
import org.wso2.dpdp.accelerator.event.notifications.service.dto.SubscriptionEventHistoryDTO;
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

@Path("/subscriptions")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class SubscriptionEndpoint {

    private final SubscriptionService subscriptionService;

    public SubscriptionEndpoint() {
        SubscriptionService svc = (SubscriptionService) PrivilegedCarbonContext
                .getThreadLocalCarbonContext()
                .getOSGiService(SubscriptionService.class, null);
        if (svc == null) {
            throw new IllegalStateException("SubscriptionService OSGi service not available");
        }
        this.subscriptionService = svc;
    }

    public SubscriptionEndpoint(SubscriptionService subscriptionService) {
        this.subscriptionService = subscriptionService;
    }

    @POST
    public Response createSubscription(
            @HeaderParam("org-id") String orgId,
            @HeaderParam("group-id") String headerGroupId,
            SubscriptionDTO request) {
        String groupId = (request != null && request.getGroupId() != null && !request.getGroupId().trim().isEmpty())
                ? request.getGroupId().trim() : headerGroupId;
        String topicName = request != null ? request.getTopic() : null;
        FilterDTO filter = (request != null) ? request.getFilter() : null;
        DeliveryConfigDTO delivery = (request != null) ? request.getDelivery() : null;

        SubscriptionDTO dto = subscriptionService.createSubscription(orgId, groupId, topicName, filter, delivery);
        return Response.status(Response.Status.CREATED).entity(dto).build();
    }

    @GET
    public Response listSubscriptions(
            @HeaderParam("org-id") String orgId,
            @QueryParam("status") String status,
            @QueryParam("purposes") String purposes,
            @QueryParam("search") String search,
            @QueryParam("limit") @DefaultValue("20") int limit,
            @QueryParam("offset") @DefaultValue("0") int offset,
            @QueryParam("sort") String sort) {
        PaginatedResult<SubscriptionDTO> result = subscriptionService.listSubscriptions(
                orgId, status, purposes, search, limit, offset, sort);
        return Response.ok(result).build();
    }

    @GET
    @Path("/{subscriptionId}")
    public Response getSubscription(
            @HeaderParam("org-id") String orgId,
            @PathParam("subscriptionId") String subscriptionId) {
        SubscriptionDTO dto = subscriptionService.getSubscription(orgId, subscriptionId);
        return Response.ok(dto).build();
    }

    @DELETE
    @Path("/{subscriptionId}")
    public Response deleteSubscription(
            @HeaderParam("org-id") String orgId,
            @PathParam("subscriptionId") String subscriptionId) {
        subscriptionService.deleteSubscription(orgId, subscriptionId);
        return Response.noContent().build();
    }

    @POST
    @Path("/{subscriptionId}/verify")
    public Response retryVerification(
            @HeaderParam("org-id") String orgId,
            @PathParam("subscriptionId") String subscriptionId) {
        SubscriptionDTO dto = subscriptionService.retryVerification(orgId, subscriptionId);
        return Response.ok(dto).build();
    }

    @GET
    @Path("/{subscriptionId}/events")
    public Response listSubscriptionEvents(
            @HeaderParam("org-id") String orgId,
            @PathParam("subscriptionId") String subscriptionId,
            @QueryParam("limit") @DefaultValue("20") int limit,
            @QueryParam("offset") @DefaultValue("0") int offset) {
        PaginatedResult<SubscriptionDeliveryDTO> result = subscriptionService.listSubscriptionEvents(
                orgId, subscriptionId, limit, offset);
        return Response.ok(result).build();
    }

    @GET
    @Path("/{subscriptionId}/events/{deliveryId}")
    public Response getSubscriptionEventHistory(
            @HeaderParam("org-id") String orgId,
            @PathParam("subscriptionId") String subscriptionId,
            @PathParam("deliveryId") String deliveryId) {
        SubscriptionEventHistoryDTO dto = subscriptionService.getSubscriptionEventHistory(
                orgId, subscriptionId, deliveryId);
        return Response.ok(dto).build();
    }
}
