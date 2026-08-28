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
import org.wso2.dpdp.accelerator.event.notifications.dao.model.Event;

import java.sql.Connection;
import java.util.List;
import java.util.Optional;

public interface EventDAO {

    boolean addEvent(Connection conn, Event event);

    default boolean addEvent(Event event) {
        Connection conn = DatabaseUtils.getDBConnection();
        try {
            boolean result = addEvent(conn, event);
            DatabaseUtils.commitTransaction(conn);
            return result;
        } catch (RuntimeException e) {
            DatabaseUtils.rollbackTransaction(conn);
            throw e;
        } finally {
            DatabaseUtils.closeConnection(conn);
        }
    }

    Optional<Event> getEventById(String eventId, String orgId);

    void addEventPurposes(Connection conn, String eventId, List<String> purposes);

    default void addEventPurposes(String eventId, List<String> purposes) {
        Connection conn = DatabaseUtils.getDBConnection();
        try {
            addEventPurposes(conn, eventId, purposes);
            DatabaseUtils.commitTransaction(conn);
        } catch (RuntimeException e) {
            DatabaseUtils.rollbackTransaction(conn);
            throw e;
        } finally {
            DatabaseUtils.closeConnection(conn);
        }
    }

    List<String> getEventPurposes(String eventId);

    List<String> getEventPurposes(Connection connection, String eventId);

    boolean hasActiveEventsForTopic(String topicId);

    PaginatedDAOResult<Event> searchEvents(String orgId, String topic, String status, String groupId,
            String subscriptionId, String purposes, String search, int limit, int offset);

    default PaginatedDAOResult<Event> searchEvents(String orgId, String topic, String status, String groupId,
            String purposes, String search, int limit, int offset) {
        return searchEvents(orgId, topic, status, groupId, null, purposes, search, limit, offset);
    }

    default PaginatedDAOResult<Event> searchEvents(String orgId, String search, int limit, int offset) {
        return searchEvents(orgId, null, null, null, null, null, search, limit, offset);
    }
}
