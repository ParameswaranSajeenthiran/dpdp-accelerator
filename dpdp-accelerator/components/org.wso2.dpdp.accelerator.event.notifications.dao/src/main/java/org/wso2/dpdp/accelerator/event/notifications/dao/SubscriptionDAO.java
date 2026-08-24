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

import org.wso2.dpdp.accelerator.event.notifications.common.constants.EventNotificationCommonConstants;
import org.wso2.dpdp.accelerator.event.notifications.common.exception.EventNotificationDataAccessException;
import org.wso2.dpdp.accelerator.common.persistence.JDBCPersistenceManager;
import org.wso2.dpdp.accelerator.event.notifications.dao.model.Subscription;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public interface SubscriptionDAO {

    void addSubscription(Subscription subscription);

    Optional<Subscription> getSubscriptionById(String subscriptionId, String orgId);

    boolean updateSubscriptionStatus(String subscriptionId, String orgId, String status);

    boolean updateSubscriptionStatus(String subscriptionId, String orgId, String expectedStatus, String newStatus);

    boolean deleteSubscriptionAtomic(String subscriptionId, String orgId, String expectedStatus);

    PaginatedDAOResult<Subscription> listSubscriptions(String orgId, String status, String purposes, String search,
            int limit, int offset, String sort);

    List<Subscription> getSubscriptionsByOrgAndTopic(Connection conn, String orgId, String topicId);

    default List<Subscription> getSubscriptionsByOrgAndTopic(String orgId, String topicId) {
        try (Connection conn = JDBCPersistenceManager.getConnection()) {
            return getSubscriptionsByOrgAndTopic(conn, orgId, topicId);
        } catch (SQLException e) {
            throw new EventNotificationDataAccessException(
                    String.format(EventNotificationCommonConstants.ERROR_GETTING_SUBSCRIPTIONS_BY_ORG_AND_TOPIC, orgId, topicId), e);
        }
    }

    List<Subscription> getSubscriptionsByOrgAndTopic(String orgId, String topicId, String status);

    /**
     * Returns all subscriptions for a topic that are in a live state
     * (active, pending, stale) — i.e. all except deleted. Used for
     * duplicate-and-conflict checking on subscription creation.
     */
    List<Subscription> getLiveSubscriptionsByOrgAndTopic(Connection conn, String orgId, String topicId);

    default List<Subscription> getLiveSubscriptionsByOrgAndTopic(String orgId, String topicId) {
        try (Connection conn = JDBCPersistenceManager.getConnection()) {
            return getLiveSubscriptionsByOrgAndTopic(conn, orgId, topicId);
        } catch (SQLException e) {
            throw new EventNotificationDataAccessException(
                    String.format(EventNotificationCommonConstants.ERROR_GETTING_SUBSCRIPTIONS_BY_ORG_AND_TOPIC, orgId, topicId), e);
        }
    }

    long countActiveSubscriptionsForTopic(String orgId, String topicId);

    List<String> getPurposesBySubscriptionId(String subscriptionId, String orgId);

    Map<String, List<String>> getPurposesBySubscriptionIds(List<String> subscriptionIds);

    boolean hasPendingOrInFlightDeliveries(String subscriptionId, String orgId);

    List<Subscription> getPendingSubscriptionsForRecovery(Timestamp updatedBefore, int limit);
}
