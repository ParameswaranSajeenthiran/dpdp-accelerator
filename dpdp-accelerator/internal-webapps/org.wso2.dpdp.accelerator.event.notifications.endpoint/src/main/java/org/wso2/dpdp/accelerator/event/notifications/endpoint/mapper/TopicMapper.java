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

import org.wso2.dpdp.accelerator.event.notifications.endpoint.dto.TopicCreateRequest;
import org.wso2.dpdp.accelerator.event.notifications.endpoint.dto.TopicListResponse;
import org.wso2.dpdp.accelerator.event.notifications.endpoint.dto.TopicResponse;
import org.wso2.dpdp.accelerator.event.notifications.service.dto.TopicDTO;

import java.util.ArrayList;
import java.util.List;

public class TopicMapper {

    private TopicMapper() {
    }

    public static String[] toServiceParams(TopicCreateRequest request) {
        if (request == null) {
            return new String[]{null, null};
        }
        return new String[]{request.getName(), request.getDescription()};
    }

    public static TopicResponse toResponse(TopicDTO dto) {
        if (dto == null) {
            return null;
        }
        return new TopicResponse(dto.getTopicId(), dto.getName(), dto.getDescription(), dto.getStatus());
    }

    public static TopicListResponse toListResponse(List<TopicDTO> dtoList, int total, int limit, int offset) {
        List<TopicResponse> list = new ArrayList<>();
        if (dtoList != null) {
            for (TopicDTO dto : dtoList) {
                list.add(toResponse(dto));
            }
        }
        return new TopicListResponse(list, total, limit, offset, list.size());
    }
}
