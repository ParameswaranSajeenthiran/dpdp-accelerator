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

package org.wso2.dpdp.accelerator.event.notifications.service.impl;

import org.wso2.dpdp.accelerator.event.notifications.service.model.PaginatedResult;

import org.wso2.dpdp.accelerator.event.notifications.service.dao.TopicDAO;
import org.wso2.dpdp.accelerator.event.notifications.service.dao.impl.TopicDAOImpl;
import org.wso2.dpdp.accelerator.event.notifications.service.dao.model.Topic;


import org.wso2.dpdp.accelerator.event.notifications.service.TopicService;
import org.wso2.dpdp.accelerator.event.notifications.service.dto.TopicDTO;
import org.wso2.dpdp.accelerator.event.notifications.service.enums.TopicStatus;
import org.wso2.dpdp.accelerator.event.notifications.service.exception.ENFException;

import org.osgi.service.component.annotations.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component(service = TopicService.class, immediate = true)
public class TopicServiceImpl implements TopicService {

    private final TopicDAO topicDAO;

    public TopicServiceImpl() {
        this(new TopicDAOImpl());
    }

    public TopicServiceImpl(TopicDAO topicDAO) {
        this.topicDAO = topicDAO;
    }

    @Override
    public TopicDTO createTopic(String orgId, String name, String description) {
        if (orgId == null || orgId.trim().isEmpty() || name == null || name.trim().isEmpty()) {
            throw new ENFException("CS-4001", "Malformed request", "Org ID and topic name are required.", 400);
        }

        Optional<Topic> existing = topicDAO.getTopicByOrgAndName(orgId.trim(), name.trim());
        if (existing.isPresent()) {
            Topic existingTopic = existing.get();
            if (!TopicStatus.DEREGISTERED.getValue().equalsIgnoreCase(existingTopic.getStatus())) {
                throw new ENFException("CS-4090", "Topic already exists",
                        "A topic with name '" + name.trim() + "' already exists for this organization.", 409);
            }
            if (!topicDAO.updateTopicStatus(existingTopic.getTopicId(), TopicStatus.ACTIVE.getValue())) {
                throw new ENFException("CS-5000", "Internal error", "Failed to reactivate topic.", 500);
            }
            return new TopicDTO(existingTopic.getTopicId(), existingTopic.getName(), existingTopic.getDescription(),
                    TopicStatus.ACTIVE.getValue());
        }

        String topicId = UUID.randomUUID().toString();
        Topic topic = new Topic(topicId, orgId, name.trim(), description != null ? description.trim() : null,
                TopicStatus.ACTIVE.getValue());
        boolean created = topicDAO.addTopic(topic);
        if (!created) {
            throw new ENFException("CS-5000", "Internal error", "Failed to create topic.", 500);
        }

        return new TopicDTO(topicId, topic.getName(), topic.getDescription(), TopicStatus.ACTIVE.getValue());
    }

    @Override
    public PaginatedResult<TopicDTO> listTopics(String orgId, String status, String search, int limit, int offset,
            String sort) {
        PaginatedResult<Topic> daoResult = topicDAO.listTopics(orgId, status, search, limit, offset, sort);
        List<TopicDTO> dtoList = new ArrayList<>();
        for (Topic t : daoResult.getItems()) {
            dtoList.add(new TopicDTO(t.getTopicId(), t.getName(), t.getDescription(), t.getStatus()));
        }
        return new PaginatedResult<>(dtoList, daoResult.getTotal());
    }

    @Override
    public TopicDTO deleteTopic(String orgId, String topicIdStr) {
        if (orgId == null || orgId.trim().isEmpty()) {
            throw new ENFException("CS-4001", "Malformed request", "Organization ID is required.", 400);
        }
        if (topicIdStr == null || topicIdStr.trim().isEmpty()) {
            throw new ENFException("CS-4040", "Resource not found", "Topic ID not found.", 404);
        }
        String topicId = topicIdStr.trim();

        Optional<Topic> topicOpt = topicDAO.getTopicById(topicId);
        if (topicOpt.isEmpty()
                || !topicOpt.get().getOrgId().equalsIgnoreCase(orgId.trim())
                || TopicStatus.DEREGISTERED.getValue().equalsIgnoreCase(topicOpt.get().getStatus())) {
            throw new ENFException("CS-4040", "Resource not found",
                    "No topic exists with this ID for the given org.", 404);
        }

        // Check if active subscriptions reference it
        if (topicDAO.hasActiveSubscriptions(topicId)) {
            throw new ENFException("CS-4091", "Topic has active subscriptions",
                    "This topic cannot be deregistered while active subscriptions reference it.", 409);
        }

        boolean updated = topicDAO.updateTopicStatus(topicId, TopicStatus.DEREGISTERED.getValue());
        if (!updated) {
            throw new ENFException("CS-5000", "Internal error", "Failed to update topic status.", 500);
        }

        Topic topic = topicOpt.get();
        return new TopicDTO(topic.getTopicId(), topic.getName(), topic.getDescription(),
                TopicStatus.DEREGISTERED.getValue());
    }
}
