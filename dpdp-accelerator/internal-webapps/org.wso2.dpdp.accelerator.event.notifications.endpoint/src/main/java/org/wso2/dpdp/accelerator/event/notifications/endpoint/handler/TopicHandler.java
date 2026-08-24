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

import org.wso2.carbon.context.PrivilegedCarbonContext;
import org.wso2.dpdp.accelerator.event.notifications.service.TopicService;
import org.wso2.dpdp.accelerator.event.notifications.service.dto.TopicDTO;
import org.wso2.dpdp.accelerator.event.notifications.service.model.PaginatedResult;

public class TopicHandler {

    private final TopicService topicService;

    public TopicHandler() {
        TopicService svc = (TopicService) PrivilegedCarbonContext
                .getThreadLocalCarbonContext()
                .getOSGiService(TopicService.class, null);
        if (svc == null) {
            throw new IllegalStateException("TopicService OSGi service not available");
        }
        this.topicService = svc;
    }

    public TopicHandler(TopicService topicService) {
        this.topicService = topicService;
    }

    public TopicDTO createTopic(String orgId, TopicDTO request) {
        String name = request != null ? request.getName() : null;
        String description = request != null ? request.getDescription() : null;
        return topicService.createTopic(orgId, name, description);
    }

    public PaginatedResult<TopicDTO> listTopics(String orgId, String status, String search,
            Integer limit, Integer offset, String sort) {
        int lim = limit == null ? 0 : limit;
        int off = offset == null ? -1 : offset;

        return topicService.listTopics(orgId, status, search, lim, off, sort);
    }

    public TopicDTO deleteTopic(String orgId, String topicId) {
        return topicService.deleteTopic(orgId, topicId);
    }
}
