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
import org.wso2.dpdp.accelerator.common.config.DPDPConfigurationService;
import org.wso2.dpdp.accelerator.common.util.DatabaseUtils;
import org.wso2.dpdp.accelerator.common.util.LogSanitizer;
import org.wso2.dpdp.accelerator.event.notifications.common.enums.DeliveryMode;
import org.wso2.dpdp.accelerator.event.notifications.common.enums.DeliveryStatus;
import org.wso2.dpdp.accelerator.event.notifications.common.enums.TopicStatus;
import org.wso2.dpdp.accelerator.event.notifications.common.exception.EventNotificationDuplicateResourceException;
import org.wso2.dpdp.accelerator.event.notifications.common.util.EventNotificationUrlValidator;
import org.wso2.dpdp.accelerator.event.notifications.dao.DeliveryAckDAO;
import org.wso2.dpdp.accelerator.event.notifications.dao.DeliveryDAO;
import org.wso2.dpdp.accelerator.event.notifications.dao.EventDAO;
import org.wso2.dpdp.accelerator.event.notifications.dao.SubscriptionDAO;
import org.wso2.dpdp.accelerator.event.notifications.dao.PaginatedDAOResult;
import org.wso2.dpdp.accelerator.event.notifications.dao.TopicDAO;
import org.wso2.dpdp.accelerator.event.notifications.dao.model.Event;
import org.wso2.dpdp.accelerator.event.notifications.dao.model.PollDelivery;
import org.wso2.dpdp.accelerator.event.notifications.dao.model.PollDeliveryError;
import org.wso2.dpdp.accelerator.event.notifications.dao.model.Subscription;
import org.wso2.dpdp.accelerator.event.notifications.dao.model.SubscriptionDeliverySummary;
import org.wso2.dpdp.accelerator.event.notifications.dao.model.Topic;
import org.wso2.dpdp.accelerator.event.notifications.service.EventFanOutService;
import org.wso2.dpdp.accelerator.event.notifications.service.EventPublishService;
import org.wso2.dpdp.accelerator.event.notifications.service.constants.EventNotificationServiceConstants;
import org.wso2.dpdp.accelerator.event.notifications.service.dto.EventDTO;
import org.wso2.dpdp.accelerator.event.notifications.service.dto.EventPollingRequestDTO;
import org.wso2.dpdp.accelerator.event.notifications.service.dto.EventPollingResponseDTO;
import org.wso2.dpdp.accelerator.event.notifications.service.dto.PollSetErrorDTO;
import org.wso2.dpdp.accelerator.event.notifications.service.dto.DeliveryCompletionRequestDTO;
import org.wso2.dpdp.accelerator.event.notifications.common.util.HmacSigner;
import org.wso2.dpdp.accelerator.event.notifications.service.dto.SubscriptionDeliveryDTO;
import org.wso2.dpdp.accelerator.event.notifications.service.dto.SubscriptionEventHistoryDTO;
import org.wso2.dpdp.accelerator.event.notifications.service.exception.EventNotificationException;
import org.wso2.dpdp.accelerator.event.notifications.service.dispatch.SignedEventPayloadFactory;
import org.wso2.dpdp.accelerator.event.notifications.service.model.PaginatedResult;
import org.wso2.dpdp.accelerator.event.notifications.service.util.EventNotificationParameterUtils;

import java.sql.Connection;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
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
public class EventPublishServiceImpl implements EventPublishService {

    private static final Log LOG = LogFactory.getLog(EventPublishServiceImpl.class);

    private final ObjectMapper objectMapper = new ObjectMapper();

    private EventDAO eventDAO;

    private TopicDAO topicDAO;

    private EventFanOutService eventFanOutService;

    private DeliveryDAO deliveryDAO;

    private DeliveryAckDAO deliveryAckDAO;
    private SubscriptionDAO subscriptionDAO;
    private DPDPConfigurationService configurationService;
    private SignedEventPayloadFactory signedEventPayloadFactory;

    public EventPublishServiceImpl() {
    }

    public EventPublishServiceImpl(EventDAO eventDAO, TopicDAO topicDAO, EventFanOutService eventFanOutService) {
        this.eventDAO = eventDAO;
        this.topicDAO = topicDAO;
        this.eventFanOutService = eventFanOutService;
    }

    public EventPublishServiceImpl(EventDAO eventDAO, TopicDAO topicDAO, EventFanOutService eventFanOutService,
            DeliveryDAO deliveryDAO, DeliveryAckDAO deliveryAckDAO) {
        this(eventDAO, topicDAO, eventFanOutService, deliveryDAO, deliveryAckDAO, null);
    }

    public EventPublishServiceImpl(EventDAO eventDAO, TopicDAO topicDAO, EventFanOutService eventFanOutService,
            DeliveryDAO deliveryDAO, DeliveryAckDAO deliveryAckDAO, SubscriptionDAO subscriptionDAO) {
        this(eventDAO, topicDAO, eventFanOutService, deliveryDAO, deliveryAckDAO, subscriptionDAO, null, null);
    }

    public EventPublishServiceImpl(EventDAO eventDAO, TopicDAO topicDAO, EventFanOutService eventFanOutService,
            DeliveryDAO deliveryDAO, DeliveryAckDAO deliveryAckDAO, SubscriptionDAO subscriptionDAO,
            DPDPConfigurationService configurationService, SignedEventPayloadFactory signedEventPayloadFactory) {
        this.eventDAO = eventDAO;
        this.topicDAO = topicDAO;
        this.eventFanOutService = eventFanOutService;
        this.deliveryDAO = deliveryDAO;
        this.deliveryAckDAO = deliveryAckDAO;
        this.subscriptionDAO = subscriptionDAO;
        this.configurationService = configurationService;
        this.signedEventPayloadFactory = signedEventPayloadFactory;
    }

    @Override
    public EventPollingResponseDTO pollEvents(String orgId, String groupId, String subscriptionId,
            String requestBody, String eventSignature) {
        String safeOrgId = requireValue(orgId, EventNotificationServiceConstants.ORG_ID_MISSING_ERROR_MSG);
        String safeGroupId = requireValue(groupId, EventNotificationServiceConstants.GROUP_ID_MISSING_ERROR_MSG);
        String safeSubscriptionId = requireValue(subscriptionId,
                EventNotificationServiceConstants.SUBSCRIPTION_ID_MISSING_ERROR_MSG);
        if (configurationService == null || signedEventPayloadFactory == null || subscriptionDAO == null) {
            throw new EventNotificationException(EventNotificationServiceConstants.ERROR_CODE_INTERNAL_ERROR,
                    EventNotificationServiceConstants.ERROR_TITLE_INTERNAL_ERROR,
                    "Polling services are not initialized.", 500);
        }

        Subscription subscription = subscriptionDAO.getSubscriptionById(safeSubscriptionId, safeOrgId)
                .filter(value -> safeOrgId.equalsIgnoreCase(value.getOrgId()))
                .filter(value -> safeGroupId.equalsIgnoreCase(value.getGroupId()))
                .filter(value -> DeliveryMode.POLL.getValue().equalsIgnoreCase(value.getDeliveryMode()))
                .filter(value -> "active".equalsIgnoreCase(value.getStatus()))
                .orElseThrow(() -> new EventNotificationException(
                        EventNotificationServiceConstants.ERROR_CODE_RESOURCE_NOT_FOUND,
                        EventNotificationServiceConstants.ERROR_TITLE_RESOURCE_NOT_FOUND,
                        EventNotificationServiceConstants.SUBSCRIPTION_NOT_FOUND_ERROR_MSG, 404));
        String sharedSecret = subscription.getSharedSecret();
        if (sharedSecret == null || sharedSecret.trim().isEmpty()) {
            throw new EventNotificationException(EventNotificationServiceConstants.ERROR_CODE_INVALID_STATE,
                    EventNotificationServiceConstants.ERROR_TITLE_INVALID_STATE,
                    "The polling subscription does not have a shared secret.", 409);
        }

        String rawBody = requestBody == null ? "" : requestBody;
        if (configurationService.isEventNotificationPollingRequestHmacValidationEnabled()
                && !HmacSigner.verify(sharedSecret, rawBody, eventSignature)) {
            throw new EventNotificationException(EventNotificationServiceConstants.ERROR_CODE_INVALID_SIGNATURE,
                    EventNotificationServiceConstants.ERROR_TITLE_OPERATION_FORBIDDEN,
                    EventNotificationServiceConstants.INVALID_SIGNATURE_ERROR_MSG, 401);
        }

        EventPollingRequestDTO request;
        try {
            String bodyToParse = rawBody.trim().isEmpty() ? "{}" : rawBody;
            request = objectMapper.readValue(bodyToParse, EventPollingRequestDTO.class);
        } catch (JsonProcessingException e) {
            throw new EventNotificationException(EventNotificationServiceConstants.ERROR_CODE_INVALID_REQUEST,
                    EventNotificationServiceConstants.ERROR_TITLE_MALFORMED_REQUEST,
                    "Polling request body is malformed.", 400);
        }
        if (request.getOrgId() != null && !safeOrgId.equalsIgnoreCase(request.getOrgId().trim())) {
            throw new EventNotificationException(EventNotificationServiceConstants.ERROR_CODE_INVALID_REQUEST,
                    EventNotificationServiceConstants.ERROR_TITLE_MALFORMED_REQUEST,
                    "The request organization does not match the tenant context.", 400);
        }

        Set<String> ackIds = normalizeDeliveryIds(
                request.getAck() == null ? Collections.emptyList() : request.getAck());
        Map<String, PollDeliveryError> errors = normalizePollErrors(request.getSetErrs());
        if (!Collections.disjoint(ackIds, errors.keySet())) {
            throw new EventNotificationException(EventNotificationServiceConstants.ERROR_CODE_INVALID_REQUEST,
                    EventNotificationServiceConstants.ERROR_TITLE_MALFORMED_REQUEST,
                    EventNotificationServiceConstants.POLL_ACK_ERROR_OVERLAP_ERROR_MSG, 400);
        }
        boolean returnImmediately = request.getReturnImmediately() == null
                ? configurationService.isEventNotificationPollingDefaultReturnImmediately()
                : request.getReturnImmediately();
        if (!returnImmediately) {
            throw new EventNotificationException(EventNotificationServiceConstants.ERROR_CODE_INVALID_REQUEST,
                    EventNotificationServiceConstants.ERROR_TITLE_MALFORMED_REQUEST,
                    "Long polling is not supported; returnImmediately must be true.", 400);
        }
        int maxEvents = resolvePollingMaxEvents(request.getMaxEvents());

        deliveryDAO.updatePollDeliveryStatusesByDeliveryIds(safeOrgId, safeGroupId, safeSubscriptionId,
                new ArrayList<>(ackIds), errors);
        int fetchLimit = maxEvents == 0 ? 1 : maxEvents + 1;
        List<PollDelivery> pending = deliveryDAO.getPendingPollDeliveries(
                safeOrgId, safeGroupId, safeSubscriptionId, fetchLimit);
        boolean moreAvailable = pending.size() > maxEvents;
        Map<String, String> sets = new LinkedHashMap<>();
        for (int index = 0; index < Math.min(maxEvents, pending.size()); index++) {
            PollDelivery delivery = pending.get(index);
            Event event = eventDAO.getEventById(delivery.getEventId(), safeOrgId)
                    .orElseThrow(() -> new EventNotificationException(
                            EventNotificationServiceConstants.ERROR_CODE_EVENT_NOT_FOUND,
                            EventNotificationServiceConstants.ERROR_TITLE_EVENT_NOT_FOUND,
                            EventNotificationServiceConstants.EVENT_NOT_FOUND_ERROR_MSG, 500));
            try {
                sets.put(delivery.getDeliveryId(), signedEventPayloadFactory.sign(
                        safeOrgId, safeGroupId, safeSubscriptionId, delivery.getDeliveryId(),
                        delivery.getEventId(), event.getTopic(), event.getPayload(), sharedSecret,
                        configurationService.getEventNotificationPayloadSigningAudience()));
            } catch (Exception e) {
                LOG.error("Failed to sign polling delivery [" + LogSanitizer.sanitize(delivery.getDeliveryId())
                        + "].", e);
                throw new EventNotificationException(EventNotificationServiceConstants.ERROR_CODE_INTERNAL_ERROR,
                        EventNotificationServiceConstants.ERROR_TITLE_INTERNAL_ERROR,
                        "Failed to sign polling event payload.", 500);
            }
        }
        return new EventPollingResponseDTO(moreAvailable, sets);
    }

    @Override
    public void completeDelivery(String orgId, String groupId, String deliveryId,
            String requestBody, String eventSignature) {
        String safeOrgId = requireValue(orgId, EventNotificationServiceConstants.ORG_ID_MISSING_ERROR_MSG);
        String safeGroupId = requireValue(groupId, EventNotificationServiceConstants.GROUP_ID_MISSING_ERROR_MSG);
        String safeDeliveryId = requireValue(deliveryId, EventNotificationServiceConstants.DELIVERY_ID_MISSING_ERROR_MSG);
        if (requestBody == null || requestBody.trim().isEmpty()) {
            throw invalidCompletion(EventNotificationServiceConstants.ERROR_TITLE_MALFORMED_REQUEST,
                    "Completion request body is required.");
        }
        Optional<org.wso2.dpdp.accelerator.event.notifications.dao.model.WebhookDelivery> delivery =
                deliveryDAO.getWebhookDeliveryById(safeDeliveryId, safeOrgId);
        if (subscriptionDAO == null) {
            throw new EventNotificationException(EventNotificationServiceConstants.ERROR_CODE_INTERNAL_ERROR,
                    EventNotificationServiceConstants.ERROR_TITLE_INTERNAL_ERROR,
                    "Delivery completion services are not initialized.", 500);
        }
        Optional<org.wso2.dpdp.accelerator.event.notifications.dao.model.Subscription> subscription = delivery
                .flatMap(value -> subscriptionDAO.getSubscriptionById(value.getSubscriptionId(), safeOrgId));
        if (delivery.isEmpty() || subscription.isEmpty()
                || !safeGroupId.equalsIgnoreCase(subscription.get().getGroupId())
                || !HmacSigner.verifyCompletion(subscription.get().getSharedSecret(), safeDeliveryId,
                        requestBody, eventSignature)) {
            throw invalidCompletionSignature();
        }
        if (!DeliveryStatus.DELIVERED.getValue().equalsIgnoreCase(delivery.get().getStatus())) {
            throw new EventNotificationException(EventNotificationServiceConstants.ERROR_CODE_INVALID_STATE,
                    EventNotificationServiceConstants.ERROR_TITLE_INVALID_STATE,
                    EventNotificationServiceConstants.DELIVERY_COMPLETION_INVALID_STATE_ERROR_MSG, 409);
        }
        final DeliveryCompletionRequestDTO completion;
        try {
            completion = objectMapper.readValue(requestBody, DeliveryCompletionRequestDTO.class);
        } catch (JsonProcessingException e) {
            throw invalidCompletion(EventNotificationServiceConstants.ERROR_TITLE_MALFORMED_REQUEST,
                    "Completion request body is malformed.");
        }
        validateCompletion(completion);
        Timestamp completedAt = completion.getCompletedAt() == null
                ? new Timestamp(System.currentTimeMillis()) : new Timestamp(completion.getCompletedAt());
        try {
            deliveryAckDAO.addDeliveryAck(
                    new org.wso2.dpdp.accelerator.event.notifications.dao.model.WebhookDeliveryAck(
                            UUID.randomUUID().toString(), safeDeliveryId, completedAt,
                            completion.getCompletionStatus().trim().toLowerCase(java.util.Locale.ROOT),
                            completion.getCompletionEvidence().trim()));
        } catch (EventNotificationDuplicateResourceException e) {
            throw new EventNotificationException(EventNotificationServiceConstants.ERROR_CODE_RESOURCE_EXISTS,
                    EventNotificationServiceConstants.ERROR_TITLE_RESOURCE_EXISTS,
                    EventNotificationServiceConstants.DELIVERY_COMPLETION_ALREADY_EXISTS_ERROR_MSG, 409);
        }
    }

    private static String requireValue(String value, String message) {
        if (value == null || value.trim().isEmpty()) {
            throw new EventNotificationException(EventNotificationServiceConstants.ERROR_CODE_INVALID_REQUEST,
                    EventNotificationServiceConstants.ERROR_TITLE_MALFORMED_REQUEST, message, 400);
        }
        return value.trim();
    }

    private static void validateCompletion(DeliveryCompletionRequestDTO completion) {
        if (completion == null || completion.getCompletionStatus() == null
                || completion.getCompletionStatus().trim().isEmpty()) {
            throw invalidCompletion(EventNotificationServiceConstants.ERROR_TITLE_MALFORMED_REQUEST,
                    EventNotificationServiceConstants.COMPLETION_STATUS_REQUIRED_ERROR_MSG);
        }
        String status = completion.getCompletionStatus().trim();
        if (!("completed".equalsIgnoreCase(status) || "ack".equalsIgnoreCase(status)
                || "disputed".equalsIgnoreCase(status) || "partial".equalsIgnoreCase(status))) {
            throw invalidCompletion(EventNotificationServiceConstants.ERROR_TITLE_MALFORMED_REQUEST,
                    "completionStatus must be completed, ack, disputed, or partial.");
        }
        if (completion.getCompletionEvidence() == null || completion.getCompletionEvidence().trim().isEmpty()) {
            throw invalidCompletion(EventNotificationServiceConstants.ERROR_TITLE_MALFORMED_REQUEST,
                    EventNotificationServiceConstants.COMPLETION_EVIDENCE_REQUIRED_ERROR_MSG);
        }
        try {
            EventNotificationUrlValidator.validateEvidenceUrl(completion.getCompletionEvidence());
        } catch (IllegalArgumentException e) {
            throw invalidCompletion(EventNotificationServiceConstants.ERROR_TITLE_MALFORMED_REQUEST,
                    EventNotificationServiceConstants.COMPLETION_EVIDENCE_INVALID_ERROR_MSG);
        }
        if (completion.getCompletedAt() != null && completion.getCompletedAt() < 0) {
            throw invalidCompletion(EventNotificationServiceConstants.ERROR_TITLE_MALFORMED_REQUEST,
                    "completedAt must not be negative.");
        }
    }

    private static EventNotificationException invalidCompletion(String title, String description) {
        return new EventNotificationException(EventNotificationServiceConstants.ERROR_CODE_INVALID_REQUEST,
                title, description, 400);
    }

    private static EventNotificationException invalidCompletionSignature() {
        return new EventNotificationException(EventNotificationServiceConstants.ERROR_CODE_INVALID_SIGNATURE,
                EventNotificationServiceConstants.ERROR_TITLE_OPERATION_FORBIDDEN,
                EventNotificationServiceConstants.INVALID_SIGNATURE_ERROR_MSG, 401);
    }

    private static Set<String> normalizeDeliveryIds(List<String> deliveryIds) {

        Set<String> normalized = new HashSet<>();
        for (String deliveryId : deliveryIds) {
            if (deliveryId != null && !deliveryId.trim().isEmpty()) {
                normalized.add(deliveryId.trim());
            }
        }
        return normalized;
    }

    private Map<String, PollDeliveryError> normalizePollErrors(Map<String, PollSetErrorDTO> errors) {

        if (errors == null || errors.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<String, PollDeliveryError> normalized = new LinkedHashMap<>();
        for (Map.Entry<String, PollSetErrorDTO> entry : errors.entrySet()) {
            if (entry.getKey() != null && !entry.getKey().trim().isEmpty()) {
                PollSetErrorDTO error = entry.getValue();
                String code = error == null ? null : error.getErr();
                String description = error == null ? null : error.getDescription();
                if (code == null || code.trim().isEmpty() || code.trim().length()
                        > EventNotificationServiceConstants.MAX_POLL_ERROR_CODE_LENGTH
                        || description == null || description.trim().isEmpty()
                        || description.trim().length()
                        > EventNotificationServiceConstants.MAX_POLL_ERROR_DETAIL_LENGTH) {
                    throw new EventNotificationException(EventNotificationServiceConstants.ERROR_CODE_INVALID_REQUEST,
                            EventNotificationServiceConstants.ERROR_TITLE_MALFORMED_REQUEST,
                            EventNotificationServiceConstants.POLL_ERROR_DETAIL_REQUIRED_ERROR_MSG, 400);
                }
                normalized.put(entry.getKey().trim(),
                        new PollDeliveryError(code.trim(), description.trim()));
            }
        }
        return normalized;
    }

    private int resolvePollingMaxEvents(Integer requestedMaxEvents) {
        int configuredLimit = configurationService.getEventNotificationPollingMaxEventsLimit();
        int effectiveMaxEvents = requestedMaxEvents == null
                ? configurationService.getEventNotificationPollingDefaultMaxEvents() : requestedMaxEvents;
        if (effectiveMaxEvents < 0 || effectiveMaxEvents > configuredLimit) {
            throw new EventNotificationException(EventNotificationServiceConstants.ERROR_CODE_INVALID_REQUEST,
                    EventNotificationServiceConstants.ERROR_TITLE_MALFORMED_REQUEST,
                    "maxEvents must be between 0 and " + configuredLimit + ".", 400);
        }
        return effectiveMaxEvents;
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

        Connection conn = DatabaseUtils.getDBConnection();
        try {
            Topic topic = resolveActiveTopic(conn, orgId, topicName);
            Event event = new Event(eventId, orgId.trim(), groupId.trim(), topic.getTopicId(), payloadJson, now);

            if (!eventDAO.addEvent(conn, event)) {
                throw new EventNotificationException(
                        EventNotificationServiceConstants.ERROR_CODE_INVALID_REQUEST,
                        EventNotificationServiceConstants.ERROR_TITLE_INVALID_STATE,
                        String.format(EventNotificationServiceConstants.TOPIC_NOT_ACTIVE_ERROR_MSG,
                                topic.getName()),
                        400);
            }
            if (purposes != null && !purposes.isEmpty()) {
                eventDAO.addEventPurposes(conn, eventId, purposes);
            }
            eventFanOutService.fanOutEvent(conn, event, purposes);

            EventDTO result = new EventDTO(eventId, orgId, event.getGroupId(), topic.getTopicId(), payloadJson,
                    purposes, now, now);
            DatabaseUtils.commitTransaction(conn);
            return result;
        } catch (EventNotificationException e) {
            DatabaseUtils.rollbackTransaction(conn);
            throw e;
        } catch (Exception e) {
            DatabaseUtils.rollbackTransaction(conn);
            LOG.error("Failed to publish event [" + LogSanitizer.sanitize(eventId) + "]: "
                    + LogSanitizer.sanitize(e.getMessage()), e);
            throw new EventNotificationException(
                    EventNotificationServiceConstants.ERROR_CODE_EVENT_PUBLISH_FAILED,
                    EventNotificationServiceConstants.ERROR_TITLE_EVENT_PUBLISH_FAILED,
                    EventNotificationServiceConstants.EVENT_PUBLISH_FAILED_ERROR_MSG,
                    500);
        } finally {
            DatabaseUtils.closeConnection(conn);
        }
    }

    private Topic resolveActiveTopic(Connection conn, String orgId, String topicName) {
        Optional<Topic> existing = topicDAO.getActiveTopicByOrgAndNameForUpdate(conn, orgId.trim(), topicName.trim());
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
        int lim = EventNotificationParameterUtils.normalizeLimit(limit);
        int off = EventNotificationParameterUtils.normalizeOffset(offset);
        String normalizedStatus = EventNotificationParameterUtils.normalizeStatusFilter(status);
        PaginatedDAOResult<Event> daoResult = subscriptionId == null || subscriptionId.trim().isEmpty()
                ? eventDAO.searchEvents(orgId.trim(), topic, normalizedStatus, groupId, purposes, search, lim, off)
                : eventDAO.searchEvents(orgId.trim(), topic, normalizedStatus, groupId, subscriptionId,
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
        int lim = EventNotificationParameterUtils.normalizeLimit(limit);
        int off = EventNotificationParameterUtils.normalizeOffset(offset);
        String normalizedStatus = EventNotificationParameterUtils.normalizeStatusFilter(status);
        int[] totalOut = new int[1];

        List<SubscriptionDeliverySummary> summaries = deliveryDAO.listOrgDeliveries(
                orgId.trim(), normalizedStatus, subscriptionId, groupId, purposes, search, lim, off, totalOut);

        List<SubscriptionDeliveryDTO> dtoList = new ArrayList<>();
        for (SubscriptionDeliverySummary summary : summaries) {
            dtoList.add(new SubscriptionDeliveryDTO(
                    summary.getDeliveryId(),
                    summary.getEventId(),
                    summary.getSubscriptionId(),
                    summary.getGroupId(),
                    summary.getTopicName(),
                    summary.getCurrentStatus() != null ? summary.getCurrentStatus()
                            : DeliveryHistoryMapper.defaultStatus(summary.getDeliveryMode()),
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

        return DeliveryHistoryMapper.map(orgId.trim(), deliveryId.trim(), summaryOpt.get(), deliveryDAO,
                deliveryAckDAO);
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
        int safeLimit = EventNotificationParameterUtils.normalizeLimit(limit);
        int safeOffset = EventNotificationParameterUtils.normalizeOffset(offset);
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
