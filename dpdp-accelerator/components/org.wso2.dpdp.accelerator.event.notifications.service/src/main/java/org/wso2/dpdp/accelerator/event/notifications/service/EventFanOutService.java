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

package org.wso2.dpdp.accelerator.event.notifications.service;

import org.wso2.dpdp.accelerator.event.notifications.dao.model.Event;

import java.sql.Connection;
import java.util.List;

/**
 * Distributes a persisted event to every active subscription that matches by
 * purpose filter and group, creating one delivery row per match.
 *
 * <p>Webhook subscriptions get a {@code WEBHOOK_DELIVERY} row that the
 * existing {@link org.wso2.dpdp.accelerator.event.notifications.service.dispatch.WebhookDeliveryWorker}
 * picks up on its next tick. Poll subscriptions are recorded separately
 * .</p>
 */
public interface EventFanOutService {

    /**
     * @param event the persisted event row, including its generated {@code eventId}
     *              and the {@code topicId}/{@code orgId}/{@code groupId} used to find
     *              candidate subscriptions.
     * @param eventPurposes the purpose tags associated with the event, used to
     *                      evaluate each subscription's purpose filter. May be null
     *                      or empty for an event with no purpose metadata.
     */
    void fanOutEvent(Connection conn, Event event, List<String> eventPurposes);
}
