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

import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.wso2.dpdp.accelerator.event.notifications.dao.PaginatedDAOResult;
import org.wso2.dpdp.accelerator.event.notifications.dao.TopicDAO;
import org.wso2.dpdp.accelerator.event.notifications.dao.model.Topic;
import org.wso2.dpdp.accelerator.event.notifications.service.TopicService;
import org.wso2.dpdp.accelerator.event.notifications.service.constants.EventNotificationServiceConstants;
import org.wso2.dpdp.accelerator.event.notifications.service.dto.TopicDTO;
import org.wso2.dpdp.accelerator.event.notifications.common.constants.EventNotificationCommonConstants;
import org.wso2.dpdp.accelerator.event.notifications.common.enums.Initiator;
import org.wso2.dpdp.accelerator.event.notifications.common.enums.TopicStatus;
import org.wso2.dpdp.accelerator.event.notifications.common.exception.EventNotificationDuplicateResourceException;
import org.wso2.dpdp.accelerator.event.notifications.service.exception.EventNotificationException;
import org.wso2.dpdp.accelerator.event.notifications.service.model.PaginatedResult;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component(service = TopicService.class, immediate = true)
public class TopicServiceImpl implements TopicService {

    @Reference
    private TopicDAO topicDAO;

    public TopicServiceImpl() {
    }

    public TopicServiceImpl(TopicDAO topicDAO) {
        this.topicDAO = topicDAO;
    }

    @Override
    public TopicDTO createTopic(String orgId, String name, String description) {
        if (orgId == null || orgId.trim().isEmpty() || name == null || name.trim().isEmpty()) {
            throw new EventNotificationException(
                    EventNotificationServiceConstants.ERROR_CODE_INVALID_REQUEST,
                    EventNotificationServiceConstants.ERROR_TITLE_MALFORMED_REQUEST,
                    EventNotificationServiceConstants.ORG_ID_OR_TOPIC_NAME_MISSING_ERROR_MSG,
                    400);
        }

        Optional<Topic> existing = topicDAO.getTopicByOrgAndName(orgId.trim(), name.trim());
        if (existing.isPresent()) {
            Topic existingTopic = existing.get();
            if (!TopicStatus.DEREGISTERED.getValue().equalsIgnoreCase(existingTopic.getStatus())) {
                throw new EventNotificationException(
                        EventNotificationServiceConstants.ERROR_CODE_RESOURCE_EXISTS,
                        EventNotificationServiceConstants.ERROR_TITLE_TOPIC_ALREADY_EXISTS,
                        EventNotificationServiceConstants.TOPIC_ALREADY_EXISTS_ERROR_MSG,
                        409);
            }
            if (!topicDAO.updateTopicStatus(existingTopic.getTopicId(), orgId.trim(), TopicStatus.ACTIVE.getValue())) {
                throw new EventNotificationException(
                        EventNotificationServiceConstants.ERROR_CODE_INTERNAL_ERROR,
                        EventNotificationServiceConstants.ERROR_TITLE_INTERNAL_ERROR,
                        EventNotificationServiceConstants.FAILED_TO_REACTIVATE_TOPIC_ERROR_MSG,
                        500);
            }
            return new TopicDTO(existingTopic.getTopicId(), existingTopic.getName(), existingTopic.getDescription(),
                    TopicStatus.ACTIVE.getValue(), existingTopic.getInitiatedBy());
        }

        String topicId = UUID.randomUUID().toString();
        Topic topic = new Topic(topicId, orgId.trim(), name.trim(), description != null ? description.trim() : null,
                TopicStatus.ACTIVE.getValue());
        try {
            boolean created = topicDAO.addTopic(topic);
            if (!created) {
                throw new EventNotificationException(
                        EventNotificationServiceConstants.ERROR_CODE_INTERNAL_ERROR,
                        EventNotificationServiceConstants.ERROR_TITLE_INTERNAL_ERROR,
                        EventNotificationServiceConstants.FAILED_TO_CREATE_TOPIC_ERROR_MSG,
                        500);
            }
        } catch (EventNotificationDuplicateResourceException e) {
            throw new EventNotificationException(
                    EventNotificationServiceConstants.ERROR_CODE_RESOURCE_EXISTS,
                    EventNotificationServiceConstants.ERROR_TITLE_TOPIC_ALREADY_EXISTS,
                    EventNotificationServiceConstants.TOPIC_ALREADY_EXISTS_ERROR_MSG,
                    409);
        }

        return new TopicDTO(topicId, topic.getName(), topic.getDescription(), TopicStatus.ACTIVE.getValue(),
                Initiator.USER.getValue());
    }

    @Override
    public PaginatedResult<TopicDTO> listTopics(String orgId, String status, String search, int limit, int offset,
            String sort) {
        if (orgId == null || orgId.trim().isEmpty()) {
            throw new EventNotificationException(
                    EventNotificationServiceConstants.ERROR_CODE_INVALID_REQUEST,
                    EventNotificationServiceConstants.ERROR_TITLE_MALFORMED_REQUEST,
                    EventNotificationServiceConstants.ORG_ID_MISSING_ERROR_MSG,
                    400);
        }
        int lim = (limit <= 0) ? EventNotificationCommonConstants.DEFAULT_LIMIT
                : Math.min(limit, EventNotificationCommonConstants.MAX_LIMIT);
        int off = (offset < 0) ? 0 : offset;
        PaginatedDAOResult<Topic> daoResult = topicDAO.listTopics(orgId.trim(), status, search, lim, off, sort);
        List<TopicDTO> dtoList = new ArrayList<>();
        for (Topic t : daoResult.getItems()) {
            dtoList.add(
                    new TopicDTO(t.getTopicId(), t.getName(), t.getDescription(), t.getStatus(), t.getInitiatedBy()));
        }
        return new PaginatedResult<>(dtoList, daoResult.getTotal());
    }

    @Override
    public TopicDTO deleteTopic(String orgId, String topicIdStr) {
        if (orgId == null || orgId.trim().isEmpty()) {
            throw new EventNotificationException(
                    EventNotificationServiceConstants.ERROR_CODE_INVALID_REQUEST,
                    EventNotificationServiceConstants.ERROR_TITLE_MALFORMED_REQUEST,
                    EventNotificationServiceConstants.ORG_ID_MISSING_ERROR_MSG,
                    400);
        }
        if (topicIdStr == null || topicIdStr.trim().isEmpty()) {
            throw new EventNotificationException(
                    EventNotificationServiceConstants.ERROR_CODE_INVALID_REQUEST,
                    EventNotificationServiceConstants.ERROR_TITLE_MALFORMED_REQUEST,
                    EventNotificationServiceConstants.TOPIC_ID_MISSING_ERROR_MSG,
                    400);
        }

        Optional<Topic> topicOpt = topicDAO.getTopicById(topicIdStr.trim(), orgId.trim());
        if (topicOpt.isEmpty() || !orgId.trim().equalsIgnoreCase(topicOpt.get().getOrgId())) {
            throw new EventNotificationException(
                    EventNotificationServiceConstants.ERROR_CODE_TOPIC_NOT_FOUND,
                    EventNotificationServiceConstants.ERROR_TITLE_TOPIC_NOT_FOUND,
                    String.format(EventNotificationServiceConstants.TOPIC_NOT_FOUND_ERROR_MSG, topicIdStr.trim()),
                    404);
        }

        Topic topic = topicOpt.get();
        if (Initiator.SYSTEM.getValue().equalsIgnoreCase(topic.getInitiatedBy())) {
            throw new EventNotificationException(
                    EventNotificationServiceConstants.ERROR_CODE_INVALID_REQUEST,
                    EventNotificationServiceConstants.ERROR_TITLE_OPERATION_FORBIDDEN,
                    String.format(EventNotificationServiceConstants.SYSTEM_TOPIC_DELETE_FORBIDDEN_ERROR_MSG, topic.getName()),
                    409);
        }

        if (TopicStatus.DEREGISTERED.getValue().equalsIgnoreCase(topic.getStatus())) {
            throw new EventNotificationException(
                    EventNotificationServiceConstants.ERROR_CODE_TOPIC_NOT_FOUND,
                    EventNotificationServiceConstants.ERROR_TITLE_TOPIC_NOT_FOUND,
                    String.format(EventNotificationServiceConstants.TOPIC_ALREADY_DEREGISTERED_ERROR_MSG, topicIdStr.trim()),
                    404);
        }

        boolean updated;
        try {
            updated = topicDAO.deregisterTopicAtomic(topic.getTopicId(), orgId.trim());
        } catch (EventNotificationDuplicateResourceException e) {
            throw new EventNotificationException(
                    EventNotificationServiceConstants.ERROR_CODE_RESOURCE_EXISTS,
                    EventNotificationServiceConstants.ERROR_TITLE_RESOURCE_EXISTS,
                    String.format(EventNotificationServiceConstants.TOPIC_HAS_ACTIVE_SUBSCRIPTIONS_ERROR_MSG,
                            topic.getName()),
                    409);
        }
        if (!updated) {
            throw new EventNotificationException(
                    EventNotificationServiceConstants.ERROR_CODE_INTERNAL_ERROR,
                    EventNotificationServiceConstants.ERROR_TITLE_INTERNAL_ERROR,
                    EventNotificationServiceConstants.FAILED_TO_DEREGISTER_TOPIC_ERROR_MSG,
                    500);
        }

        return new TopicDTO(topic.getTopicId(), topic.getName(), topic.getDescription(),
                TopicStatus.DEREGISTERED.getValue(), topic.getInitiatedBy());
    }

    public Optional<TopicDTO> getTopic(String orgId, String topicIdStr) {
        if (orgId == null || orgId.trim().isEmpty() || topicIdStr == null || topicIdStr.trim().isEmpty()) {
            return Optional.empty();
        }
        Optional<Topic> topicOpt = topicDAO.getTopicById(topicIdStr.trim(), orgId.trim());
        if (topicOpt.isPresent() && orgId.trim().equalsIgnoreCase(topicOpt.get().getOrgId())) {
            Topic t = topicOpt.get();
            return Optional.of(
                    new TopicDTO(t.getTopicId(), t.getName(), t.getDescription(), t.getStatus(), t.getInitiatedBy()));
        }
        return Optional.empty();
    }
}
