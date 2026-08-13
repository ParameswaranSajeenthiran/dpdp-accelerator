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

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.wso2.dpdp.accelerator.event.notifications.common.constants.EventNotificationCommonConstants;
import org.wso2.dpdp.accelerator.event.notifications.common.enums.TopicStatus;
import org.wso2.dpdp.accelerator.event.notifications.common.util.DBUtils;
import org.wso2.dpdp.accelerator.event.notifications.dao.EventDAO;
import org.wso2.dpdp.accelerator.event.notifications.dao.PaginatedDAOResult;
import org.wso2.dpdp.accelerator.event.notifications.dao.TopicDAO;
import org.wso2.dpdp.accelerator.event.notifications.dao.model.Event;
import org.wso2.dpdp.accelerator.event.notifications.dao.model.Topic;
import org.wso2.dpdp.accelerator.event.notifications.service.EventFanOutService;
import org.wso2.dpdp.accelerator.event.notifications.service.EventPublishService;
import org.wso2.dpdp.accelerator.event.notifications.service.constants.EventNotificationServiceConstants;
import org.wso2.dpdp.accelerator.event.notifications.service.dto.EventDTO;
import org.wso2.dpdp.accelerator.event.notifications.service.exception.EventNotificationException;
import org.wso2.dpdp.accelerator.event.notifications.service.model.PaginatedResult;

import java.sql.Connection;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Default {@link EventPublishService} implementation.
 *
 * <p>
 * Synchronously persists the {@code EVENT} row plus its purpose tags and
 * then hands off to {@link EventFanOutService} so the API caller receives a
 * {@code 201 Created} only after delivery rows have been queued for every
 * active matching subscription. The actual outbound HTTP dispatch happens
 * asynchronously via the existing
 * {@code WebhookDeliveryWorker}.
 * </p>
 */
@Component(service = EventPublishService.class, immediate = true)
public class EventPublishServiceImpl implements EventPublishService {

    private static final Logger LOG = Logger.getLogger(EventPublishServiceImpl.class.getName());

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Reference
    private EventDAO eventDAO;

    @Reference
    private TopicDAO topicDAO;

    @Reference
    private EventFanOutService eventFanOutService;

    public EventPublishServiceImpl() {
    }

    public EventPublishServiceImpl(EventDAO eventDAO, TopicDAO topicDAO, EventFanOutService eventFanOutService) {
        this.eventDAO = eventDAO;
        this.topicDAO = topicDAO;
        this.eventFanOutService = eventFanOutService;
    }

    @Override
    public EventDTO publishEvent(String orgId, String groupId, String topicName, List<String> purposes,
            Map<String, Object> payload) {
        if (orgId == null || orgId.trim().isEmpty()) {
            throw new EventNotificationException(
                    EventNotificationServiceConstants.ERROR_CODE_INVALID_REQUEST,
                    EventNotificationServiceConstants.ERROR_TITLE_MALFORMED_REQUEST,
                    EventNotificationServiceConstants.ORG_ID_MISSING_ERROR_MSG,
                    400);
        }
        if (groupId == null || groupId.trim().isEmpty()) {
            throw new EventNotificationException(
                    EventNotificationServiceConstants.ERROR_CODE_INVALID_REQUEST,
                    EventNotificationServiceConstants.ERROR_TITLE_MALFORMED_REQUEST,
                    EventNotificationServiceConstants.GROUP_ID_MISSING_ERROR_MSG,
                    400);
        }
        if (topicName == null || topicName.trim().isEmpty()) {
            throw new EventNotificationException(
                    EventNotificationServiceConstants.ERROR_CODE_INVALID_REQUEST,
                    EventNotificationServiceConstants.ERROR_TITLE_MALFORMED_REQUEST,
                    EventNotificationServiceConstants.ORG_ID_OR_TOPIC_NAME_MISSING_ERROR_MSG,
                    400);
        }

        String payloadJson;
        try {
            payloadJson = payload == null ? "{}" : objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            LOG.log(Level.SEVERE, "Failed to serialize event payload: " + e.getMessage(), e);
            throw new EventNotificationException(
                    EventNotificationServiceConstants.ERROR_CODE_EVENT_PUBLISH_FAILED,
                    EventNotificationServiceConstants.ERROR_TITLE_EVENT_PUBLISH_FAILED,
                    EventNotificationServiceConstants.EVENT_PUBLISH_FAILED_ERROR_MSG,
                    500);
        }

        String eventId = UUID.randomUUID().toString();
        Timestamp now = new Timestamp(System.currentTimeMillis());

        Connection conn = null;
        try {
            conn = DBUtils.getConnection();
        } catch (Exception e) {
            LOG.log(Level.FINE, "Could not acquire connection from DBUtils: " + e.getMessage());
        }

        if (conn != null) {
            boolean originalAutoCommit = true;
            try {
                originalAutoCommit = conn.getAutoCommit();
                conn.setAutoCommit(false);
                Topic topic = resolveActiveTopic(conn, orgId, topicName);
                Event event = new Event(eventId, orgId.trim(), groupId.trim(), topic.getTopicId(), payloadJson, now);

                eventDAO.addEvent(conn, event);
                if (purposes != null && !purposes.isEmpty()) {
                    eventDAO.addEventPurposes(conn, eventId, purposes);
                }
                eventFanOutService.fanOutEvent(conn, event, purposes);

                conn.commit();
                return new EventDTO(eventId, orgId, event.getGroupId(), topic.getTopicId(), payloadJson, purposes, now,
                        now);
            } catch (EventNotificationException e) {
                try {
                    conn.rollback();
                } catch (Exception ignored) {
                }
                throw e;
            } catch (Exception e) {
                try {
                    conn.rollback();
                } catch (Exception ignored) {
                }
                LOG.log(Level.SEVERE, "Failed to publish event [" + eventId + "]: " + e.getMessage(), e);
                throw new EventNotificationException(
                        EventNotificationServiceConstants.ERROR_CODE_EVENT_PUBLISH_FAILED,
                        EventNotificationServiceConstants.ERROR_TITLE_EVENT_PUBLISH_FAILED,
                        EventNotificationServiceConstants.EVENT_PUBLISH_FAILED_ERROR_MSG,
                        500);
            } finally {
                try {
                    conn.setAutoCommit(originalAutoCommit);
                } catch (Exception ignored) {
                } finally {
                    try {
                        conn.close();
                    } catch (Exception ignored) {
                    }
                }
            }
        } else {
            // Fallback for mocked unit tests or environments without direct JDBC pool
            try {
                Topic topic = resolveActiveTopic(orgId, topicName);
                Event event = new Event(eventId, orgId.trim(), groupId.trim(), topic.getTopicId(), payloadJson, now);
                eventDAO.addEvent(event);
                if (purposes != null && !purposes.isEmpty()) {
                    eventDAO.addEventPurposes(eventId, purposes);
                }
                eventFanOutService.fanOutEvent(event, purposes);
                return new EventDTO(eventId, orgId, event.getGroupId(), topic.getTopicId(), payloadJson, purposes, now,
                        now);
            } catch (EventNotificationException e) {
                throw e;
            } catch (Exception e) {
                LOG.log(Level.SEVERE, "Failed to publish event [" + eventId + "]: " + e.getMessage(), e);
                throw new EventNotificationException(
                        EventNotificationServiceConstants.ERROR_CODE_EVENT_PUBLISH_FAILED,
                        EventNotificationServiceConstants.ERROR_TITLE_EVENT_PUBLISH_FAILED,
                        EventNotificationServiceConstants.EVENT_PUBLISH_FAILED_ERROR_MSG,
                        500);
            }
        }
    }

    private Topic resolveActiveTopic(String orgId, String topicName) {
        return resolveActiveTopic(null, orgId, topicName);
    }

    private Topic resolveActiveTopic(java.sql.Connection conn, String orgId, String topicName) {
        Optional<Topic> existing = (conn != null)
                ? topicDAO.getTopicByOrgAndName(conn, orgId.trim(), topicName.trim())
                : topicDAO.getTopicByOrgAndName(orgId.trim(), topicName.trim());
        if (!existing.isPresent()) {
            throw new EventNotificationException(
                    EventNotificationServiceConstants.ERROR_CODE_TOPIC_NOT_FOUND,
                    EventNotificationServiceConstants.ERROR_TITLE_TOPIC_NOT_FOUND,
                    String.format(EventNotificationServiceConstants.EVENT_TOPIC_NOT_FOUND_ERROR_MSG, topicName),
                    404);
        }
        Topic topic = existing.get();
        if (!TopicStatus.ACTIVE.getValue().equalsIgnoreCase(topic.getStatus())) {
            throw new EventNotificationException(
                    EventNotificationServiceConstants.ERROR_CODE_INVALID_REQUEST,
                    EventNotificationServiceConstants.ERROR_TITLE_INVALID_STATE,
                    String.format(EventNotificationServiceConstants.TOPIC_NOT_ACTIVE_ERROR_MSG, topic.getName()),
                    400);
        }
        return topic;
    }

    @Override
    public PaginatedResult<EventDTO> searchEvents(String orgId, String search, int limit, int offset) {
        if (orgId == null || orgId.trim().isEmpty()) {
            throw new EventNotificationException(EventNotificationServiceConstants.ERROR_CODE_INVALID_REQUEST,
                    EventNotificationServiceConstants.ERROR_TITLE_MALFORMED_REQUEST,
                    EventNotificationServiceConstants.ORG_ID_MISSING_ERROR_MSG, 400);
        }
        int lim = (limit <= 0) ? EventNotificationCommonConstants.DEFAULT_LIMIT
                : Math.min(limit, EventNotificationCommonConstants.MAX_LIMIT);
        int off = (offset < 0) ? 0 : offset;
        PaginatedDAOResult<Event> daoResult = eventDAO.searchEvents(orgId.trim(), search, lim, off);
        List<EventDTO> dtoList = new ArrayList<>();
        for (Event event : daoResult.getItems()) {
            dtoList.add(mapToDTO(event));
        }
        return new PaginatedResult<>(dtoList, daoResult.getTotal());
    }

    private EventDTO mapToDTO(Event event) {
        if (event == null) {
            return null;
        }
        return new EventDTO(
                event.getEventId(),
                event.getOrgId(),
                event.getGroupId(),
                event.getTopicId(),
                event.getPayload(),
                event.getPurposes(),
                // The DAO model only stores CREATED_AT; map it to both occurredAt and createdAt
                // for API symmetry with the publishEvent response. Consumers that need a
                // distinct
                // occurred-at timestamp can populate it on EVENT_PURPOSE or on a future schema
                // column.
                event.getCreatedAt(),
                event.getCreatedAt());
    }
}
