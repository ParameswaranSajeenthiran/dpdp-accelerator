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

package org.wso2.dpdp.accelerator.event.notifications.dao;

import org.wso2.dpdp.accelerator.common.util.DatabaseUtils;
import org.wso2.dpdp.accelerator.event.notifications.dao.model.Subscription;

import java.sql.Connection;
import java.sql.Timestamp;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public interface SubscriptionDAO {

    void addSubscription(Subscription subscription);

    void addSubscription(Connection connection, Subscription subscription);

    Optional<Subscription> getSubscriptionById(String subscriptionId, String orgId);

    Optional<Subscription> getSubscriptionById(Connection connection, String subscriptionId, String orgId);

    /**
     * Acquires the subscription row for an exclusive webhook verification attempt.
     * The caller must keep the supplied transaction open until verification and the
     * resulting state transition have completed.
     */
    Optional<Subscription> lockSubscriptionForVerification(Connection connection, String subscriptionId,
            String orgId, String expectedStatus);

    boolean updateSubscriptionStatus(String subscriptionId, String orgId, String status);

    boolean updateSubscriptionStatus(Connection connection, String subscriptionId, String orgId, String status);

    boolean updateSubscriptionStatus(String subscriptionId, String orgId, String expectedStatus, String newStatus);

    boolean updateSubscriptionStatus(Connection connection, String subscriptionId, String orgId,
            String expectedStatus, String newStatus);

    boolean deleteSubscriptionAtomic(String subscriptionId, String orgId, String expectedStatus);

    boolean deleteSubscriptionAtomic(Connection connection, String subscriptionId, String orgId,
            String expectedStatus);

    PaginatedDAOResult<Subscription> listSubscriptions(String orgId, String status, String purposes, String search,
            int limit, int offset, String sort);

    /**
     * Returns all subscriptions for a topic that are in a live state
     * (active, pending, stale) — i.e. all except deleted. Used for
     * duplicate-and-conflict checking on subscription creation.
     */
    List<Subscription> getLiveSubscriptionsByOrgAndTopic(Connection conn, String orgId, String topicId);

    default List<Subscription> getLiveSubscriptionsByOrgAndTopic(String orgId, String topicId) {
        Connection conn = DatabaseUtils.getDBConnection();
        try {
            List<Subscription> result = getLiveSubscriptionsByOrgAndTopic(conn, orgId, topicId);
            DatabaseUtils.commitTransaction(conn);
            return result;
        } catch (RuntimeException e) {
            DatabaseUtils.rollbackTransaction(conn);
            throw e;
        } finally {
            DatabaseUtils.closeConnection(conn);
        }
    }

    /**
     * Returns and locks active subscriptions that are eligible for event fan-out.
     * The caller must hold the supplied transaction until all delivery rows have
     * been inserted.
     */
    List<Subscription> getActiveSubscriptionsForFanOut(Connection conn, String orgId, String topicId);

    long countActiveSubscriptionsForTopic(String orgId, String topicId);

    List<String> getPurposesBySubscriptionId(String subscriptionId, String orgId);

    Map<String, List<String>> getPurposesBySubscriptionIds(List<String> subscriptionIds);

    boolean hasPendingOrInFlightDeliveries(String subscriptionId, String orgId);

    List<Subscription> getPendingSubscriptionsForRecovery(Timestamp updatedBefore, int limit);

}
