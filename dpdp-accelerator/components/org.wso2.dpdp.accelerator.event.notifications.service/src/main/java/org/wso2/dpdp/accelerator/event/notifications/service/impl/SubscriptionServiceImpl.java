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
import org.wso2.dpdp.accelerator.event.notifications.service.dao.DeliveryAckDAO;
import org.wso2.dpdp.accelerator.event.notifications.service.dao.DeliveryDAO;
import org.wso2.dpdp.accelerator.event.notifications.service.dao.SubscriptionDAO;
import org.wso2.dpdp.accelerator.event.notifications.service.dao.TopicDAO;
import org.wso2.dpdp.accelerator.event.notifications.service.dao.impl.DeliveryAckDAOImpl;
import org.wso2.dpdp.accelerator.event.notifications.service.dao.impl.DeliveryDAOImpl;
import org.wso2.dpdp.accelerator.event.notifications.service.dao.impl.SubscriptionDAOImpl;
import org.wso2.dpdp.accelerator.event.notifications.service.dao.impl.TopicDAOImpl;

import org.wso2.dpdp.accelerator.event.notifications.service.dao.model.PollDelivery;
import org.wso2.dpdp.accelerator.event.notifications.service.dao.model.Subscription;
import org.wso2.dpdp.accelerator.event.notifications.service.dao.model.SubscriptionDeliverySummary;
import org.wso2.dpdp.accelerator.event.notifications.service.dao.model.Topic;
import org.wso2.dpdp.accelerator.event.notifications.service.dao.model.WebhookDelivery;
import org.wso2.dpdp.accelerator.event.notifications.service.dao.model.WebhookDeliveryAck;
import org.wso2.dpdp.accelerator.event.notifications.service.dao.model.WebhookDeliveryAudit;

import org.wso2.dpdp.accelerator.event.notifications.service.enums.DeliveryMode;
import org.wso2.dpdp.accelerator.event.notifications.service.enums.DeliveryStatus;
import org.wso2.dpdp.accelerator.event.notifications.service.enums.PurposeFilterMode;
import org.wso2.dpdp.accelerator.event.notifications.service.enums.SubscriptionStatus;
import org.wso2.dpdp.accelerator.event.notifications.service.enums.TopicStatus;

import org.wso2.dpdp.accelerator.event.notifications.service.SubscriptionService;
import org.wso2.dpdp.accelerator.event.notifications.service.dto.DeliveryConfigDTO;
import org.wso2.dpdp.accelerator.event.notifications.service.dto.FilterDTO;
import org.wso2.dpdp.accelerator.event.notifications.service.dto.SubscriptionDTO;
import org.wso2.dpdp.accelerator.event.notifications.service.dto.SubscriptionDeliveryAttemptDTO;
import org.wso2.dpdp.accelerator.event.notifications.service.dto.SubscriptionDeliveryDTO;
import org.wso2.dpdp.accelerator.event.notifications.service.dto.SubscriptionEventHistoryDTO;
import org.wso2.dpdp.accelerator.event.notifications.service.exception.DataAccessException;
import org.wso2.dpdp.accelerator.event.notifications.service.exception.ENFException;
import org.wso2.dpdp.accelerator.event.notifications.service.mapper.SubscriptionModelMapper;

import org.osgi.service.component.annotations.Component;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
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

    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    /** Max automatic retry attempts for async webhook intent verification (after the initial attempt). */
    private static final int WEBHOOK_MAX_RETRIES = 3;

    /** Base backoff delay in seconds. Actual delay = BASE_BACKOFF_SECONDS * 3^attempt (1s, 5s, 15s, 45s). */
    private static final long BASE_BACKOFF_SECONDS = 5;

    /**
     * Single-thread scheduled executor used exclusively for async webhook verification retries.
     * Using a daemon thread so it does not prevent JVM/OSGi shutdown.
     */
    private static final ScheduledExecutorService WEBHOOK_SCHEDULER = Executors.newScheduledThreadPool(
            Runtime.getRuntime().availableProcessors(),
            runnable -> {
                Thread t = new Thread(runnable, "webhook-verify");
                t.setDaemon(true);
                return t;
            });

    private final SubscriptionDAO subscriptionDAO;
    private final TopicDAO topicDAO;
    private final DeliveryDAO deliveryDAO;
    private final DeliveryAckDAO deliveryAckDAO;

    public SubscriptionServiceImpl() {
        this(new SubscriptionDAOImpl(), new TopicDAOImpl(), new DeliveryDAOImpl(), new DeliveryAckDAOImpl());
    }

    public SubscriptionServiceImpl(SubscriptionDAO subscriptionDAO, TopicDAO topicDAO, DeliveryDAO deliveryDAO,
            DeliveryAckDAO deliveryAckDAO) {
        this.subscriptionDAO = subscriptionDAO;
        this.topicDAO = topicDAO;
        this.deliveryDAO = deliveryDAO;
        this.deliveryAckDAO = deliveryAckDAO;
    }

    @Override
    public SubscriptionDTO createSubscription(String orgId, String groupId, String topicName, FilterDTO filter,
            DeliveryConfigDTO delivery) {
        if (orgId == null || orgId.trim().isEmpty() || groupId == null || groupId.trim().isEmpty()
                || topicName == null || topicName.trim().isEmpty() || filter == null || filter.getType() == null
                || delivery == null || delivery.getMode() == null) {
            throw new ENFException("CS-4001", "Malformed request",
                    "Org ID, group ID, and required subscription fields are missing.", 400);
        }

        PurposeFilterMode filterType = filter.getType();
        List<String> purposes = filter.getPurposes() != null ? new ArrayList<>(filter.getPurposes())
                : new ArrayList<>();

        // Validation rule 422 CS-4002: filter.purposes required for SPECIFIC / EXCEPT
        if ((filterType == PurposeFilterMode.SPECIFIC || filterType == PurposeFilterMode.EXCEPT)
                && purposes.isEmpty()) {
            throw new ENFException("CS-4002", "Validation failed",
                    "filter.purposes is required for filter.type " + filterType.getValue() + ".", 422);
        }

        DeliveryMode deliveryMode = delivery.getMode();

        // Validation rule 422 CS-4002: callbackUrl required for webhook
        if (deliveryMode == DeliveryMode.WEBHOOK
                && (delivery.getCallbackUrl() == null || delivery.getCallbackUrl().trim().isEmpty())) {
            throw new ENFException("CS-4002", "Validation failed",
                    "delivery.callbackUrl is required for delivery.mode webhook.", 422);
        }

        if (delivery.getSharedSecret() == null || delivery.getSharedSecret().trim().isEmpty()) {
            throw new ENFException("CS-4001", "Malformed request", "delivery.sharedSecret is required.", 400);
        }

        // Resolve the topic from the organization's topic registry.
        Optional<Topic> topicOpt = topicDAO.getTopicByOrgAndName(orgId, topicName.trim());
        if (topicOpt.isEmpty() || !TopicStatus.ACTIVE.getValue().equalsIgnoreCase(topicOpt.get().getStatus())) {
            throw new ENFException("CS-4040", "Topic not found",
                    "No active topic exists with this name for the given org.", 404);
        }
        String topicId = topicOpt.get().getTopicId();
        String resolvedTopicName = topicOpt.get().getName();

        // Sort purposes to guarantee uniqueness matching
        Collections.sort(purposes);

        // Check for duplicate active subscription
        Optional<Subscription> duplicate = subscriptionDAO.findDuplicateSubscription(orgId, groupId, topicId,
                filterType.getValue(), purposes);
        if (duplicate.isPresent()) {
            Subscription existing = duplicate.get();
            SubscriptionDTO existingDTO = new SubscriptionDTO();
            existingDTO.setSubscriptionId(existing.getSubscriptionId());
            existingDTO.setAlreadyExists(true);
            existingDTO.setMessage(
                    "Subscription already exists for topic '" + topicName + "' and the specified purposes.");
            return existingDTO;
        }

        // Check for conflicting/overlapping active subscriptions
        validateSubscriptionConflict(orgId, groupId, topicId, filterType.getValue(), purposes);

        String subId = UUID.randomUUID().toString();
        Timestamp now = new Timestamp(System.currentTimeMillis());

        // Webhook subscriptions start as PENDING; poll subscriptions go straight to ACTIVE.
        String initialStatus = deliveryMode == DeliveryMode.WEBHOOK
                ? SubscriptionStatus.PENDING.getValue()
                : SubscriptionStatus.ACTIVE.getValue();

        Subscription sub = new Subscription(
                subId,
                orgId,
                groupId,
                topicId,
                initialStatus,
                filterType.getValue(),
                deliveryMode == DeliveryMode.WEBHOOK ? delivery.getCallbackUrl().trim() : null,
                delivery.getSharedSecret().trim(),
                now,
                now,
                deliveryMode.getValue());
        sub.setPurposes(purposes);

        try {
            boolean created = subscriptionDAO.addSubscription(sub);
            if (!created) {
                throw new ENFException("CS-5000", "Internal error", "Failed to persist subscription.", 500);
            }
        } catch (DataAccessException dae) {
            // Catch unique-constraint violation (TOCTOU race between two concurrent identical POSTs)
            // and surface it as a 409 Conflict rather than a 500.
            Throwable cause = dae.getCause();
            if (cause instanceof SQLException) {
                String state = ((SQLException) cause).getSQLState();
                // SQLState 23xxx = integrity constraint violation across all major RDBMS
                if (state != null && state.startsWith("23")) {
                    throw new ENFException("CS-4090", "Subscription conflict",
                            "A concurrent request already created a subscription with the same parameters.", 409);
                }
            }
            throw new ENFException("CS-5000", "Internal error", "Failed to persist subscription: " + dae.getMessage(), 500);
        }

        // For webhook subscriptions, perform Intent Verification asynchronously so the
        // request thread is not blocked. The subscription stays PENDING until verification
        // succeeds (→ ACTIVE) or all retries are exhausted (→ STALE).
        if (deliveryMode == DeliveryMode.WEBHOOK) {
            final String callbackUrl = delivery.getCallbackUrl().trim();
            final String finalTopicName = resolvedTopicName;
            scheduleWebhookVerification(subId, callbackUrl, finalTopicName, 0);
        }

        return mapToDTO(sub, resolvedTopicName);
    }

    @Override
    public PaginatedResult<SubscriptionDTO> listSubscriptions(String orgId, String status, String purposes,
            String search, int limit, int offset, String sort) {
        PaginatedResult<Subscription> daoResult = subscriptionDAO.listSubscriptions(orgId, status, purposes, search,
                limit, offset, sort);

        // Batch-fetch all topics needed for this page in a single query (avoids N+1).
        Set<String> topicIds = new HashSet<>();
        for (Subscription sub : daoResult.getItems()) {
            topicIds.add(sub.getTopicId());
        }
        Map<String, String> topicNameById = topicDAO.getTopicNamesByIds(new ArrayList<>(topicIds));

        List<SubscriptionDTO> result = new ArrayList<>();
        for (Subscription sub : daoResult.getItems()) {
            String topicName = topicNameById.getOrDefault(sub.getTopicId(), "unknown");
            result.add(mapToDTO(sub, topicName));
        }
        return new PaginatedResult<>(result, daoResult.getTotal());
    }

    @Override
    public SubscriptionDTO getSubscription(String orgId, String subscriptionId) {
        Optional<Subscription> subOpt = subscriptionDAO.getSubscriptionById(subscriptionId, orgId);
        if (subOpt.isEmpty() || SubscriptionStatus.DELETED.getValue().equalsIgnoreCase(subOpt.get().getStatus())) {
            throw new ENFException("CS-4040", "Resource not found",
                    "No subscription exists with this ID for the given org.", 404);
        }
        Subscription sub = subOpt.get();
        String topicName = topicDAO.getTopicById(sub.getTopicId()).map(Topic::getName).orElse("unknown");
        return mapToDTO(sub, topicName);
    }

    @Override
    public void deleteSubscription(String orgId, String subscriptionId) {
        Optional<Subscription> subOpt = subscriptionDAO.getSubscriptionById(subscriptionId, orgId);
        if (subOpt.isEmpty() || SubscriptionStatus.DELETED.getValue().equalsIgnoreCase(subOpt.get().getStatus())) {
            throw new ENFException("CS-4040", "Resource not found",
                    "No subscription exists with this ID for the given org.", 404);
        }

        // Check if undelivered WEBHOOK_DELIVERY or POLL_DELIVERY rows exist
        if (subscriptionDAO.hasPendingOrInFlightDeliveries(subscriptionId)) {
            throw new ENFException("CS-4092", "Subscription has active deliveries",
                    "This subscription cannot be deleted while undelivered WEBHOOK_DELIVERY or POLL_DELIVERY rows exist for it.", 409);
        }

        boolean updated = subscriptionDAO.updateSubscriptionStatus(subscriptionId, SubscriptionStatus.DELETED.getValue());
        if (!updated) {
            throw new ENFException("CS-5000", "Internal error", "Failed to update subscription status.", 500);
        }
    }

    private void validateSubscriptionConflict(String orgId, String groupId, String topicId, String newFilterType,
            List<String> newPurposes) {
        List<Subscription> activeSubs = subscriptionDAO.getActiveSubscriptionsForMatching(orgId, groupId, topicId);
        if (activeSubs == null || activeSubs.isEmpty()) {
            return;
        }

        Set<String> newPurposeSet = new HashSet<>(newPurposes != null ? newPurposes : Collections.emptyList());

        for (Subscription sub : activeSubs) {
            String existMode = sub.getPurposeFilterMode().toLowerCase();
            Set<String> existPurposes = new HashSet<>(
                    sub.getPurposes() != null ? sub.getPurposes() : Collections.emptyList());

            // Case A: New or existing filter mode is 'all'
            if (PurposeFilterMode.ALL.getValue().equalsIgnoreCase(newFilterType)
                    || PurposeFilterMode.ALL.getValue().equalsIgnoreCase(existMode)) {
                throw new ENFException("CS-4090", "Subscription conflict",
                        "A subscription with filter type 'all' conflicts with other subscriptions for this topic.", 409);
            }

            // Case B: New filter mode is 'specific'
            if (PurposeFilterMode.SPECIFIC.getValue().equalsIgnoreCase(newFilterType)) {
                if (PurposeFilterMode.SPECIFIC.getValue().equalsIgnoreCase(existMode)) {
                    Set<String> overlap = new HashSet<>(newPurposeSet);
                    overlap.retainAll(existPurposes);
                    if (!overlap.isEmpty()) {
                        throw new ENFException("CS-4090", "Subscription conflict",
                                "Specific subscription purpose(s) " + overlap
                                        + " overlap with an existing specific subscription.", 409);
                    }
                } else if (PurposeFilterMode.EXCEPT.getValue().equalsIgnoreCase(existMode)) {
                    Set<String> nonExcluded = new HashSet<>(newPurposeSet);
                    nonExcluded.removeAll(existPurposes);
                    if (!nonExcluded.isEmpty()) {
                        throw new ENFException("CS-4090", "Subscription conflict",
                                "Specific subscription purpose(s) " + nonExcluded
                                        + " are not excluded by the existing all_except subscription.", 409);
                    }
                }
            }

            // Case C: New filter mode is 'all_except'
            if (PurposeFilterMode.EXCEPT.getValue().equalsIgnoreCase(newFilterType)) {
                if (PurposeFilterMode.EXCEPT.getValue().equalsIgnoreCase(existMode)) {
                    throw new ENFException("CS-4090", "Subscription conflict",
                            "An all_except subscription already exists for this subscriber and topic.", 409);
                } else if (PurposeFilterMode.SPECIFIC.getValue().equalsIgnoreCase(existMode)) {
                    Set<String> nonExcluded = new HashSet<>(existPurposes);
                    nonExcluded.removeAll(newPurposeSet);
                    if (!nonExcluded.isEmpty()) {
                        throw new ENFException("CS-4090", "Subscription conflict",
                                "New all_except subscription does not exclude existing specific purpose(s) "
                                        + nonExcluded + ".", 409);
                    }
                }
            }
        }
    }

    /**
     * Schedules an async webhook intent-verification attempt.
     * On failure, retries up to {@value #WEBHOOK_MAX_RETRIES} times with exponential back-off
     * (5 s, 15 s, 45 s). After all retries are exhausted the subscription is marked STALE.
     *
     * @param subscriptionId the subscription to verify
     * @param callbackUrl    the webhook URL to verify
     * @param topicName      the topic name sent as part of the challenge
     * @param attempt        zero-based attempt counter (0 = first/immediate attempt)
     */
    private void scheduleWebhookVerification(String subscriptionId, String callbackUrl,
            String topicName, int attempt) {

        long delaySeconds = 0;
        if (attempt > 0) {
            // Exponential back-off: 5s, 15s, 45s
            delaySeconds = BASE_BACKOFF_SECONDS * (long) Math.pow(3, attempt - 1);
        }

        WEBHOOK_SCHEDULER.schedule(() -> {
            try {
                verifyWebhookCallback(callbackUrl, topicName);
                subscriptionDAO.updateSubscriptionStatus(subscriptionId, SubscriptionStatus.ACTIVE.getValue());
                LOG.info("Webhook verification succeeded for subscription [" + subscriptionId
                        + "] on attempt " + (attempt + 1) + ".");
            } catch (Exception e) {
                int nextAttempt = attempt + 1;
                if (nextAttempt <= WEBHOOK_MAX_RETRIES) {
                    LOG.log(Level.WARNING, "Webhook verification attempt " + (attempt + 1)
                            + " failed for subscription [" + subscriptionId + "]. "
                            + "Retrying in " + (BASE_BACKOFF_SECONDS * (long) Math.pow(3, attempt)) + "s. "
                            + "Reason: " + e.getMessage());
                    scheduleWebhookVerification(subscriptionId, callbackUrl, topicName, nextAttempt);
                } else {
                    LOG.log(Level.WARNING, "Webhook verification exhausted all " + WEBHOOK_MAX_RETRIES
                            + " retries for subscription [" + subscriptionId + "]. Marking as STALE.");
                    subscriptionDAO.updateSubscriptionStatus(subscriptionId, SubscriptionStatus.STALE.getValue());
                }
            }
        }, delaySeconds, TimeUnit.SECONDS);
    }

    private void verifyWebhookCallback(String callbackUrl, String topicName) {
        // --- SSRF Prevention: validate scheme and disallow private/loopback addresses
        // ---
        try {
            URI parsedUri = URI.create(callbackUrl);
            String scheme = parsedUri.getScheme();
            if (scheme == null || (!scheme.equalsIgnoreCase("http") && !scheme.equalsIgnoreCase("https"))) {
                throw new ENFException("CS-4220", "Webhook verification failed",
                        "Callback URL must use http or https scheme. Received: '" + scheme + "'", 422);
            }
            String host = parsedUri.getHost();
            if (host == null || host.trim().isEmpty()) {
                throw new ENFException("CS-4220", "Webhook verification failed",
                        "Callback URL does not contain a valid host.", 422);
            }
            java.net.InetAddress addr;
            try {
                addr = java.net.InetAddress.getByName(host);
            } catch (java.net.UnknownHostException e) {
                throw new ENFException("CS-4220", "Webhook verification failed",
                        "Callback URL host cannot be resolved: '" + host + "'", 422);
            }
            // Block loopback and private/internal network ranges (SSRF protection).
            // Set ENF_ALLOW_LOOPBACK=true environment variable to allow local dev/test
            // callback URLs.
            if (addr.isLoopbackAddress() || addr.isSiteLocalAddress() || addr.isLinkLocalAddress()
                    || addr.isAnyLocalAddress()) {
                boolean allowLoopback = "true".equalsIgnoreCase(System.getenv("ENF_ALLOW_LOOPBACK"));
                if (!allowLoopback) {
                    throw new ENFException("CS-4220", "Webhook verification failed",
                            "Callback URL must not resolve to a private or loopback address. " +
                                    "Set ENF_ALLOW_LOOPBACK=true in non-production environments.",
                            422);
                }
            }
        } catch (ENFException e) {
            throw e;
        } catch (IllegalArgumentException e) {
            throw new ENFException("CS-4220", "Webhook verification failed",
                    "Callback URL is not a valid URI: '" + callbackUrl + "'", 422);
        }

        String challenge = UUID.randomUUID().toString();
        String delimiter = callbackUrl.contains("?") ? "&" : "?";
        String verificationUrl = callbackUrl + delimiter
                + "hub.mode=subscribe"
                + "&hub.topic=" + URLEncoder.encode(topicName, StandardCharsets.UTF_8)
                + "&hub.challenge=" + challenge
                + "&challenge=" + challenge;

        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(verificationUrl))
                    .timeout(Duration.ofSeconds(5))
                    .GET()
                    .build();

            HttpResponse<String> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                throw new ENFException("CS-4220", "Webhook verification failed",
                        "Callback URL [" + callbackUrl + "] responded with HTTP " + response.statusCode()
                                + " during intent verification.",
                        422);
            }

            String body = response.body() != null ? response.body().trim() : "";
            if (!body.contains(challenge)) {
                throw new ENFException("CS-4220", "Webhook verification failed",
                        "Callback URL [" + callbackUrl
                                + "] did not echo back the expected challenge string. Received: '" + body + "'",
                        422);
            }
        } catch (ENFException e) {
            throw e;
        } catch (Exception e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            throw new ENFException("CS-4220", "Webhook verification failed",
                    "Webhook verification call failed: " + e.getMessage(), 422);
        }
    }

    @Override
    public SubscriptionDTO retryVerification(String orgId, String subscriptionId) {
        if (subscriptionId == null || subscriptionId.trim().isEmpty()) {
            throw new ENFException("CS-4001", "Malformed request", "subscriptionId is required.", 400);
        }

        Optional<Subscription> subOpt = subscriptionDAO.getSubscriptionById(subscriptionId.trim(), orgId);
        if (subOpt.isEmpty() || SubscriptionStatus.DELETED.getValue().equalsIgnoreCase(subOpt.get().getStatus())) {
            throw new ENFException("CS-4040", "Resource not found",
                    "No subscription exists with this ID for the given org.", 404);
        }

        Subscription sub = subOpt.get();

        if (!SubscriptionStatus.STALE.getValue().equalsIgnoreCase(sub.getStatus())) {
            throw new ENFException("CS-4003", "Invalid state",
                    "Only subscriptions in 'stale' state can be re-verified. Current status: " + sub.getStatus(), 409);
        }

        if (sub.getCallbackUrl() == null || sub.getCallbackUrl().trim().isEmpty()) {
            throw new ENFException("CS-4003", "Invalid state",
                    "Subscription does not have a callback URL — re-verification is only applicable to webhook subscriptions.", 409);
        }

        String topicName = topicDAO.getTopicById(sub.getTopicId()).map(Topic::getName).orElse("unknown");

        try {
            verifyWebhookCallback(sub.getCallbackUrl().trim(), topicName);
            subscriptionDAO.updateSubscriptionStatus(subscriptionId.trim(), SubscriptionStatus.ACTIVE.getValue());
            sub.setStatus(SubscriptionStatus.ACTIVE.getValue());
        } catch (ENFException e) {
            // Keep status as stale; propagate the verification error detail
            throw new ENFException("CS-4220", "Webhook re-verification failed",
                    "Re-verification of callback URL [" + sub.getCallbackUrl().trim() + "] failed. "
                            + e.getDescription(),
                    422);
        }

        return mapToDTO(sub, topicName);
    }

    private SubscriptionDTO mapToDTO(Subscription sub, String topicName) {
        SubscriptionDTO dto = SubscriptionModelMapper.toDTO(sub, topicName);
        if (dto.getFilter() != null && sub.getPurposes() != null) {
            dto.getFilter().setPurposes(sub.getPurposes());
        }
        if (dto.getDelivery() != null) {
            dto.getDelivery().setSharedSecret(null); // sharedSecret writeOnly
        }
        return dto;
    }

    @Override
    public PaginatedResult<SubscriptionDeliveryDTO> listSubscriptionEvents(String orgId, String subscriptionId,
            int limit, int offset) {
        if (subscriptionId == null || subscriptionId.trim().isEmpty()) {
            throw new ENFException("CS-4001", "Malformed request", "subscriptionId is required.", 400);
        }

        // Validate subscription exists and belongs to orgId
        Optional<Subscription> subOpt = subscriptionDAO.getSubscriptionById(subscriptionId.trim(), orgId);
        if (subOpt.isEmpty()) {
            throw new ENFException("CS-4040", "Subscription not found",
                    "Subscription with ID '" + subscriptionId + "' does not exist for this org.", 404);
        }

        int lim = (limit <= 0) ? 20 : Math.min(limit, 100);
        int off = (offset < 0) ? 0 : offset;

        int[] totalOut = new int[1];
        List<SubscriptionDeliverySummary> summaries = deliveryDAO
                .listSubscriptionDeliveries(subscriptionId.trim(), lim, off, totalOut);
        List<SubscriptionDeliveryDTO> dtoList = new ArrayList<>();
        for (SubscriptionDeliverySummary summary : summaries) {
            dtoList.add(new SubscriptionDeliveryDTO(
                    summary.getDeliveryId(),
                    summary.getEventId(),
                    summary.getTopicName(),
                    summary.getCurrentStatus() != null ? summary.getCurrentStatus().toUpperCase() : "PENDING",
                    summary.getDeliveryMode() != null ? summary.getDeliveryMode().toUpperCase() : "WEBHOOK",
                    summary.getOccurredAt() != null ? summary.getOccurredAt().getTime()
                            : (summary.getCreatedAt() != null ? summary.getCreatedAt().getTime()
                                    : System.currentTimeMillis())));
        }
        return new PaginatedResult<>(dtoList, totalOut[0]);
    }

    @Override
    public SubscriptionEventHistoryDTO getSubscriptionEventHistory(String orgId, String subscriptionId,
            String deliveryId) {
        if (subscriptionId == null || subscriptionId.trim().isEmpty() || deliveryId == null
                || deliveryId.trim().isEmpty()) {
            throw new ENFException("CS-4001", "Malformed request", "subscriptionId and deliveryId are required.", 400);
        }

        // Validate subscription exists and belongs to orgId
        Optional<Subscription> subOpt = subscriptionDAO.getSubscriptionById(subscriptionId.trim(), orgId);
        if (subOpt.isEmpty()) {
            throw new ENFException("CS-4040", "Subscription not found",
                    "Subscription with ID '" + subscriptionId + "' does not exist for this org.", 404);
        }

        // Validate delivery exists and belongs to this subscription
        Optional<SubscriptionDeliverySummary> summaryOpt = deliveryDAO
                .getSubscriptionDeliveryById(subscriptionId.trim(), deliveryId.trim());
        if (summaryOpt.isEmpty()) {
            throw new ENFException("CS-4042", "Delivery not found",
                    "Delivery with ID '" + deliveryId + "' does not exist for subscription '" + subscriptionId + "'.", 404);
        }

        SubscriptionDeliverySummary summary = summaryOpt.get();
        String mode = summary.getDeliveryMode() != null ? summary.getDeliveryMode().toUpperCase() : "WEBHOOK";

        SubscriptionEventHistoryDTO dto = new SubscriptionEventHistoryDTO();
        dto.setDeliveryId(summary.getDeliveryId());
        dto.setEventId(summary.getEventId());
        dto.setTopic(summary.getTopicName());
        dto.setDeliveryMode(mode);
        dto.setCurrentStatus(summary.getCurrentStatus() != null ? summary.getCurrentStatus().toUpperCase() : "PENDING");
        dto.setOccurredAt(summary.getOccurredAt() != null ? summary.getOccurredAt().getTime()
                : (summary.getCreatedAt() != null ? summary.getCreatedAt().getTime()
                        : System.currentTimeMillis()));

        if ("WEBHOOK".equals(mode)) {
            // Webhook details
            Optional<WebhookDelivery> whOpt = deliveryDAO.getWebhookDeliveryById(deliveryId.trim(), orgId);
            if (whOpt.isPresent()) {
                WebhookDelivery wh = whOpt.get();
                if (wh.getNextRetryAt() != null) {
                    dto.setNextRetryAt(wh.getNextRetryAt().getTime());
                }
            }

            // Webhook ACK if present
            Optional<WebhookDeliveryAck> ackOpt = deliveryAckDAO.getDeliveryAckByDeliveryId(deliveryId.trim());
            if (ackOpt.isPresent()) {
                WebhookDeliveryAck ack = ackOpt.get();
                dto.setCompletionStatus(
                        ack.getCompletionStatus() != null ? ack.getCompletionStatus().toUpperCase() : "COMPLETED");
                dto.setCompletionEvidence(ack.getCompletionEvidence());
            }

            // Webhook attempt audit history
            List<WebhookDeliveryAudit> audits = deliveryDAO.getWebhookDeliveryAudits(deliveryId.trim());
            List<SubscriptionDeliveryAttemptDTO> attempts = new ArrayList<>();
            int attemptNumber = 1;
            for (WebhookDeliveryAudit audit : audits) {
                Integer httpStatus = null;
                String error = null;
                String status = "FAILED";
                if (audit.getResponseCode() != null) {
                    try {
                        httpStatus = Integer.parseInt(audit.getResponseCode().trim());
                        if (httpStatus >= 200 && httpStatus < 300) {
                            status = "DELIVERED";
                        } else {
                            error = "HTTP " + httpStatus;
                        }
                    } catch (NumberFormatException e) {
                        error = audit.getResponseCode();
                    }
                }
                attempts.add(new SubscriptionDeliveryAttemptDTO(
                        attemptNumber++,
                        status,
                        audit.getAttemptAt() != null ? audit.getAttemptAt().getTime()
                                : (audit.getCreatedAt() != null ? audit.getCreatedAt().getTime()
                                        : System.currentTimeMillis()),
                        httpStatus,
                        error));
            }
            // If no audits found yet but webhook delivery exists (initial attempt pending)
            if (attempts.isEmpty()) {
                attempts.add(new SubscriptionDeliveryAttemptDTO(
                        1,
                        summary.getCurrentStatus() != null ? summary.getCurrentStatus().toUpperCase() : "PENDING",
                        summary.getCreatedAt() != null ? summary.getCreatedAt().getTime() : System.currentTimeMillis(),
                        null,
                        null));
            }
            dto.setHistory(attempts);
        } else {
            // Poll details
            Optional<PollDelivery> pollOpt = deliveryDAO.getPollDeliveryById(deliveryId.trim(), orgId);
            List<SubscriptionDeliveryAttemptDTO> attempts = new ArrayList<>();
            String pollStatus = summary.getCurrentStatus() != null ? summary.getCurrentStatus().toUpperCase()
                    : "PENDING";
            long timestamp = summary.getOccurredAt() != null ? summary.getOccurredAt().getTime()
                    : System.currentTimeMillis();

            if (pollOpt.isPresent()) {
                PollDelivery pd = pollOpt.get();
                if (pd.getCompletedAt() != null) {
                    timestamp = pd.getCompletedAt().getTime();
                }
            }

            dto.setCompletionStatus(pollStatus);
            attempts.add(new SubscriptionDeliveryAttemptDTO(
                    1,
                    pollStatus,
                    timestamp,
                    null,
                    null));
            dto.setHistory(attempts);
        }

        return dto;
    }
}
