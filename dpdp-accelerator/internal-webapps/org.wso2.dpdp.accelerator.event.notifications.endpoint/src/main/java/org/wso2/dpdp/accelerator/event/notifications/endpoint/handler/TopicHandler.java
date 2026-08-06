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

import org.wso2.dpdp.accelerator.event.notifications.endpoint.dto.TopicCreateRequest;
import org.wso2.dpdp.accelerator.event.notifications.endpoint.dto.TopicListResponse;
import org.wso2.dpdp.accelerator.event.notifications.endpoint.dto.TopicResponse;
import org.wso2.dpdp.accelerator.event.notifications.endpoint.mapper.TopicMapper;

import org.wso2.dpdp.accelerator.event.notifications.service.ServiceFactory;
import org.wso2.dpdp.accelerator.event.notifications.service.TopicService;
import org.wso2.dpdp.accelerator.event.notifications.service.dto.TopicDTO;

import org.wso2.dpdp.accelerator.event.notifications.service.model.PaginatedResult;

public class TopicHandler {

    private final TopicService topicService;

    public TopicHandler() {
        this.topicService = ServiceFactory.createTopicService();
    }

    public TopicHandler(TopicService topicService) {
        this.topicService = topicService;
    }

    public TopicResponse createTopic(String orgId, TopicCreateRequest request) {
        String[] params = TopicMapper.toServiceParams(request);
        TopicDTO dto = topicService.createTopic(orgId, params[0], params[1]);
        return TopicMapper.toResponse(dto);
    }

    public TopicListResponse listTopics(String orgId, String status, String search,
            Integer limit, Integer offset, String sort) {
        int lim = limit != null && limit > 0 ? limit : 20;
        int off = offset != null && offset >= 0 ? offset : 0;

        PaginatedResult<TopicDTO> result = topicService.listTopics(orgId, status, search, lim, off, sort);
        return TopicMapper.toListResponse(result.getItems(), result.getTotal(), lim, off);
    }

    public TopicResponse deleteTopic(String orgId, String topicId) {
        TopicDTO dto = topicService.deleteTopic(orgId, topicId);
        return TopicMapper.toResponse(dto);
    }
}
