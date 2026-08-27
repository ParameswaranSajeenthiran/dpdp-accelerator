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

package org.wso2.dpdp.accelerator.complaint.mgt.dao.util;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.wso2.dpdp.common.config.ConfigProvider;

import javax.naming.InitialContext;
import javax.naming.NamingException;
import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public class DBUtil {

    private static final Log LOG = LogFactory.getLog(DBUtil.class);
    private static DataSource dataSource = null;

    private DBUtil() {
    }

    public static Connection getConnection() throws SQLException {
        if (dataSource != null) {
            return dataSource.getConnection();
        }
        try {
            InitialContext ctx = new InitialContext();
            dataSource = (DataSource) ctx.lookup("java:comp/env/jdbc/WSO2DPDP_DB");
            return dataSource.getConnection();
        } catch (NamingException e) {
//            When JNDI isn't available (unit tests, or the WAR run standalone), it falls back to reading the datasource URL/credentials straight from deployment.toml (then CO_DB_* system
//            properties below that) — and has to manually un-escape &amp; back to & in the URL, since Carbon copies the TOML value verbatim into master-datasources.xml where XML parsing
//            does that unescaping for it, but reading the TOML directly here bypasses that step.
            String dbUrl = ConfigProvider.getString("datasource.WSO2DPDP_DB.url",
                    System.getProperty("CO_DB_URL",
                            "jdbc:mysql://localhost:3306/complaint_db?useSSL=false&allowPublicKeyRetrieval=true"))
                    .replace("&amp;", "&");
            String dbUser = ConfigProvider.getString("datasource.WSO2DPDP_DB.username",
                    System.getProperty("CO_DB_USER", "root"));
            String dbPass = ConfigProvider.getString("datasource.WSO2DPDP_DB.password",
                    System.getProperty("CO_DB_PASS", "root"));
            return DriverManager.getConnection(dbUrl, dbUser, dbPass);
        }
    }

    /** Unit of work run against a single connection inside {@link #executeInTransaction}. */
    @FunctionalInterface
    public interface TransactionalWork {
        void execute(Connection conn) throws SQLException;
    }

    /**
     * Runs the given work against a single connection with auto-commit disabled, committing on success and
     * rolling back if the work throws. Lets callers group multiple DAO writes (e.g. a status update and its
     * audit event) into one atomic transaction.
     *
     * <p>Catches {@link RuntimeException} as well as {@link SQLException}: every DAO write method in this
     * module wraps its {@code SQLException} into the unchecked {@link
     * org.wso2.dpdp.accelerator.complaint.mgt.dao.exception.ComplaintDAOException} before it can propagate this
     * far, so a {@code catch (SQLException)} alone would silently skip the rollback on the exact failures this
     * method exists to guard against.
     */
    public static void executeInTransaction(TransactionalWork work) throws SQLException {
        try (Connection conn = getConnection()) {
            conn.setAutoCommit(false);
            try {
                work.execute(conn);
                conn.commit();
            } catch (SQLException | RuntimeException e) {
                try {
                    conn.rollback();
                } catch (SQLException rollbackEx) {
                    e.addSuppressed(rollbackEx);
                }
                throw e;
            }
        }
    }

    public static void closeAll(Connection conn, PreparedStatement ps, ResultSet rs) {
        if (rs != null) {
            try {
                rs.close();
            } catch (SQLException e) {
                LOG.warn("Error closing ResultSet", e);
            }
        }
        if (ps != null) {
            try {
                ps.close();
            } catch (SQLException e) {
                LOG.warn("Error closing PreparedStatement", e);
            }
        }
        if (conn != null) {
            try {
                conn.close();
            } catch (SQLException e) {
                LOG.warn("Error closing Connection", e);
            }
        }
    }
}
