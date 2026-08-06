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

package org.wso2.dpdp.accelerator.event.notifications.service.dao.mapper;

import org.wso2.dpdp.accelerator.event.notifications.service.dao.model.Subscription;

import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * Maps JDBC {@link ResultSet} rows to {@link Subscription} domain models.
 */
public final class SubscriptionRowMapper {

    private SubscriptionRowMapper() {
    }

    public static Subscription map(ResultSet rs) throws SQLException {
        Subscription sub = new Subscription();
        sub.setSubscriptionId(rs.getString("SUBSCRIPTION_ID"));
        sub.setOrgId(rs.getString("ORG_ID"));
        sub.setGroupId(rs.getString("GROUP_ID"));
        sub.setTopicId(rs.getString("TOPIC_ID"));
        sub.setStatus(rs.getString("STATUS"));
        sub.setPurposeFilterMode(rs.getString("PURPOSE_FILTER_MODE"));
        sub.setCallbackUrl(rs.getString("CALLBACK_URL"));
        sub.setSharedSecret(rs.getString("SHARED_SECRET"));
        sub.setCreatedAt(rs.getTimestamp("CREATED_AT"));
        sub.setUpdatedAt(rs.getTimestamp("UPDATED_AT"));
        sub.setDeliveryMode(rs.getString("DELIVERY_MODE"));
        return sub;
    }
}
