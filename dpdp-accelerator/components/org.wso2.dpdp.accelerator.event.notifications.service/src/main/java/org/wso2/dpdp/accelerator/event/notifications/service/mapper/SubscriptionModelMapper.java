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

package org.wso2.dpdp.accelerator.event.notifications.service.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;
import org.mapstruct.factory.Mappers;

import org.wso2.dpdp.accelerator.event.notifications.service.enums.DeliveryMode;
import org.wso2.dpdp.accelerator.event.notifications.service.enums.PurposeFilterMode;
import org.wso2.dpdp.accelerator.event.notifications.service.enums.SubscriptionStatus;
import org.wso2.dpdp.accelerator.event.notifications.service.dao.model.Subscription;

import org.wso2.dpdp.accelerator.event.notifications.service.dto.DeliveryConfigDTO;
import org.wso2.dpdp.accelerator.event.notifications.service.dto.FilterDTO;
import org.wso2.dpdp.accelerator.event.notifications.service.dto.SubscriptionDTO;

import java.sql.Timestamp;

/**
 * MapStruct mapper for converting {@link Subscription} DAO models to
 * {@link SubscriptionDTO} service DTOs.
 */

@Mapper
public interface SubscriptionModelMapper {

    SubscriptionModelMapper INSTANCE = Mappers.getMapper(SubscriptionModelMapper.class);

    @Mapping(target = "status", source = "status", qualifiedByName = "mapSubscriptionStatus")
    @Mapping(target = "topic", ignore = true)
    @Mapping(target = "alreadyExists", ignore = true)
    @Mapping(target = "message", ignore = true)
    @Mapping(target = "createdAt", source = "createdAt", qualifiedByName = "mapTimestampToLong")
    @Mapping(target = "updatedAt", source = "updatedAt", qualifiedByName = "mapTimestampToLong")
    @Mapping(target = "filter", expression = "java(mapFilter(sub))")
    @Mapping(target = "delivery", expression = "java(mapDelivery(sub))")
    SubscriptionDTO toDTO(Subscription sub);

    static SubscriptionDTO toDTO(Subscription sub, String topicName) {
        SubscriptionDTO dto = INSTANCE.toDTO(sub);
        if (dto != null) {
            dto.setTopic(topicName);
        }
        return dto;
    }

    @Named("mapSubscriptionStatus")
    default SubscriptionStatus mapSubscriptionStatus(String status) {
        return SubscriptionStatus.fromValueOrDefault(status, SubscriptionStatus.ACTIVE);
    }

    @Named("mapTimestampToLong")
    default Long mapTimestampToLong(Timestamp ts) {
        return ts != null ? ts.getTime() : null;
    }

    default FilterDTO mapFilter(Subscription sub) {
        if (sub == null)
            return null;
        PurposeFilterMode filterType = PurposeFilterMode.fromValueOrDefault(sub.getPurposeFilterMode(),
                PurposeFilterMode.ALL);
        return new FilterDTO(filterType, sub.getPurposes());
    }

    default DeliveryConfigDTO mapDelivery(Subscription sub) {
        if (sub == null)
            return null;
        DeliveryMode deliveryMode = DeliveryMode.fromValueOrDefault(sub.getDeliveryMode(), DeliveryMode.WEBHOOK);
        return new DeliveryConfigDTO(deliveryMode, sub.getCallbackUrl(), sub.getSharedSecret());
    }
}
