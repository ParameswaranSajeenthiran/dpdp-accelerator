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

import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Reference;
import org.wso2.dpdp.accelerator.event.notifications.common.config.EventNotificationConfigParser;
import org.wso2.dpdp.accelerator.event.notifications.common.constants.EventNotificationCommonConstants;
import org.wso2.dpdp.accelerator.event.notifications.common.util.HTTPClientFactory;
import org.wso2.dpdp.accelerator.event.notifications.common.util.PurposeOverlapUtils;
import org.wso2.dpdp.accelerator.event.notifications.dao.DeliveryAckDAO;
import org.wso2.dpdp.accelerator.event.notifications.dao.DeliveryDAO;
import org.wso2.dpdp.accelerator.event.notifications.dao.PaginatedDAOResult;
import org.wso2.dpdp.accelerator.event.notifications.dao.SubscriptionDAO;
import org.wso2.dpdp.accelerator.event.notifications.dao.TopicDAO;
import org.wso2.dpdp.accelerator.event.notifications.dao.model.PollDelivery;
import org.wso2.dpdp.accelerator.event.notifications.dao.model.Subscription;
import org.wso2.dpdp.accelerator.event.notifications.dao.model.SubscriptionDeliverySummary;
import org.wso2.dpdp.accelerator.event.notifications.dao.model.Topic;
import org.wso2.dpdp.accelerator.event.notifications.dao.model.WebhookDelivery;
import org.wso2.dpdp.accelerator.event.notifications.dao.model.WebhookDeliveryAck;
import org.wso2.dpdp.accelerator.event.notifications.dao.model.WebhookDeliveryAudit;
import org.wso2.dpdp.accelerator.event.notifications.service.SubscriptionService;
import org.wso2.dpdp.accelerator.event.notifications.service.constants.EventNotificationServiceConstants;
import org.wso2.dpdp.accelerator.event.notifications.service.dto.DeliveryConfigDTO;
import org.wso2.dpdp.accelerator.event.notifications.service.dto.FilterDTO;
import org.wso2.dpdp.accelerator.event.notifications.service.dto.SubscriptionDTO;
import org.wso2.dpdp.accelerator.event.notifications.service.dto.SubscriptionDeliveryAttemptDTO;
import org.wso2.dpdp.accelerator.event.notifications.service.dto.SubscriptionDeliveryDTO;
import org.wso2.dpdp.accelerator.event.notifications.service.dto.SubscriptionEventHistoryDTO;
import org.wso2.dpdp.accelerator.event.notifications.common.enums.DeliveryMode;
import org.wso2.dpdp.accelerator.event.notifications.common.enums.DeliveryStatus;
import org.wso2.dpdp.accelerator.event.notifications.common.enums.PurposeFilterMode;
import org.wso2.dpdp.accelerator.event.notifications.common.enums.SubscriptionStatus;
import org.wso2.dpdp.accelerator.event.notifications.common.enums.TopicStatus;
import org.wso2.dpdp.accelerator.event.notifications.common.exception.EventNotificationDuplicateResourceException;
import org.wso2.dpdp.accelerator.event.notifications.common.exception.EventNotificationInvalidStateException;
import org.wso2.dpdp.accelerator.event.notifications.service.exception.EventNotificationException;
import org.wso2.dpdp.accelerator.event.notifications.service.model.PaginatedResult;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

@Component(service = SubscriptionService.class, immediate = true)
public class SubscriptionServiceImpl implements SubscriptionService {

    private static final Logger LOG = Logger.getLogger(SubscriptionServiceImpl.class.getName());

    @Reference
    private SubscriptionDAO subscriptionDAO;
    @Reference
    private TopicDAO topicDAO;
    @Reference
    private DeliveryDAO deliveryDAO;
    @Reference
    private DeliveryAckDAO deliveryAckDAO;

    private ScheduledExecutorService scheduler;
    private HttpClient httpClient;

    public SubscriptionServiceImpl() {
    }

    public SubscriptionServiceImpl(SubscriptionDAO subscriptionDAO, TopicDAO topicDAO,
            DeliveryDAO deliveryDAO, DeliveryAckDAO deliveryAckDAO) {
        this.subscriptionDAO = subscriptionDAO;
        this.topicDAO = topicDAO;
        this.deliveryDAO = deliveryDAO;
        this.deliveryAckDAO = deliveryAckDAO;
    }

    @Activate
    protected void activate() {
        int poolSize = EventNotificationConfigParser.getInstance().getThreadPoolSize();
        this.scheduler = Executors.newScheduledThreadPool(poolSize, r -> {
            Thread t = new Thread(r, "webhook-verification-pool");
            t.setDaemon(true);
            return t;
        });

        this.httpClient = HTTPClientFactory.getHttpClient();
    }

    @Deactivate
    protected void deactivate() {
        if (scheduler != null && !scheduler.isShutdown()) {
            scheduler.shutdownNow();
        }
    }

    @Override
    public SubscriptionDTO createSubscription(String orgId, String groupId, String topicName,
            FilterDTO filter, DeliveryConfigDTO delivery) {
        if (orgId == null || orgId.trim().isEmpty()) {
            throw new EventNotificationException(EventNotificationServiceConstants.ERROR_CODE_INVALID_REQUEST,
                    EventNotificationServiceConstants.ERROR_TITLE_MALFORMED_REQUEST,
                    EventNotificationServiceConstants.ORG_ID_MISSING_ERROR_MSG, 400);
        }
        if (groupId == null || groupId.trim().isEmpty()) {
            throw new EventNotificationException(EventNotificationServiceConstants.ERROR_CODE_INVALID_REQUEST,
                    EventNotificationServiceConstants.ERROR_TITLE_MALFORMED_REQUEST,
                    EventNotificationServiceConstants.GROUP_ID_MISSING_ERROR_MSG, 400);
        }
        if (topicName == null || topicName.trim().isEmpty()) {
            throw new EventNotificationException(EventNotificationServiceConstants.ERROR_CODE_INVALID_REQUEST,
                    EventNotificationServiceConstants.ERROR_TITLE_MALFORMED_REQUEST,
                    EventNotificationServiceConstants.ORG_ID_OR_TOPIC_NAME_MISSING_ERROR_MSG, 400);
        }

        Optional<Topic> topicOpt = topicDAO.getTopicByOrgAndName(orgId.trim(), topicName.trim());
        if (topicOpt.isEmpty() || TopicStatus.DEREGISTERED.getValue().equalsIgnoreCase(topicOpt.get().getStatus())) {
            throw new EventNotificationException(EventNotificationServiceConstants.ERROR_CODE_TOPIC_NOT_FOUND,
                    EventNotificationServiceConstants.ERROR_TITLE_TOPIC_NOT_FOUND,
                    "Topic '" + topicName + "' is not registered for this org.", 404);
        }

        Topic topic = topicOpt.get();
        PurposeFilterMode filterType = (filter != null && filter.getType() != null) ? filter.getType()
                : PurposeFilterMode.ALL;
        List<String> purposes = (filter != null && filter.getPurposes() != null) ? filter.getPurposes()
                : Collections.emptyList();
        DeliveryMode deliveryMode = (delivery != null && delivery.getMode() != null) ? delivery.getMode()
                : DeliveryMode.WEBHOOK;
        String callbackUrl = (delivery != null) ? delivery.getCallbackUrl() : null;
        String sharedSecret = (delivery != null) ? delivery.getSharedSecret() : null;

        if (deliveryMode == DeliveryMode.WEBHOOK) {
            validateCallbackUrl(callbackUrl);
        }

        validatePurposeFilterMode(filterType, purposes);

        List<Subscription> existingSubs = subscriptionDAO.getLiveSubscriptionsByOrgAndTopic(orgId.trim(),
                topic.getTopicId());
        String effectiveGroupId = groupId.trim();
        validateDuplicateAndConflict(existingSubs, effectiveGroupId, filterType, purposes, deliveryMode, callbackUrl);

        String initialStatus = (deliveryMode == DeliveryMode.WEBHOOK)
                ? SubscriptionStatus.PENDING.getValue()
                : SubscriptionStatus.ACTIVE.getValue();

        String subscriptionId = UUID.randomUUID().toString();
        Subscription sub = new Subscription(subscriptionId, orgId.trim(), effectiveGroupId, topic.getTopicId(),
                filterType.getValue(), purposes, deliveryMode.getValue(),
                callbackUrl != null ? callbackUrl.trim() : null,
                sharedSecret != null ? sharedSecret.trim() : null,
                initialStatus, null, null);

        try {
            subscriptionDAO.addSubscription(sub);
        } catch (EventNotificationInvalidStateException e) {
            // The DAO detected a deregistered/inactive topic under the row lock — a
            // concurrent
            // TopicService.deleteTopic committed between our service-layer pre-check and
            // the
            // FOR UPDATE acquisition in the DAO. Map to 409 with the topic name for
            // clarity.
            throw new EventNotificationException(
                    EventNotificationServiceConstants.ERROR_CODE_INVALID_STATE,
                    EventNotificationServiceConstants.ERROR_TITLE_INVALID_STATE,
                    String.format(EventNotificationServiceConstants.TOPIC_NOT_ACTIVE_ERROR_MSG, topicName.trim()),
                    409);
        } catch (EventNotificationDuplicateResourceException e) {
            throw new EventNotificationException(EventNotificationServiceConstants.ERROR_CODE_RESOURCE_EXISTS,
                    EventNotificationServiceConstants.ERROR_TITLE_DUPLICATE_SUBSCRIPTION,
                    EventNotificationServiceConstants.DUPLICATE_SUBSCRIPTION_ERROR_MSG, 409);
        }

        if (deliveryMode == DeliveryMode.WEBHOOK) {
            scheduleWebhookVerificationTask(subscriptionId, orgId.trim(), callbackUrl.trim(), topicName.trim(), 0);
        }

        return mapToDTO(sub, topicName.trim());
    }

    private void validatePurposeFilterMode(PurposeFilterMode filterType, List<String> purposes) {
        Set<String> purposesSet = PurposeOverlapUtils.canonicalize(purposes);
        if (filterType == PurposeFilterMode.SPECIFIC && purposesSet.isEmpty()) {
            throw new EventNotificationException(
                    EventNotificationServiceConstants.ERROR_CODE_MISSING_REQUIRED_PARAM,
                    EventNotificationServiceConstants.ERROR_TITLE_VALIDATION_FAILED,
                    EventNotificationServiceConstants.FILTER_PURPOSES_REQUIRED_FOR_SPECIFIC_ERROR_MSG, 422);
        }
        if (filterType == PurposeFilterMode.EXCEPT && purposesSet.isEmpty()) {
            throw new EventNotificationException(
                    EventNotificationServiceConstants.ERROR_CODE_MISSING_REQUIRED_PARAM,
                    EventNotificationServiceConstants.ERROR_TITLE_VALIDATION_FAILED,
                    EventNotificationServiceConstants.FILTER_PURPOSES_REQUIRED_FOR_EXCEPT_ERROR_MSG, 422);
        }
    }

    private void validateDuplicateAndConflict(List<Subscription> existingSubs, String groupId,
            PurposeFilterMode filterType, List<String> purposes,
            DeliveryMode deliveryMode, String callbackUrl) {
        Set<String> newPurposesSet = PurposeOverlapUtils.canonicalize(purposes);

        for (Subscription existing : existingSubs) {
            if (!groupId.equals(existing.getGroupId() != null ? existing.getGroupId() : "")) {
                continue;
            }

            String existingStatus = existing.getStatus() != null ? existing.getStatus().toLowerCase() : "";
            if (!SubscriptionStatus.ACTIVE.getValue().toLowerCase().equals(existingStatus)
                    && !SubscriptionStatus.PENDING.getValue().toLowerCase().equals(existingStatus)
                    && !SubscriptionStatus.STALE.getValue().toLowerCase().equals(existingStatus)) {
                continue;
            }

            PurposeFilterMode existingFilterMode = PurposeFilterMode.fromValueOrDefault(existing.getPurposeFilterMode(),
                    PurposeFilterMode.ALL);
            Set<String> existingPurposesSet = PurposeOverlapUtils.canonicalize(existing.getPurposes());

            if (PurposeOverlapUtils.overlaps(filterType, newPurposesSet, existingFilterMode, existingPurposesSet)) {
                throw new EventNotificationException(EventNotificationServiceConstants.ERROR_CODE_RESOURCE_EXISTS,
                        EventNotificationServiceConstants.ERROR_TITLE_DUPLICATE_SUBSCRIPTION,
                        EventNotificationServiceConstants.DUPLICATE_SUBSCRIPTION_ERROR_MSG, 409);
            }
        }
    }

    private void scheduleWebhookVerificationTask(String subscriptionId, String orgId, String callbackUrl,
            String topicName,
            int attempt) {
        if (scheduler == null || scheduler.isShutdown()) {
            return;
        }
        long delaySeconds = 0;
        if (attempt > 0) {
            delaySeconds = EventNotificationConfigParser.getInstance().getBaseBackoffSeconds()
                    * (long) Math.pow(3, attempt - 1);
        }
        scheduler.schedule(new WebhookVerificationTask(subscriptionId, orgId, callbackUrl, topicName, attempt),
                delaySeconds,
                TimeUnit.SECONDS);
    }

    private class WebhookVerificationTask implements Runnable {
        private final String subscriptionId;
        private final String orgId;
        private final String callbackUrl;
        private final String topicName;
        private final int attempt;

        WebhookVerificationTask(String subscriptionId, String orgId, String callbackUrl, String topicName,
                int attempt) {
            this.subscriptionId = subscriptionId;
            this.orgId = orgId;
            this.callbackUrl = callbackUrl;
            this.topicName = topicName;
            this.attempt = attempt;
        }

        @Override
        public void run() {
            try {
                verifyWebhookCallback(callbackUrl, topicName);
                boolean updated = subscriptionDAO.updateSubscriptionStatus(subscriptionId, orgId,
                        SubscriptionStatus.PENDING.getValue(), SubscriptionStatus.ACTIVE.getValue());
                if (updated) {
                    LOG.info("Webhook verification succeeded for subscription [" + subscriptionId + "] on attempt "
                            + (attempt + 1) + ".");
                } else {
                    LOG.info("Webhook verification succeeded for subscription [" + subscriptionId
                            + "] but status was no longer PENDING.");
                }
            } catch (Exception e) {
                int nextAttempt = attempt + 1;
                int maxRetries = EventNotificationConfigParser.getInstance().getMaxRetries();
                if (nextAttempt <= maxRetries) {
                    LOG.log(Level.WARNING, "Webhook verification attempt " + (attempt + 1)
                            + " failed for subscription [" + subscriptionId + "]. Retrying. Reason: " + e.getMessage());
                    scheduleWebhookVerificationTask(subscriptionId, orgId, callbackUrl, topicName, nextAttempt);
                } else {
                    LOG.log(Level.WARNING,
                            "Exhausted all retries for subscription [" + subscriptionId + "]. Marking as STALE.");
                    subscriptionDAO.updateSubscriptionStatus(subscriptionId, orgId,
                            SubscriptionStatus.PENDING.getValue(),
                            SubscriptionStatus.STALE.getValue());
                }
            }
        }
    }

    private void validateCallbackUrl(String callbackUrl) {
        if (callbackUrl == null || callbackUrl.trim().isEmpty()) {
            throw new EventNotificationException(
                    EventNotificationServiceConstants.ERROR_CODE_MISSING_REQUIRED_PARAM,
                    EventNotificationServiceConstants.ERROR_TITLE_VALIDATION_FAILED,
                    EventNotificationServiceConstants.CALLBACK_URL_REQUIRED_ERROR_MSG, 422);
        }
        try {
            HTTPClientFactory.validateUrl(callbackUrl);
            URI uri = URI.create(callbackUrl.trim());
            String scheme = uri.getScheme();
            if ("http".equalsIgnoreCase(scheme)
                    && !EventNotificationConfigParser.getInstance().isAllowHttpCallbackUrl()) {
                throw new EventNotificationException(
                        EventNotificationServiceConstants.ERROR_CODE_INVALID_REQUEST,
                        EventNotificationServiceConstants.ERROR_TITLE_VALIDATION_FAILED,
                        EventNotificationServiceConstants.CALLBACK_URL_HTTPS_REQUIRED_ERROR_MSG, 400);
            }
        } catch (EventNotificationException e) {
            throw e;
        } catch (Exception e) {
            throw new EventNotificationException(
                    EventNotificationServiceConstants.ERROR_CODE_INVALID_REQUEST,
                    EventNotificationServiceConstants.ERROR_TITLE_VALIDATION_FAILED,
                    "Invalid callbackUrl: " + e.getMessage(), 400);
        }
    }

    private void verifyWebhookCallback(String callbackUrl, String topicName) {
        validateCallbackUrl(callbackUrl);
        String challenge = UUID.randomUUID().toString();
        try {
            String encodedTopic = URLEncoder.encode(topicName, StandardCharsets.UTF_8);
            String encodedChallenge = URLEncoder.encode(challenge, StandardCharsets.UTF_8);
            String separator = callbackUrl.contains("?") ? "&" : "?";
            String verificationUrl = callbackUrl + separator + "hub.mode=subscribe&hub.topic=" + encodedTopic
                    + "&hub.challenge=" + encodedChallenge;

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(verificationUrl))
                    .timeout(Duration.ofSeconds(5))
                    .GET()
                    .build();

            HttpResponse<byte[]> response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());
            int maxBodyBytes = EventNotificationConfigParser.getInstance().getMaxVerificationResponseBodyBytes();
            if (response.statusCode() != 200) {
                throw new EventNotificationException(
                        EventNotificationServiceConstants.ERROR_CODE_WEBHOOK_VERIFICATION_FAILED,
                        EventNotificationServiceConstants.ERROR_TITLE_WEBHOOK_VERIFICATION_FAILED,
                        "Callback URL responded with HTTP " + response.statusCode(),
                        422);
            }

            byte[] bodyBytes = response.body() != null ? response.body() : new byte[0];
            if (bodyBytes.length > maxBodyBytes) {
                throw new EventNotificationException(
                        EventNotificationServiceConstants.ERROR_CODE_WEBHOOK_VERIFICATION_FAILED,
                        EventNotificationServiceConstants.ERROR_TITLE_WEBHOOK_VERIFICATION_FAILED,
                        "Verification response body exceeded " + maxBodyBytes + " bytes", 422);
            }
            String body = new String(bodyBytes, StandardCharsets.UTF_8).trim();
            // Spec-compliant verification: the response body must be the challenge value
            // exactly
            // (modulo surrounding whitespace). Using contains() would let a receiver pass
            // the
            // check by appending the challenge to arbitrary content.
            if (!challenge.equals(body)) {
                throw new EventNotificationException(
                        EventNotificationServiceConstants.ERROR_CODE_WEBHOOK_VERIFICATION_FAILED,
                        EventNotificationServiceConstants.ERROR_TITLE_WEBHOOK_VERIFICATION_FAILED,
                        EventNotificationServiceConstants.WEBHOOK_CHALLENGE_MISMATCH_ERROR_MSG, 422);
            }
        } catch (EventNotificationException e) {
            throw e;
        } catch (Exception e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            throw new EventNotificationException(
                    EventNotificationServiceConstants.ERROR_CODE_WEBHOOK_VERIFICATION_FAILED,
                    EventNotificationServiceConstants.ERROR_TITLE_WEBHOOK_VERIFICATION_FAILED,
                    "Webhook call failed: " + e.getMessage(), 422);
        }
    }

    @Override
    public PaginatedResult<SubscriptionDTO> listSubscriptions(String orgId, String status, String purposes,
            String search, int limit, int offset, String sort) {
        if (orgId == null || orgId.trim().isEmpty()) {
            throw new EventNotificationException(EventNotificationServiceConstants.ERROR_CODE_INVALID_REQUEST,
                    EventNotificationServiceConstants.ERROR_TITLE_MALFORMED_REQUEST,
                    EventNotificationServiceConstants.ORG_ID_MISSING_ERROR_MSG, 400);
        }
        int lim = (limit <= 0) ? EventNotificationCommonConstants.DEFAULT_LIMIT
                : Math.min(limit, EventNotificationCommonConstants.MAX_LIMIT);
        int off = (offset < 0) ? 0 : offset;
        PaginatedDAOResult<Subscription> daoResult = subscriptionDAO.listSubscriptions(orgId.trim(), status, purposes,
                search,
                lim,
                off, sort);
        List<SubscriptionDTO> dtoList = new ArrayList<>();
        for (Subscription sub : daoResult.getItems()) {
            String topicName = topicDAO.getTopicById(sub.getTopicId(), sub.getOrgId()).map(Topic::getName)
                    .orElse("unknown");
            dtoList.add(mapToDTO(sub, topicName));
        }
        return new PaginatedResult<>(dtoList, daoResult.getTotal());
    }

    @Override
    public SubscriptionDTO getSubscription(String orgId, String subscriptionIdStr) {
        if (orgId == null || orgId.trim().isEmpty()) {
            throw new EventNotificationException(EventNotificationServiceConstants.ERROR_CODE_INVALID_REQUEST,
                    EventNotificationServiceConstants.ERROR_TITLE_MALFORMED_REQUEST,
                    EventNotificationServiceConstants.ORG_ID_MISSING_ERROR_MSG, 400);
        }
        if (subscriptionIdStr == null || subscriptionIdStr.trim().isEmpty()) {
            throw new EventNotificationException(EventNotificationServiceConstants.ERROR_CODE_INVALID_REQUEST,
                    EventNotificationServiceConstants.ERROR_TITLE_MALFORMED_REQUEST,
                    EventNotificationServiceConstants.SUBSCRIPTION_ID_MISSING_ERROR_MSG, 400);
        }
        Optional<Subscription> subOpt = subscriptionDAO.getSubscriptionById(subscriptionIdStr.trim(), orgId.trim());
        if (subOpt.isPresent()) {
            Subscription sub = subOpt.get();
            String topicName = topicDAO.getTopicById(sub.getTopicId(), sub.getOrgId()).map(Topic::getName)
                    .orElse("unknown");
            return mapToDTO(sub, topicName);
        }
        throw new EventNotificationException(EventNotificationServiceConstants.ERROR_CODE_RESOURCE_NOT_FOUND,
                EventNotificationServiceConstants.ERROR_TITLE_RESOURCE_NOT_FOUND,
                EventNotificationServiceConstants.SUBSCRIPTION_NOT_FOUND_ERROR_MSG, 404);
    }

    @Override
    public SubscriptionDTO deleteSubscription(String orgId, String subscriptionIdStr) {
        if (orgId == null || orgId.trim().isEmpty()) {
            throw new EventNotificationException(EventNotificationServiceConstants.ERROR_CODE_INVALID_REQUEST,
                    EventNotificationServiceConstants.ERROR_TITLE_MALFORMED_REQUEST,
                    EventNotificationServiceConstants.ORG_ID_MISSING_ERROR_MSG, 400);
        }
        if (subscriptionIdStr == null || subscriptionIdStr.trim().isEmpty()) {
            throw new EventNotificationException(EventNotificationServiceConstants.ERROR_CODE_INVALID_REQUEST,
                    EventNotificationServiceConstants.ERROR_TITLE_MALFORMED_REQUEST,
                    EventNotificationServiceConstants.SUBSCRIPTION_ID_MISSING_ERROR_MSG, 400);
        }

        Optional<Subscription> subOpt = subscriptionDAO.getSubscriptionById(subscriptionIdStr.trim(), orgId.trim());
        if (subOpt.isEmpty() || SubscriptionStatus.DELETED.getValue().equalsIgnoreCase(subOpt.get().getStatus())) {
            throw new EventNotificationException(EventNotificationServiceConstants.ERROR_CODE_RESOURCE_NOT_FOUND,
                    EventNotificationServiceConstants.ERROR_TITLE_RESOURCE_NOT_FOUND,
                    EventNotificationServiceConstants.SUBSCRIPTION_NOT_FOUND_ERROR_MSG, 404);
        }

        Subscription sub = subOpt.get();
        boolean deleted = subscriptionDAO.deleteSubscriptionAtomic(sub.getSubscriptionId(), orgId.trim(),
                sub.getStatus());
        if (!deleted) {
            Optional<Subscription> latest = subscriptionDAO.getSubscriptionById(sub.getSubscriptionId(), orgId.trim());
            if (latest.isEmpty() || SubscriptionStatus.DELETED.getValue().equalsIgnoreCase(latest.get().getStatus())) {
                throw new EventNotificationException(EventNotificationServiceConstants.ERROR_CODE_RESOURCE_NOT_FOUND,
                        EventNotificationServiceConstants.ERROR_TITLE_RESOURCE_NOT_FOUND,
                        EventNotificationServiceConstants.SUBSCRIPTION_NOT_FOUND_ERROR_MSG, 404);
            }
            if (!sub.getStatus().equalsIgnoreCase(latest.get().getStatus())) {
                throw new EventNotificationException(EventNotificationServiceConstants.ERROR_CODE_RESOURCE_EXISTS,
                        EventNotificationServiceConstants.ERROR_TITLE_CONCURRENT_MUTATION,
                        EventNotificationServiceConstants.SUBSCRIPTION_CONCURRENT_MODIFICATION_ERROR_MSG, 409);
            }
            if (subscriptionDAO.hasPendingOrInFlightDeliveries(sub.getSubscriptionId(), orgId.trim())) {
                throw new EventNotificationException(EventNotificationServiceConstants.ERROR_CODE_RESOURCE_EXISTS,
                        EventNotificationServiceConstants.ERROR_TITLE_IN_FLIGHT_DELIVERIES,
                        EventNotificationServiceConstants.SUBSCRIPTION_IN_FLIGHT_DELIVERIES_ERROR_MSG, 409);
            }
            throw new EventNotificationException(EventNotificationServiceConstants.ERROR_CODE_INTERNAL_ERROR,
                    EventNotificationServiceConstants.ERROR_TITLE_INTERNAL_ERROR,
                    EventNotificationServiceConstants.FAILED_TO_DELETE_SUBSCRIPTION_ERROR_MSG, 500);
        }

        String topicName = topicDAO.getTopicById(sub.getTopicId(), sub.getOrgId()).map(Topic::getName)
                .orElse("unknown");
        Subscription deletedSub = new Subscription(
                sub.getSubscriptionId(),
                sub.getOrgId(),
                sub.getGroupId(),
                sub.getTopicId(),
                sub.getPurposeFilterMode(),
                sub.getPurposes(),
                sub.getDeliveryMode(),
                sub.getCallbackUrl(),
                sub.getSharedSecret(),
                SubscriptionStatus.DELETED.getValue(),
                sub.getCreatedAt(),
                new java.sql.Timestamp(System.currentTimeMillis()));
        return mapToDTO(deletedSub, topicName);
    }

    @Override
    public void retriggerVerificationTask(String orgId, String subscriptionId, String callbackUrl, String topicName) {
        if (orgId == null || orgId.trim().isEmpty()) {
            throw new EventNotificationException(EventNotificationServiceConstants.ERROR_CODE_INVALID_REQUEST,
                    EventNotificationServiceConstants.ERROR_TITLE_MALFORMED_REQUEST,
                    EventNotificationServiceConstants.ORG_ID_MISSING_ERROR_MSG, 400);
        }
        if (subscriptionId == null || subscriptionId.trim().isEmpty()) {
            throw new EventNotificationException(EventNotificationServiceConstants.ERROR_CODE_INVALID_REQUEST,
                    EventNotificationServiceConstants.ERROR_TITLE_MALFORMED_REQUEST,
                    EventNotificationServiceConstants.SUBSCRIPTION_ID_MISSING_ERROR_MSG, 400);
        }
        if (callbackUrl == null || callbackUrl.trim().isEmpty() || topicName == null
                || topicName.trim().isEmpty()) {
            throw new EventNotificationException(
                    EventNotificationServiceConstants.ERROR_CODE_MISSING_REQUIRED_PARAM,
                    EventNotificationServiceConstants.ERROR_TITLE_VALIDATION_FAILED,
                    EventNotificationServiceConstants.CALLBACK_URL_REQUIRED_ERROR_MSG, 422);
        }

        // Confirm the subscription exists for this org before scheduling any outbound
        // traffic.
        Optional<Subscription> subOpt = subscriptionDAO.getSubscriptionById(subscriptionId.trim(), orgId.trim());
        if (subOpt.isEmpty() || SubscriptionStatus.DELETED.getValue().equalsIgnoreCase(subOpt.get().getStatus())) {
            throw new EventNotificationException(EventNotificationServiceConstants.ERROR_CODE_RESOURCE_NOT_FOUND,
                    EventNotificationServiceConstants.ERROR_TITLE_RESOURCE_NOT_FOUND,
                    EventNotificationServiceConstants.SUBSCRIPTION_NOT_FOUND_ERROR_MSG, 404);
        }

        // Apply the same SSRF + scheme guards the public path uses. Without this, this
        // entry point
        // would let callers schedule arbitrary outbound webhooks against any URL.
        validateCallbackUrl(callbackUrl.trim());

        scheduleWebhookVerificationTask(subscriptionId.trim(), orgId.trim(), callbackUrl.trim(), topicName.trim(), 0);
    }

    @Override
    public SubscriptionDTO retryVerification(String orgId, String subscriptionId) {
        if (orgId == null || orgId.trim().isEmpty()) {
            throw new EventNotificationException(EventNotificationServiceConstants.ERROR_CODE_INVALID_REQUEST,
                    EventNotificationServiceConstants.ERROR_TITLE_MALFORMED_REQUEST,
                    EventNotificationServiceConstants.ORG_ID_MISSING_ERROR_MSG, 400);
        }
        if (subscriptionId == null || subscriptionId.trim().isEmpty()) {
            throw new EventNotificationException(EventNotificationServiceConstants.ERROR_CODE_INVALID_REQUEST,
                    EventNotificationServiceConstants.ERROR_TITLE_MALFORMED_REQUEST,
                    EventNotificationServiceConstants.SUBSCRIPTION_ID_MISSING_ERROR_MSG, 400);
        }

        Optional<Subscription> subOpt = subscriptionDAO.getSubscriptionById(subscriptionId.trim(), orgId.trim());
        if (subOpt.isEmpty() || SubscriptionStatus.DELETED.getValue().equalsIgnoreCase(subOpt.get().getStatus())) {
            throw new EventNotificationException(EventNotificationServiceConstants.ERROR_CODE_RESOURCE_NOT_FOUND,
                    EventNotificationServiceConstants.ERROR_TITLE_RESOURCE_NOT_FOUND,
                    EventNotificationServiceConstants.SUBSCRIPTION_NOT_FOUND_ERROR_MSG, 404);
        }

        Subscription sub = subOpt.get();
        if (!SubscriptionStatus.STALE.getValue().equalsIgnoreCase(sub.getStatus())
                && !SubscriptionStatus.PENDING.getValue().equalsIgnoreCase(sub.getStatus())) {
            throw new EventNotificationException(EventNotificationServiceConstants.ERROR_CODE_INVALID_STATE,
                    EventNotificationServiceConstants.ERROR_TITLE_INVALID_STATE,
                    EventNotificationServiceConstants.ONLY_STALE_SUBSCRIPTIONS_VERIFIABLE_ERROR_MSG,
                    409);
        }

        if (sub.getCallbackUrl() == null || sub.getCallbackUrl().trim().isEmpty()) {
            throw new EventNotificationException(EventNotificationServiceConstants.ERROR_CODE_INVALID_STATE,
                    EventNotificationServiceConstants.ERROR_TITLE_INVALID_STATE,
                    EventNotificationServiceConstants.NO_CALLBACK_URL_ERROR_MSG, 409);
        }

        String topicName = topicDAO.getTopicById(sub.getTopicId(), sub.getOrgId()).map(Topic::getName)
                .orElse("unknown");
        try {
            verifyWebhookCallback(sub.getCallbackUrl().trim(), topicName);
            subscriptionDAO.updateSubscriptionStatus(subscriptionId.trim(), orgId.trim(),
                    SubscriptionStatus.ACTIVE.getValue());
            sub.setStatus(SubscriptionStatus.ACTIVE.getValue());
        } catch (EventNotificationException e) {
            throw new EventNotificationException(
                    EventNotificationServiceConstants.ERROR_CODE_WEBHOOK_VERIFICATION_FAILED,
                    EventNotificationServiceConstants.ERROR_TITLE_WEBHOOK_VERIFICATION_FAILED, e.getDescription(), 422);
        }

        return mapToDTO(sub, topicName);
    }

    @Override
    public PaginatedResult<SubscriptionDeliveryDTO> listSubscriptionEvents(String orgId, String subscriptionId,
            int limit, int offset) {
        if (orgId == null || orgId.trim().isEmpty()) {
            throw new EventNotificationException(EventNotificationServiceConstants.ERROR_CODE_INVALID_REQUEST,
                    EventNotificationServiceConstants.ERROR_TITLE_MALFORMED_REQUEST,
                    EventNotificationServiceConstants.ORG_ID_MISSING_ERROR_MSG, 400);
        }
        if (subscriptionId == null || subscriptionId.trim().isEmpty()) {
            throw new EventNotificationException(EventNotificationServiceConstants.ERROR_CODE_INVALID_REQUEST,
                    EventNotificationServiceConstants.ERROR_TITLE_MALFORMED_REQUEST,
                    EventNotificationServiceConstants.SUBSCRIPTION_ID_MISSING_ERROR_MSG, 400);
        }

        Optional<Subscription> subOpt = subscriptionDAO.getSubscriptionById(subscriptionId.trim(), orgId.trim());
        if (subOpt.isEmpty()) {
            throw new EventNotificationException(EventNotificationServiceConstants.ERROR_CODE_RESOURCE_NOT_FOUND,
                    EventNotificationServiceConstants.ERROR_TITLE_RESOURCE_NOT_FOUND,
                    EventNotificationServiceConstants.SUBSCRIPTION_NOT_FOUND_ERROR_MSG, 404);
        }

        int lim = (limit <= 0) ? EventNotificationCommonConstants.DEFAULT_LIMIT
                : Math.min(limit, EventNotificationCommonConstants.MAX_LIMIT);
        int off = (offset < 0) ? 0 : offset;
        int[] totalOut = new int[1];

        List<SubscriptionDeliverySummary> summaries = deliveryDAO.listSubscriptionDeliveries(orgId.trim(),
                subscriptionId.trim(), lim,
                off, totalOut);
        List<SubscriptionDeliveryDTO> dtoList = new ArrayList<>();
        for (SubscriptionDeliverySummary summary : summaries) {
            dtoList.add(new SubscriptionDeliveryDTO(
                    summary.getDeliveryId(),
                    summary.getEventId(),
                    summary.getTopicName(),
                    summary.getCurrentStatus() != null ? summary.getCurrentStatus()
                            : EventNotificationServiceConstants.STATUS_PENDING,
                    summary.getDeliveryMode() != null ? summary.getDeliveryMode()
                            : EventNotificationServiceConstants.WEBHOOK_DELIVERY_MODE,
                    summary.getOccurredAt() != null ? summary.getOccurredAt().getTime()
                            : (summary.getCreatedAt() != null ? summary.getCreatedAt().getTime()
                                    : System.currentTimeMillis())));
        }
        return new PaginatedResult<>(dtoList, totalOut[0]);
    }

    @Override
    public SubscriptionEventHistoryDTO getSubscriptionEventHistory(String orgId, String subscriptionId,
            String deliveryId) {
        if (orgId == null || orgId.trim().isEmpty()) {
            throw new EventNotificationException(EventNotificationServiceConstants.ERROR_CODE_INVALID_REQUEST,
                    EventNotificationServiceConstants.ERROR_TITLE_MALFORMED_REQUEST,
                    EventNotificationServiceConstants.ORG_ID_MISSING_ERROR_MSG, 400);
        }
        if (subscriptionId == null || subscriptionId.trim().isEmpty() || deliveryId == null
                || deliveryId.trim().isEmpty()) {
            throw new EventNotificationException(EventNotificationServiceConstants.ERROR_CODE_INVALID_REQUEST,
                    EventNotificationServiceConstants.ERROR_TITLE_MALFORMED_REQUEST,
                    EventNotificationServiceConstants.SUBSCRIPTION_ID_MISSING_ERROR_MSG, 400);
        }

        Optional<Subscription> subOpt = subscriptionDAO.getSubscriptionById(subscriptionId.trim(), orgId.trim());
        if (subOpt.isEmpty()) {
            throw new EventNotificationException(EventNotificationServiceConstants.ERROR_CODE_RESOURCE_NOT_FOUND,
                    EventNotificationServiceConstants.ERROR_TITLE_RESOURCE_NOT_FOUND,
                    EventNotificationServiceConstants.SUBSCRIPTION_NOT_FOUND_ERROR_MSG, 404);
        }

        Optional<SubscriptionDeliverySummary> summaryOpt = deliveryDAO
                .getSubscriptionDeliveryById(orgId.trim(), subscriptionId.trim(), deliveryId.trim());
        if (summaryOpt.isEmpty()) {
            throw new EventNotificationException(EventNotificationServiceConstants.ERROR_CODE_DELIVERY_NOT_FOUND,
                    EventNotificationServiceConstants.ERROR_TITLE_DELIVERY_NOT_FOUND,
                    EventNotificationServiceConstants.DELIVERY_NOT_FOUND_ERROR_MSG, 404);
        }

        SubscriptionDeliverySummary summary = summaryOpt.get();
        String mode = summary.getDeliveryMode() != null ? summary.getDeliveryMode()
                : EventNotificationServiceConstants.WEBHOOK_DELIVERY_MODE;

        SubscriptionEventHistoryDTO dto = new SubscriptionEventHistoryDTO();
        dto.setDeliveryId(summary.getDeliveryId());
        dto.setEventId(summary.getEventId());
        dto.setTopic(summary.getTopicName());
        dto.setDeliveryMode(mode);
        dto.setCurrentStatus(summary.getCurrentStatus() != null ? summary.getCurrentStatus()
                : EventNotificationServiceConstants.STATUS_PENDING);
        dto.setOccurredAt(summary.getOccurredAt() != null ? summary.getOccurredAt().getTime()
                : (summary.getCreatedAt() != null ? summary.getCreatedAt().getTime() : System.currentTimeMillis()));

        if (EventNotificationServiceConstants.WEBHOOK_DELIVERY_MODE.equals(mode)) {
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
            List<SubscriptionDeliveryAttemptDTO> attempts = new ArrayList<>();
            int attemptNumber = 1;
            for (WebhookDeliveryAudit audit : audits) {
                Integer httpStatus = null;
                String error = null;
                String status = DeliveryStatus.FAILED.getValue();
                if (audit.getResponseCode() != null) {
                    try {
                        httpStatus = Integer.parseInt(audit.getResponseCode().trim());
                        if (httpStatus >= 200 && httpStatus < 300) {
                            status = DeliveryStatus.DELIVERED.getValue();
                        } else {
                            error = "HTTP " + httpStatus;
                        }
                    } catch (NumberFormatException e) {
                        error = audit.getResponseCode();
                    }
                }
                attempts.add(new SubscriptionDeliveryAttemptDTO(attemptNumber++, status,
                        audit.getAttemptAt() != null ? audit.getAttemptAt().getTime()
                                : (audit.getCreatedAt() != null ? audit.getCreatedAt().getTime()
                                        : System.currentTimeMillis()),
                        httpStatus, error));
            }
            if (attempts.isEmpty()) {
                attempts.add(new SubscriptionDeliveryAttemptDTO(1,
                        summary.getCurrentStatus() != null ? summary.getCurrentStatus()
                                : EventNotificationServiceConstants.STATUS_PENDING,
                        summary.getCreatedAt() != null ? summary.getCreatedAt().getTime() : System.currentTimeMillis(),
                        null, null));
            }
            dto.setHistory(attempts);
        } else {
            Optional<PollDelivery> pollOpt = deliveryDAO.getPollDeliveryById(deliveryId.trim(), orgId.trim());
            List<SubscriptionDeliveryAttemptDTO> attempts = new ArrayList<>();
            String pollStatus = summary.getCurrentStatus() != null ? summary.getCurrentStatus()
                    : EventNotificationServiceConstants.STATUS_PENDING;
            long timestamp = summary.getOccurredAt() != null ? summary.getOccurredAt().getTime()
                    : System.currentTimeMillis();

            if (pollOpt.isPresent()) {
                PollDelivery pd = pollOpt.get();
                if (pd.getCompletedAt() != null) {
                    timestamp = pd.getCompletedAt().getTime();
                }
            }

            dto.setCompletionStatus(pollStatus);
            attempts.add(new SubscriptionDeliveryAttemptDTO(1, pollStatus, timestamp, null, null));
            dto.setHistory(attempts);
        }

        return dto;
    }

    private SubscriptionDTO mapToDTO(Subscription sub, String topicName) {
        if (sub == null) {
            return null;
        }

        SubscriptionStatus status = SubscriptionStatus.fromValueOrDefault(sub.getStatus(), SubscriptionStatus.ACTIVE);
        Long createdAt = sub.getCreatedAt() != null ? sub.getCreatedAt().getTime() : null;
        Long updatedAt = sub.getUpdatedAt() != null ? sub.getUpdatedAt().getTime() : null;

        PurposeFilterMode filterType = PurposeFilterMode.fromValueOrDefault(sub.getPurposeFilterMode(),
                PurposeFilterMode.ALL);
        FilterDTO filter = new FilterDTO(filterType, sub.getPurposes());

        DeliveryMode deliveryMode = DeliveryMode.fromValueOrDefault(sub.getDeliveryMode(), DeliveryMode.WEBHOOK);
        DeliveryConfigDTO delivery = new DeliveryConfigDTO(deliveryMode, sub.getCallbackUrl(), null);

        return new SubscriptionDTO(
                sub.getSubscriptionId(),
                sub.getOrgId(),
                sub.getGroupId(),
                topicName,
                filter,
                delivery,
                status,
                createdAt,
                updatedAt,
                false,
                null);
    }
}
