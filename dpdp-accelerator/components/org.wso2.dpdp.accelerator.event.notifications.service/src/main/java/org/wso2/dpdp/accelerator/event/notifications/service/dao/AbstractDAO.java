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

import org.wso2.dpdp.accelerator.event.notifications.service.util.DBUtil;

import java.util.logging.Level;
import java.util.logging.Logger;

import java.sql.Connection;
import java.sql.SQLException;

/**
 * Abstract base class for DAOs. Provides shared Connection acquisition and transaction helper utilities.
 */

public abstract class AbstractDAO {

    private static final Logger LOG = Logger.getLogger(AbstractDAO.class.getName());

    protected Connection getConnection() throws SQLException {
        return DBUtil.getConnection();
    }

    protected void rollback(Connection conn) {
        if (conn != null) {
            try {
                conn.rollback();
            } catch (SQLException e) {
                LOG.log(Level.SEVERE, "Failed to rollback transaction", e);
            }
        }
    }

    protected void resetAutoCommit(Connection conn) {
        if (conn != null) {
            try {
                conn.setAutoCommit(true);
            } catch (SQLException e) {
                LOG.log(Level.SEVERE, "Failed to reset auto-commit status", e);
            }
        }
    }
}
