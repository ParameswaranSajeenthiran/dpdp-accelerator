/*
 * Copyright (c) 2026, WSO2 LLC. (https://www.wso2.com).
 *
 * WSO2 LLC. licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.wso2.dpdp.accelerator.common.util;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.wso2.dpdp.accelerator.common.persistence.JDBCPersistenceManager;

import java.sql.Connection;
import java.sql.SQLException;

/**
 * Thin static facade over {@link JDBCPersistenceManager}, mirroring the Financial Services
 * accelerator's own {@code DatabaseUtils}, so DAO/service code depends on this rather than the
 * persistence manager directly.
 */
public final class DatabaseUtils {

    private static final Log LOG = LogFactory.getLog(DatabaseUtils.class);

    private DatabaseUtils() {

    }

    public static Connection getDBConnection() {

        return JDBCPersistenceManager.getInstance().getDBConnection();
    }

    public static void commitTransaction(Connection connection) {

        JDBCPersistenceManager.getInstance().commitTransaction(connection);
    }

    public static void rollbackTransaction(Connection connection) {

        JDBCPersistenceManager.getInstance().rollbackTransaction(connection);
    }

    public static void closeConnection(Connection connection) {

        if (connection == null) {
            return;
        }
        try {
            connection.close();
        } catch (SQLException e) {
            LOG.error("Error while closing a DPDP DB connection.", e);
        }
    }
}
