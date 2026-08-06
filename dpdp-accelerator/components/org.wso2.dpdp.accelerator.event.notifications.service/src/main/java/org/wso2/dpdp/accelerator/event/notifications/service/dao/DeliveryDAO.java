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

package org.wso2.dpdp.accelerator.event.notifications.service.dao;

import org.wso2.dpdp.accelerator.event.notifications.service.dao.model.WebhookDelivery;
import org.wso2.dpdp.accelerator.event.notifications.service.dao.model.WebhookDeliveryAudit;
import org.wso2.dpdp.accelerator.event.notifications.service.dao.model.PollDelivery;
import org.wso2.dpdp.accelerator.event.notifications.service.dao.model.SubscriptionDeliverySummary;

import java.util.List;
import java.util.Optional;

public interface DeliveryDAO {
    boolean addWebhookDelivery(WebhookDelivery delivery);
    Optional<WebhookDelivery> getWebhookDeliveryById(String deliveryId, String orgId);
    List<WebhookDelivery> getPendingWebhookDeliveries(int limit);
    boolean updateWebhookDeliveryStatus(WebhookDelivery delivery);
    
    boolean addWebhookDeliveryAudit(WebhookDeliveryAudit audit);
    List<WebhookDeliveryAudit> getWebhookDeliveryAudits(String deliveryId);

    boolean addPollDelivery(PollDelivery delivery);
    Optional<PollDelivery> getPollDeliveryById(String deliveryId, String orgId);
    List<PollDelivery> getPendingPollDeliveries(String orgId, String groupId, int limit);
    boolean updatePollDeliveryStatus(String deliveryId, String status);
    boolean isDeliveryExistsForOrg(String deliveryId, String orgId);

    List<SubscriptionDeliverySummary> listSubscriptionDeliveries(String subscriptionId, int limit, int offset, int[] totalOut);
    Optional<SubscriptionDeliverySummary> getSubscriptionDeliveryById(String subscriptionId, String deliveryId);

    List<SubscriptionDeliverySummary> listOrgDeliveries(String orgId, String statusFilter, String subscriptionIdFilter, String purposesFilter, String search, int limit, int offset, int[] totalOut);
    Optional<SubscriptionDeliverySummary> getOrgDeliveryById(String orgId, String deliveryId);
}
