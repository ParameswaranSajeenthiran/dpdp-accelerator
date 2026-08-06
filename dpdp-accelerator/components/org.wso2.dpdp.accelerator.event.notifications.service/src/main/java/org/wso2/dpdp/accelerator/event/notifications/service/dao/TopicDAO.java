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

package org.wso2.dpdp.accelerator.event.notifications.service.dao;

import org.wso2.dpdp.accelerator.event.notifications.service.model.PaginatedResult;
import org.wso2.dpdp.accelerator.event.notifications.service.dao.model.Topic;

import java.util.List;
import java.util.Map;
import java.util.Optional;

public interface TopicDAO {

    boolean addTopic(Topic topic);

    Optional<Topic> getTopicById(String topicId);

    Optional<Topic> getTopicByOrgAndName(String orgId, String name);

    boolean updateTopicStatus(String topicId, String status);

    PaginatedResult<Topic> listTopics(String orgId, String status, String search, int limit, int offset, String sort);

    boolean hasActiveSubscriptions(String topicId);

    /**
     * Returns a map of topicId → topicName for the given set of IDs in a single query.
     * Unknown IDs are simply omitted from the result map.
     */
    Map<String, String> getTopicNamesByIds(List<String> topicIds);
}
