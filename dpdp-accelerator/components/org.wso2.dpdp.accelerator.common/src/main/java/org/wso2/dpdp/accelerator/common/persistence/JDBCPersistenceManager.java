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

package org.wso2.dpdp.accelerator.common.persistence;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.wso2.dpdp.accelerator.common.config.DPDPConfigParser;
import org.wso2.dpdp.accelerator.common.exception.DPDPCommonRuntimeException;

import java.sql.Connection;
import java.sql.SQLException;

import javax.naming.Context;
import javax.naming.InitialContext;
import javax.naming.NamingException;
import javax.sql.DataSource;

/**
 * Looks up the JNDI datasource named in {@code dpdp-accelerator.xml}'s {@code ConsentHistory}
 * config and hands out connections from it. Mirrors the Financial Services accelerator's own
 * {@code JDBCPersistenceManager} exactly - double-checked-locking singleton, lazy datasource
 * lookup, autocommit off on every connection handed out.
 */
public final class JDBCPersistenceManager {

    private static final Log LOG = LogFactory.getLog(JDBCPersistenceManager.class);
    private static volatile JDBCPersistenceManager instance;
    private static volatile DataSource dataSource;

    private JDBCPersistenceManager() {

        initDataSource();
    }

    public static JDBCPersistenceManager getInstance() {

        if (instance == null) {
            synchronized (JDBCPersistenceManager.class) {
                if (instance == null) {
                    instance = new JDBCPersistenceManager();
                }
            }
        }
        return instance;
    }

    private void initDataSource() {

        if (dataSource != null) {
            return;
        }
        synchronized (JDBCPersistenceManager.class) {
            if (dataSource != null) {
                return;
            }
            String dataSourceName = DPDPConfigParser.getInstance().getConsentHistoryDataSourceName();
            try {
                Context context = new InitialContext();
                dataSource = (DataSource) context.lookup(dataSourceName);
                LOG.debug("Looked up the DPDP consent history datasource: " + dataSourceName);
            } catch (NamingException e) {
                throw new DPDPCommonRuntimeException("Could not look up the DPDP consent history datasource: "
                        + dataSourceName, e);
            }
        }
    }

    public Connection getDBConnection() {

        try {
            Connection connection = dataSource.getConnection();
            connection.setAutoCommit(false);
            return connection;
        } catch (SQLException e) {
            throw new DPDPCommonRuntimeException("Error while obtaining a DPDP consent history DB connection.", e);
        }
    }

    public void commitTransaction(Connection connection) {

        try {
            connection.commit();
        } catch (SQLException e) {
            LOG.error("Error while committing the DPDP consent history transaction.", e);
        }
    }

    public void rollbackTransaction(Connection connection) {

        try {
            connection.rollback();
        } catch (SQLException e) {
            LOG.error("Error while rolling back the DPDP consent history transaction.", e);
        }
    }
}
