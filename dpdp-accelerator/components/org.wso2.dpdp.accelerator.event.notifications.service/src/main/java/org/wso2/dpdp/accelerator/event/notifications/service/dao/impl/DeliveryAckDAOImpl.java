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

package org.wso2.dpdp.accelerator.event.notifications.service.dao.impl;

import org.wso2.dpdp.accelerator.event.notifications.service.dao.AbstractDAO;
import org.wso2.dpdp.accelerator.event.notifications.service.dao.DeliveryAckDAO;
import org.wso2.dpdp.accelerator.event.notifications.service.dao.model.WebhookDeliveryAck;
import org.wso2.dpdp.accelerator.event.notifications.service.exception.DataAccessException;
import org.wso2.dpdp.accelerator.event.notifications.service.queries.DeliveryQueries;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;

/**
 * JDBC implementation of {@link DeliveryAckDAO} for managing delivery acknowledgements.
 */
public class DeliveryAckDAOImpl extends AbstractDAO implements DeliveryAckDAO {

    @Override
    public boolean addDeliveryAck(WebhookDeliveryAck ack) {
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(DeliveryQueries.ADD_WEBHOOK_DELIVERY_ACK)) {
            ps.setString(1, ack.getAckId());
            ps.setString(2, ack.getDeliveryId());
            ps.setTimestamp(3, ack.getCompletedAt() != null ? ack.getCompletedAt() : new java.sql.Timestamp(System.currentTimeMillis()));
            ps.setString(4, ack.getCompletionStatus());
            ps.setString(5, ack.getCompletionEvidence());
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            throw new DataAccessException("Error adding delivery ACK for delivery [" + (ack != null ? ack.getDeliveryId() : "null") + "]", e);
        }
    }

    @Override
    public Optional<WebhookDeliveryAck> getDeliveryAckByDeliveryId(String deliveryId) {
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(DeliveryQueries.GET_WEBHOOK_DELIVERY_ACK_BY_DELIVERY_ID)) {
            ps.setString(1, deliveryId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(new WebhookDeliveryAck(
                            rs.getString("ACK_ID"),
                            rs.getString("DELIVERY_ID"),
                            rs.getTimestamp("COMPLETED_AT"),
                            rs.getString("COMPLETION_STATUS"),
                            rs.getString("COMPLETION_EVIDENCE")
                    ));
                }
            }
            return Optional.empty();
        } catch (SQLException e) {
            throw new DataAccessException("Error getting delivery ACK for delivery [" + deliveryId + "]", e);
        }
    }
}
