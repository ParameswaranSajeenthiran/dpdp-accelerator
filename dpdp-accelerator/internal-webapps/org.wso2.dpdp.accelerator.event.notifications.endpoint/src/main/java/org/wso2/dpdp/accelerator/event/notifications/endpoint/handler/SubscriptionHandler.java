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

package org.wso2.dpdp.accelerator.event.notifications.endpoint.handler;

import org.wso2.dpdp.accelerator.event.notifications.service.model.PaginatedResult;

import org.wso2.dpdp.accelerator.event.notifications.endpoint.dto.SubscriptionCreateRequest;
import org.wso2.dpdp.accelerator.event.notifications.endpoint.dto.SubscriptionEventHistoryResponse;
import org.wso2.dpdp.accelerator.event.notifications.endpoint.dto.SubscriptionEventListResponse;
import org.wso2.dpdp.accelerator.event.notifications.endpoint.dto.SubscriptionListResponse;
import org.wso2.dpdp.accelerator.event.notifications.endpoint.dto.SubscriptionResponse;
import org.wso2.dpdp.accelerator.event.notifications.endpoint.mapper.SubscriptionMapper;

import org.wso2.dpdp.accelerator.event.notifications.service.ServiceFactory;
import org.wso2.dpdp.accelerator.event.notifications.service.SubscriptionService;
import org.wso2.dpdp.accelerator.event.notifications.service.dto.DeliveryConfigDTO;
import org.wso2.dpdp.accelerator.event.notifications.service.dto.FilterDTO;
import org.wso2.dpdp.accelerator.event.notifications.service.dto.SubscriptionDTO;
import org.wso2.dpdp.accelerator.event.notifications.service.dto.SubscriptionDeliveryDTO;
import org.wso2.dpdp.accelerator.event.notifications.service.dto.SubscriptionEventHistoryDTO;

public class SubscriptionHandler {

    private final SubscriptionService subscriptionService;

    public SubscriptionHandler() {
        this.subscriptionService = ServiceFactory.createSubscriptionService();
    }

    public SubscriptionHandler(SubscriptionService subscriptionService) {
        this.subscriptionService = subscriptionService;
    }

    public SubscriptionResponse createSubscription(String orgId, String groupId,
            SubscriptionCreateRequest request) {
        String topic = request != null ? request.getTopic() : null;
        FilterDTO filterDTO = SubscriptionMapper.toFilterDTO(request != null ? request.getFilter() : null);
        DeliveryConfigDTO deliveryDTO = SubscriptionMapper
                .toDeliveryConfigDTO(request != null ? request.getDelivery() : null);

        SubscriptionDTO dto = subscriptionService.createSubscription(orgId, groupId, topic, filterDTO, deliveryDTO);
        return SubscriptionMapper.toResponse(dto);
    }

    public SubscriptionListResponse listSubscriptions(String orgId, String status, String purposes,
            String search, Integer limit, Integer offset, String sort) {
        int lim = limit != null && limit > 0 ? limit : 20;
        int off = offset != null && offset >= 0 ? offset : 0;

        PaginatedResult<SubscriptionDTO> result = subscriptionService.listSubscriptions(orgId, status, purposes, search,
                lim, off, sort);
        return SubscriptionMapper.toListResponse(result.getItems(), result.getTotal(), lim, off);
    }

    public SubscriptionResponse getSubscription(String orgId, String subscriptionId) {
        SubscriptionDTO dto = subscriptionService.getSubscription(orgId, subscriptionId);
        return SubscriptionMapper.toResponse(dto);
    }

    public void deleteSubscription(String orgId, String subscriptionId) {
        subscriptionService.deleteSubscription(orgId, subscriptionId);
    }

    public SubscriptionResponse retryVerification(String orgId, String subscriptionId) {
        SubscriptionDTO dto = subscriptionService.retryVerification(orgId, subscriptionId);
        return SubscriptionMapper.toResponse(dto);
    }

    public SubscriptionEventListResponse listSubscriptionEvents(String orgId, String subscriptionId,
            Integer limit, Integer offset) {
        int lim = limit != null && limit > 0 ? limit : 20;
        int off = offset != null && offset >= 0 ? offset : 0;

        PaginatedResult<SubscriptionDeliveryDTO> result = subscriptionService.listSubscriptionEvents(orgId,
                subscriptionId, lim, off);
        return SubscriptionMapper.toEventListResponse(result.getItems(), result.getTotal(), lim, off);
    }

    public SubscriptionEventHistoryResponse getSubscriptionEventHistory(String orgId, String subscriptionId,
            String deliveryId) {
        SubscriptionEventHistoryDTO dto = subscriptionService.getSubscriptionEventHistory(orgId, subscriptionId,
                deliveryId);
        return SubscriptionMapper.toHistoryResponse(dto);
    }
}
