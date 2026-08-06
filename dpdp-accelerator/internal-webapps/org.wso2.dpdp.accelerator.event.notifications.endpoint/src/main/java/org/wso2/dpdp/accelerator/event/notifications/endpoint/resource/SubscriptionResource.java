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

import org.wso2.dpdp.accelerator.event.notifications.endpoint.dto.SubscriptionCreateRequest;
import org.wso2.dpdp.accelerator.event.notifications.endpoint.dto.SubscriptionEventHistoryResponse;
import org.wso2.dpdp.accelerator.event.notifications.endpoint.dto.SubscriptionEventListResponse;
import org.wso2.dpdp.accelerator.event.notifications.endpoint.dto.SubscriptionListResponse;
import org.wso2.dpdp.accelerator.event.notifications.endpoint.dto.SubscriptionResponse;
import org.wso2.dpdp.accelerator.event.notifications.endpoint.handler.SubscriptionHandler;

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

@Path("/subscriptions")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class SubscriptionResource {

    private final SubscriptionHandler subscriptionHandler;

    public SubscriptionResource() {
        this.subscriptionHandler = new SubscriptionHandler();
    }

    public SubscriptionResource(SubscriptionHandler subscriptionHandler) {
        this.subscriptionHandler = subscriptionHandler;
    }

    @POST
    public Response createSubscription(
            @HeaderParam("X-Org-Id") String orgIdHeader,
            @QueryParam("orgId") String orgIdQuery,
            @HeaderParam("X-Group-Id") String groupIdHeader,
            @QueryParam("groupId") String groupIdQuery,
            SubscriptionCreateRequest request) {
        String orgId = (orgIdHeader != null && !orgIdHeader.isBlank()) ? orgIdHeader : orgIdQuery;
        String groupId = (groupIdHeader != null && !groupIdHeader.isBlank()) ? groupIdHeader : groupIdQuery;
        SubscriptionResponse response = subscriptionHandler.createSubscription(orgId, groupId, request);
        if (Boolean.TRUE.equals(response.getAlreadyExists())) {
            return Response.ok(response).build();
        }
        return Response.status(Response.Status.CREATED).entity(response).build();
    }

    @GET
    public Response listSubscriptions(
            @HeaderParam("X-Org-Id") String orgIdHeader,
            @QueryParam("orgId") String orgIdQuery,
            @QueryParam("status") String status,
            @QueryParam("purposes") String purposes,
            @QueryParam("search") String search,
            @QueryParam("limit") Integer limit,
            @QueryParam("offset") Integer offset,
            @QueryParam("sort") String sort) {
        String orgId = (orgIdHeader != null && !orgIdHeader.isBlank()) ? orgIdHeader : orgIdQuery;
        SubscriptionListResponse response = subscriptionHandler.listSubscriptions(orgId, status, purposes, search,
                limit, offset, sort);
        return Response.ok(response).build();
    }

    @GET
    @Path("/{subscriptionId}")
    public Response getSubscription(
            @HeaderParam("X-Org-Id") String orgIdHeader,
            @QueryParam("orgId") String orgIdQuery,
            @PathParam("subscriptionId") String subscriptionId) {
        String orgId = (orgIdHeader != null && !orgIdHeader.isBlank()) ? orgIdHeader : orgIdQuery;
        SubscriptionResponse response = subscriptionHandler.getSubscription(orgId, subscriptionId);
        return Response.ok(response).build();
    }

    @DELETE
    @Path("/{subscriptionId}")
    public Response deleteSubscription(
            @HeaderParam("X-Org-Id") String orgIdHeader,
            @QueryParam("orgId") String orgIdQuery,
            @PathParam("subscriptionId") String subscriptionId) {
        String orgId = (orgIdHeader != null && !orgIdHeader.isBlank()) ? orgIdHeader : orgIdQuery;
        subscriptionHandler.deleteSubscription(orgId, subscriptionId);
        return Response.noContent().build();
    }

    @POST
    @Path("/{subscriptionId}/verify")
    public Response retryVerification(
            @HeaderParam("X-Org-Id") String orgIdHeader,
            @QueryParam("orgId") String orgIdQuery,
            @PathParam("subscriptionId") String subscriptionId) {
        String orgId = (orgIdHeader != null && !orgIdHeader.isBlank()) ? orgIdHeader : orgIdQuery;
        SubscriptionResponse response = subscriptionHandler.retryVerification(orgId, subscriptionId);
        return Response.ok(response).build();
    }

    @GET
    @Path("/{subscriptionId}/events")
    public Response listSubscriptionEvents(
            @HeaderParam("X-Org-Id") String orgIdHeader,
            @QueryParam("orgId") String orgIdQuery,
            @PathParam("subscriptionId") String subscriptionId,
            @QueryParam("limit") Integer limit,
            @QueryParam("offset") Integer offset) {
        String orgId = (orgIdHeader != null && !orgIdHeader.isBlank()) ? orgIdHeader : orgIdQuery;
        SubscriptionEventListResponse response = subscriptionHandler.listSubscriptionEvents(orgId, subscriptionId,
                limit, offset);
        return Response.ok(response).build();
    }

    @GET
    @Path("/{subscriptionId}/events/{deliveryId}/history")
    public Response getSubscriptionEventHistory(
            @HeaderParam("X-Org-Id") String orgIdHeader,
            @QueryParam("orgId") String orgIdQuery,
            @PathParam("subscriptionId") String subscriptionId,
            @PathParam("deliveryId") String deliveryId) {
        String orgId = (orgIdHeader != null && !orgIdHeader.isBlank()) ? orgIdHeader : orgIdQuery;
        SubscriptionEventHistoryResponse response = subscriptionHandler.getSubscriptionEventHistory(orgId,
                subscriptionId, deliveryId);
        return Response.ok(response).build();
    }
}
