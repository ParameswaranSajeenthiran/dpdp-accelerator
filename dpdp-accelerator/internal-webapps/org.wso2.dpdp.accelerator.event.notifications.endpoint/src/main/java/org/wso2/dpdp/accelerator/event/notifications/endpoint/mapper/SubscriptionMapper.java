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

package org.wso2.dpdp.accelerator.event.notifications.endpoint.mapper;

import org.wso2.dpdp.accelerator.event.notifications.service.enums.DeliveryMode;
import org.wso2.dpdp.accelerator.event.notifications.service.enums.PurposeFilterMode;

import org.wso2.dpdp.accelerator.event.notifications.endpoint.dto.DeliveryConfig;
import org.wso2.dpdp.accelerator.event.notifications.endpoint.dto.DeliveryConfigOut;
import org.wso2.dpdp.accelerator.event.notifications.endpoint.dto.PurposeFilter;
import org.wso2.dpdp.accelerator.event.notifications.endpoint.dto.SubscriptionDeliveryAttempt;
import org.wso2.dpdp.accelerator.event.notifications.endpoint.dto.SubscriptionEventHistoryResponse;
import org.wso2.dpdp.accelerator.event.notifications.endpoint.dto.SubscriptionEventItem;
import org.wso2.dpdp.accelerator.event.notifications.endpoint.dto.SubscriptionEventListResponse;
import org.wso2.dpdp.accelerator.event.notifications.endpoint.dto.SubscriptionListResponse;
import org.wso2.dpdp.accelerator.event.notifications.endpoint.dto.SubscriptionResponse;

import org.wso2.dpdp.accelerator.event.notifications.service.dto.DeliveryConfigDTO;
import org.wso2.dpdp.accelerator.event.notifications.service.dto.FilterDTO;
import org.wso2.dpdp.accelerator.event.notifications.service.dto.SubscriptionDTO;
import org.wso2.dpdp.accelerator.event.notifications.service.dto.SubscriptionDeliveryAttemptDTO;
import org.wso2.dpdp.accelerator.event.notifications.service.dto.SubscriptionDeliveryDTO;
import org.wso2.dpdp.accelerator.event.notifications.service.dto.SubscriptionEventHistoryDTO;

import java.util.ArrayList;
import java.util.List;

public final class SubscriptionMapper {

    private SubscriptionMapper() {
    }

    public static FilterDTO toFilterDTO(PurposeFilter filter) {
        if (filter == null) {
            return null;
        }
        PurposeFilterMode mode = filter.getType() != null ? PurposeFilterMode.fromValue(filter.getType()) : null;
        return new FilterDTO(mode, filter.getPurposes());
    }

    public static DeliveryConfigDTO toDeliveryConfigDTO(DeliveryConfig config) {
        if (config == null) {
            return null;
        }
        DeliveryMode mode = config.getMode() != null ? DeliveryMode.fromValue(config.getMode()) : null;
        return new DeliveryConfigDTO(mode, config.getCallbackUrl(), config.getSharedSecret());
    }

    public static SubscriptionResponse toResponse(SubscriptionDTO dto) {
        if (dto == null) {
            return null;
        }
        PurposeFilter filter = dto.getFilter() != null
                ? new PurposeFilter(dto.getFilter().getType() != null ? dto.getFilter().getType().getValue() : null,
                        dto.getFilter().getPurposes())
                : null;
        DeliveryConfigOut delivery = dto.getDelivery() != null
                ? new DeliveryConfigOut(
                        dto.getDelivery().getMode() != null ? dto.getDelivery().getMode().getValue() : null,
                        dto.getDelivery().getCallbackUrl())
                : null;
        SubscriptionResponse response = new SubscriptionResponse(
                dto.getSubscriptionId(),
                dto.getTopic(),
                filter,
                delivery,
                dto.getStatus() != null ? dto.getStatus().getValue() : null,
                dto.getCreatedAt(),
                dto.getUpdatedAt());
        response.setAlreadyExists(dto.getAlreadyExists());
        response.setMessage(dto.getMessage());
        return response;
    }

    public static SubscriptionListResponse toListResponse(List<SubscriptionDTO> dtos, int total, int limit,
            int offset) {
        List<SubscriptionResponse> dtoList = new ArrayList<>();
        if (dtos != null) {
            for (SubscriptionDTO dto : dtos) {
                dtoList.add(toResponse(dto));
            }
        }
        return new SubscriptionListResponse(dtoList, total, limit, offset, dtoList.size());
    }

    public static SubscriptionEventItem toEventItem(SubscriptionDeliveryDTO dto) {
        if (dto == null) {
            return null;
        }
        return new SubscriptionEventItem(
                dto.getDeliveryId(),
                dto.getEventId(),
                dto.getTopic(),
                dto.getCurrentStatus(),
                dto.getDeliveryMode(),
                dto.getOccurredAt());
    }

    public static SubscriptionEventListResponse toEventListResponse(List<SubscriptionDeliveryDTO> dtos, int total,
            int limit, int offset) {
        List<SubscriptionEventItem> items = new ArrayList<>();
        if (dtos != null) {
            for (SubscriptionDeliveryDTO dto : dtos) {
                items.add(toEventItem(dto));
            }
        }
        return new SubscriptionEventListResponse(items, total, limit, offset, items.size());
    }

    public static SubscriptionEventHistoryResponse toHistoryResponse(SubscriptionEventHistoryDTO dto) {
        if (dto == null) {
            return null;
        }
        SubscriptionEventHistoryResponse response = new SubscriptionEventHistoryResponse(
                dto.getDeliveryId(),
                dto.getEventId(),
                dto.getTopic(),
                dto.getDeliveryMode(),
                dto.getCurrentStatus(),
                dto.getOccurredAt());
        response.setNextRetryAt(dto.getNextRetryAt());
        response.setCompletionStatus(dto.getCompletionStatus());
        response.setCompletionEvidence(dto.getCompletionEvidence());

        List<SubscriptionDeliveryAttempt> attempts = new ArrayList<>();
        if (dto.getHistory() != null) {
            for (SubscriptionDeliveryAttemptDTO att : dto.getHistory()) {
                attempts.add(new SubscriptionDeliveryAttempt(
                        att.getAttempt(),
                        att.getStatus(),
                        att.getTimestamp(),
                        att.getHttpStatus(),
                        att.getError()));
            }
        }
        response.setHistory(attempts);
        return response;
    }
}
