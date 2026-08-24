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
import org.wso2.dpdp.accelerator.event.notifications.common.enums.DeliveryMode;
import org.wso2.dpdp.accelerator.event.notifications.common.enums.DeliveryStatus;
import org.wso2.dpdp.accelerator.event.notifications.common.enums.SubscriptionStatus;
import org.wso2.dpdp.accelerator.event.notifications.common.enums.TopicStatus;
import org.wso2.dpdp.accelerator.common.persistence.JDBCPersistenceManager;
import org.wso2.dpdp.accelerator.common.persistence.TransactionManager;
import org.wso2.dpdp.accelerator.common.util.LogSanitizer;
import org.wso2.dpdp.accelerator.event.notifications.dao.DeliveryAckDAO;
import org.wso2.dpdp.accelerator.event.notifications.dao.DeliveryDAO;
import org.wso2.dpdp.accelerator.event.notifications.dao.EventDAO;
import org.wso2.dpdp.accelerator.event.notifications.dao.PaginatedDAOResult;
import org.wso2.dpdp.accelerator.event.notifications.dao.TopicDAO;
import org.wso2.dpdp.accelerator.event.notifications.dao.model.Event;
import org.wso2.dpdp.accelerator.event.notifications.dao.model.SubscriptionDeliverySummary;
import org.wso2.dpdp.accelerator.event.notifications.dao.model.Topic;
import org.wso2.dpdp.accelerator.event.notifications.dao.model.WebhookDelivery;
import org.wso2.dpdp.accelerator.event.notifications.dao.model.WebhookDeliveryAck;
import org.wso2.dpdp.accelerator.event.notifications.dao.model.WebhookDeliveryAudit;
import org.wso2.dpdp.accelerator.event.notifications.service.EventFanOutService;
import org.wso2.dpdp.accelerator.event.notifications.service.EventPublishService;
import org.wso2.dpdp.accelerator.event.notifications.service.constants.EventNotificationServiceConstants;
import org.wso2.dpdp.accelerator.event.notifications.service.dto.EventDTO;
import org.wso2.dpdp.accelerator.event.notifications.service.dto.SubscriptionDeliveryAttemptDTO;
import org.wso2.dpdp.accelerator.event.notifications.service.dto.SubscriptionDeliveryDTO;
import org.wso2.dpdp.accelerator.event.notifications.service.dto.SubscriptionEventHistoryDTO;
import org.wso2.dpdp.accelerator.event.notifications.service.exception.EventNotificationException;
import org.wso2.dpdp.accelerator.event.notifications.service.model.PaginatedResult;

import java.sql.Connection;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

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

    private static final Log LOG = LogFactory.getLog(EventPublishServiceImpl.class);

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final TransactionManager transactionManager;

    @Reference
    private EventDAO eventDAO;

    @Reference
    private TopicDAO topicDAO;

    @Reference
    private EventFanOutService eventFanOutService;

    @Reference
    private DeliveryDAO deliveryDAO;

    @Reference
    private DeliveryAckDAO deliveryAckDAO;

    public EventPublishServiceImpl() {
        this.transactionManager = JDBCPersistenceManager.getInstance();
    }

    public EventPublishServiceImpl(EventDAO eventDAO, TopicDAO topicDAO, EventFanOutService eventFanOutService) {
        this(eventDAO, topicDAO, eventFanOutService, JDBCPersistenceManager.getInstance());
    }

    public EventPublishServiceImpl(EventDAO eventDAO, TopicDAO topicDAO, EventFanOutService eventFanOutService,
            TransactionManager transactionManager) {
        this.eventDAO = eventDAO;
        this.topicDAO = topicDAO;
        this.eventFanOutService = eventFanOutService;
        this.transactionManager = transactionManager;
    }

    public EventPublishServiceImpl(EventDAO eventDAO, TopicDAO topicDAO, EventFanOutService eventFanOutService,
            DeliveryDAO deliveryDAO, DeliveryAckDAO deliveryAckDAO) {
        this(eventDAO, topicDAO, eventFanOutService, deliveryDAO, deliveryAckDAO,
                JDBCPersistenceManager.getInstance());
    }

    public EventPublishServiceImpl(EventDAO eventDAO, TopicDAO topicDAO, EventFanOutService eventFanOutService,
            DeliveryDAO deliveryDAO, DeliveryAckDAO deliveryAckDAO, TransactionManager transactionManager) {
        this.eventDAO = eventDAO;
        this.topicDAO = topicDAO;
        this.eventFanOutService = eventFanOutService;
        this.deliveryDAO = deliveryDAO;
        this.deliveryAckDAO = deliveryAckDAO;
        this.transactionManager = transactionManager;
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

        if (payload == null) {
            throw new EventNotificationException(
                    EventNotificationServiceConstants.ERROR_CODE_MISSING_REQUIRED_PARAM,
                    EventNotificationServiceConstants.ERROR_TITLE_VALIDATION_FAILED,
                    EventNotificationServiceConstants.EVENT_PAYLOAD_REQUIRED_ERROR_MSG,
                    422);
        }

        String payloadJson;
        try {
            payloadJson = objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            LOG.error("Failed to serialize event payload: " + LogSanitizer.sanitize(e.getMessage()), e);
            throw new EventNotificationException(
                    EventNotificationServiceConstants.ERROR_CODE_EVENT_PUBLISH_FAILED,
                    EventNotificationServiceConstants.ERROR_TITLE_EVENT_PUBLISH_FAILED,
                    EventNotificationServiceConstants.EVENT_PUBLISH_FAILED_ERROR_MSG,
                    500);
        }

        String eventId = UUID.randomUUID().toString();
        Timestamp now = new Timestamp(System.currentTimeMillis());

        try {
            return transactionManager.executeInTransaction(conn -> {
                Topic topic = resolveActiveTopic(conn, orgId, topicName);
                Event event = new Event(eventId, orgId.trim(), groupId.trim(), topic.getTopicId(), payloadJson, now);

                eventDAO.addEvent(conn, event);
                if (purposes != null && !purposes.isEmpty()) {
                    eventDAO.addEventPurposes(conn, eventId, purposes);
                }
                eventFanOutService.fanOutEvent(conn, event, purposes);

                return new EventDTO(eventId, orgId, event.getGroupId(), topic.getTopicId(), payloadJson, purposes, now,
                        now);
            });
        } catch (EventNotificationException e) {
            throw e;
        } catch (Exception e) {
            LOG.error("Failed to publish event [" + LogSanitizer.sanitize(eventId) + "]: "
                    + LogSanitizer.sanitize(e.getMessage()), e);
            throw new EventNotificationException(
                    EventNotificationServiceConstants.ERROR_CODE_EVENT_PUBLISH_FAILED,
                    EventNotificationServiceConstants.ERROR_TITLE_EVENT_PUBLISH_FAILED,
                    EventNotificationServiceConstants.EVENT_PUBLISH_FAILED_ERROR_MSG,
                    500);
        }
    }

    private Topic resolveActiveTopic(Connection conn, String orgId, String topicName) {
        Optional<Topic> existing = topicDAO.getTopicByOrgAndName(conn, orgId.trim(), topicName.trim());
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
        return searchEvents(orgId, null, null, null, null, null, search, limit, offset);
    }

    public PaginatedResult<EventDTO> searchEvents(String orgId, String topic, String status, String groupId,
            String purposes, String search, int limit, int offset) {
        return searchEvents(orgId, topic, status, groupId, null, purposes, search, limit, offset);
    }

    @Override
    public PaginatedResult<EventDTO> searchEvents(String orgId, String topic, String status, String groupId,
            String subscriptionId, String purposes, String search, int limit, int offset) {
        if (orgId == null || orgId.trim().isEmpty()) {
            throw new EventNotificationException(EventNotificationServiceConstants.ERROR_CODE_INVALID_REQUEST,
                    EventNotificationServiceConstants.ERROR_TITLE_MALFORMED_REQUEST,
                    EventNotificationServiceConstants.ORG_ID_MISSING_ERROR_MSG, 400);
        }
        int lim = (limit <= 0) ? EventNotificationCommonConstants.DEFAULT_LIMIT
                : Math.min(limit, EventNotificationCommonConstants.MAX_LIMIT);
        int off = (offset < 0) ? 0 : offset;
        PaginatedDAOResult<Event> daoResult = subscriptionId == null || subscriptionId.trim().isEmpty()
                ? eventDAO.searchEvents(orgId.trim(), topic, status, groupId, purposes, search, lim, off)
                : eventDAO.searchEvents(orgId.trim(), topic, status, groupId, subscriptionId,
                        purposes, search, lim, off);
        List<EventDTO> dtoList = new ArrayList<>();
        for (Event event : daoResult.getItems()) {
            dtoList.add(mapToDTO(event));
        }
        return new PaginatedResult<>(dtoList, daoResult.getTotal());
    }

    @Override
    public PaginatedResult<SubscriptionDeliveryDTO> listOrgDeliveries(String orgId, String status,
            String subscriptionId, String groupId, String purposes, String search, int limit, int offset) {
        if (orgId == null || orgId.trim().isEmpty()) {
            throw new EventNotificationException(EventNotificationServiceConstants.ERROR_CODE_INVALID_REQUEST,
                    EventNotificationServiceConstants.ERROR_TITLE_MALFORMED_REQUEST,
                    EventNotificationServiceConstants.ORG_ID_MISSING_ERROR_MSG, 400);
        }
        int lim = (limit <= 0) ? EventNotificationCommonConstants.DEFAULT_LIMIT
                : Math.min(limit, EventNotificationCommonConstants.MAX_LIMIT);
        int off = (offset < 0) ? 0 : offset;
        int[] totalOut = new int[1];

        List<SubscriptionDeliverySummary> summaries = deliveryDAO.listOrgDeliveries(
                orgId.trim(), status, subscriptionId, groupId, purposes, search, lim, off, totalOut);

        List<SubscriptionDeliveryDTO> dtoList = new ArrayList<>();
        for (SubscriptionDeliverySummary summary : summaries) {
            dtoList.add(new SubscriptionDeliveryDTO(
                    summary.getDeliveryId(),
                    summary.getEventId(),
                    summary.getSubscriptionId(),
                    summary.getGroupId(),
                    summary.getTopicName(),
                    summary.getCurrentStatus() != null ? summary.getCurrentStatus()
                            : SubscriptionStatus.PENDING.getValue(),
                    summary.getDeliveryMode() != null ? summary.getDeliveryMode()
                            : DeliveryMode.WEBHOOK.getValue(),
                    summary.getOccurredAt() != null ? summary.getOccurredAt().getTime()
                            : (summary.getCreatedAt() != null ? summary.getCreatedAt().getTime()
                                    : System.currentTimeMillis())));
        }
        return new PaginatedResult<>(dtoList, totalOut[0]);
    }

    @Override
    public SubscriptionEventHistoryDTO getDeliveryHistory(String orgId, String deliveryId) {
        if (orgId == null || orgId.trim().isEmpty()) {
            throw new EventNotificationException(EventNotificationServiceConstants.ERROR_CODE_INVALID_REQUEST,
                    EventNotificationServiceConstants.ERROR_TITLE_MALFORMED_REQUEST,
                    EventNotificationServiceConstants.ORG_ID_MISSING_ERROR_MSG, 400);
        }
        if (deliveryId == null || deliveryId.trim().isEmpty()) {
            throw new EventNotificationException(EventNotificationServiceConstants.ERROR_CODE_INVALID_REQUEST,
                    EventNotificationServiceConstants.ERROR_TITLE_MALFORMED_REQUEST,
                    EventNotificationServiceConstants.DELIVERY_ID_MISSING_ERROR_MSG, 400);
        }

        Optional<SubscriptionDeliverySummary> summaryOpt = deliveryDAO.getOrgDeliveryById(orgId.trim(), deliveryId.trim());
        if (summaryOpt.isEmpty()) {
            throw new EventNotificationException(EventNotificationServiceConstants.ERROR_CODE_DELIVERY_NOT_FOUND,
                    EventNotificationServiceConstants.ERROR_TITLE_DELIVERY_NOT_FOUND,
                    EventNotificationServiceConstants.DELIVERY_NOT_FOUND_ERROR_MSG, 404);
        }

        SubscriptionDeliverySummary summary = summaryOpt.get();
        String mode = summary.getDeliveryMode() != null ? summary.getDeliveryMode()
                : DeliveryMode.WEBHOOK.getValue();

        SubscriptionEventHistoryDTO dto = new SubscriptionEventHistoryDTO();
        dto.setDeliveryId(summary.getDeliveryId());
        dto.setEventId(summary.getEventId());
        dto.setTopic(summary.getTopicName());
        dto.setDeliveryMode(mode);
        dto.setCurrentStatus(summary.getCurrentStatus() != null ? summary.getCurrentStatus()
                : SubscriptionStatus.PENDING.getValue());
        dto.setOccurredAt(summary.getOccurredAt() != null ? summary.getOccurredAt().getTime()
                : (summary.getCreatedAt() != null ? summary.getCreatedAt().getTime() : System.currentTimeMillis()));

        if (DeliveryMode.WEBHOOK.getValue().equals(mode)) {
            Optional<WebhookDelivery> whOpt = deliveryDAO.getWebhookDeliveryById(deliveryId.trim(), orgId.trim());
            if (whOpt.isPresent()) {
                WebhookDelivery wh = whOpt.get();
                if (wh.getNextRetryAt() != null) {
                    dto.setNextRetryAt(wh.getNextRetryAt().getTime());
                }
            }

            Optional<WebhookDeliveryAck> ackOpt = deliveryAckDAO.getDeliveryAckByDeliveryId(deliveryId.trim());
            if (ackOpt.isPresent()) {
                WebhookDeliveryAck ack = ackOpt.get();
                dto.setCompletionStatus(
                        ack.getCompletionStatus() != null ? ack.getCompletionStatus()
                                : DeliveryStatus.COMPLETED.getValue());
                dto.setCompletionEvidence(ack.getCompletionEvidence());
            }

            List<WebhookDeliveryAudit> audits = deliveryDAO.getWebhookDeliveryAudits(deliveryId.trim(), orgId.trim());
            List<SubscriptionDeliveryAttemptDTO> history = new ArrayList<>();
            int attemptNum = 1;
            for (WebhookDeliveryAudit audit : audits) {
                String auditStatus;
                Integer httpStatus = null;
                String respCode = audit.getResponseCode();
                if (respCode != null) {
                    try {
                        int code = Integer.parseInt(respCode);
                        httpStatus = code;
                        auditStatus = (code >= 200 && code < 300) ? DeliveryStatus.DELIVERED.getValue()
                                : DeliveryStatus.FAILED.getValue();
                    } catch (NumberFormatException e) {
                        auditStatus = DeliveryStatus.FAILED.getValue();
                    }
                } else {
                    auditStatus = DeliveryStatus.FAILED.getValue();
                }

                history.add(new SubscriptionDeliveryAttemptDTO(
                        attemptNum++,
                        auditStatus,
                        audit.getAttemptAt() != null ? audit.getAttemptAt().getTime()
                                : (audit.getCreatedAt() != null ? audit.getCreatedAt().getTime()
                                        : System.currentTimeMillis()),
                        httpStatus,
                        (httpStatus != null && httpStatus >= 200 && httpStatus < 300)
                                ? null : (httpStatus != null ? "HTTP " + httpStatus : respCode)));
            }
            dto.setHistory(history);
        } else {
            dto.setHistory(Collections.emptyList());
        }

        return dto;
    }

    @Override
    public EventDTO getEventById(String orgId, String eventId) {
        if (orgId == null || orgId.trim().isEmpty()) {
            throw new EventNotificationException(
                    EventNotificationServiceConstants.ERROR_CODE_INVALID_REQUEST,
                    EventNotificationServiceConstants.ERROR_TITLE_MALFORMED_REQUEST,
                    EventNotificationServiceConstants.ORG_ID_MISSING_ERROR_MSG,
                    400);
        }
        if (eventId == null || eventId.trim().isEmpty()) {
            throw new EventNotificationException(
                    EventNotificationServiceConstants.ERROR_CODE_INVALID_REQUEST,
                    EventNotificationServiceConstants.ERROR_TITLE_MALFORMED_REQUEST,
                    EventNotificationServiceConstants.EVENT_ID_MISSING_ERROR_MSG,
                    400);
        }
        Optional<Event> eventOpt = eventDAO.getEventById(eventId.trim(), orgId.trim());
        if (!eventOpt.isPresent()) {
            throw new EventNotificationException(
                    EventNotificationServiceConstants.ERROR_CODE_EVENT_NOT_FOUND,
                    EventNotificationServiceConstants.ERROR_TITLE_EVENT_NOT_FOUND,
                    String.format(EventNotificationServiceConstants.EVENT_NOT_FOUND_ERROR_MSG, eventId.trim()),
                    404);
        }
        Event event = eventOpt.get();
        EventDTO dto = mapToDTO(event);
        if (event.getTopicId() != null) {
            Optional<Topic> topicOpt = topicDAO.getTopicById(event.getTopicId(), orgId.trim());
            if (topicOpt.isPresent()) {
                dto.setTopic(topicOpt.get().getName());
            } else {
                dto.setTopic(event.getTopicId());
            }
        }
        int[] totalOut = new int[1];
        deliveryDAO.listEventDeliveries(orgId.trim(), eventId.trim(), 1, 0, totalOut);
        dto.setDeliveriesCount(totalOut[0]);
        return dto;
    }

    @Override
    public PaginatedResult<SubscriptionDeliveryDTO> getEventDeliveries(String orgId, String eventId, int limit, int offset) {
        if (orgId == null || orgId.trim().isEmpty()) {
            throw new EventNotificationException(
                    EventNotificationServiceConstants.ERROR_CODE_INVALID_REQUEST,
                    EventNotificationServiceConstants.ERROR_TITLE_MALFORMED_REQUEST,
                    EventNotificationServiceConstants.ORG_ID_MISSING_ERROR_MSG,
                    400);
        }
        if (eventId == null || eventId.trim().isEmpty()) {
            throw new EventNotificationException(
                    EventNotificationServiceConstants.ERROR_CODE_INVALID_REQUEST,
                    EventNotificationServiceConstants.ERROR_TITLE_MALFORMED_REQUEST,
                    EventNotificationServiceConstants.EVENT_ID_MISSING_ERROR_MSG,
                    400);
        }
        int safeLimit = Math.max(1, limit);
        int safeOffset = Math.max(0, offset);
        int[] totalOut = new int[1];
        List<SubscriptionDeliverySummary> summaries = deliveryDAO.listEventDeliveries(
                orgId.trim(), eventId.trim(), safeLimit, safeOffset, totalOut);

        List<SubscriptionDeliveryDTO> dtos = new ArrayList<>();
        for (SubscriptionDeliverySummary summary : summaries) {
            SubscriptionDeliveryDTO dto = new SubscriptionDeliveryDTO(
                    summary.getDeliveryId(),
                    summary.getEventId(),
                    summary.getSubscriptionId(),
                    summary.getGroupId(),
                    summary.getTopicName(),
                    summary.getCurrentStatus(),
                    summary.getDeliveryMode(),
                    summary.getOccurredAt() != null ? summary.getOccurredAt().getTime() : 0L);
            dtos.add(dto);
        }
        return new PaginatedResult<SubscriptionDeliveryDTO>(dtos, totalOut[0]);
    }

    private EventDTO mapToDTO(Event event) {
        if (event == null) {
            return null;
        }
        EventDTO dto = new EventDTO(
                event.getEventId(),
                event.getOrgId(),
                event.getGroupId(),
                event.getTopicId(),
                event.getPayload(),
                event.getPurposes(),
                event.getCreatedAt(),
                event.getCreatedAt());
        dto.setTopic(event.getTopic());
        dto.setDeliveriesCount(event.getDeliveriesCount());
        return dto;
    }
}
