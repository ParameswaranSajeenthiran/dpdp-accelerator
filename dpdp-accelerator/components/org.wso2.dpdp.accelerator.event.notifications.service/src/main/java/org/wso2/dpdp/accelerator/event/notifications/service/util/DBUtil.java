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

package org.wso2.dpdp.accelerator.event.notifications.service.util;

import java.util.logging.Level;
import java.util.logging.Logger;

import javax.naming.InitialContext;
import javax.naming.NamingException;
import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * JDBC connection utility supporting JNDI DataSource and standalone JDBC
 * fallback.
 */

public final class DBUtil {

    private static final Logger LOGGER = Logger.getLogger(DBUtil.class.getName());
    private static final String[] JNDI_DATASOURCE_NAMES = {
        "jdbc/ENFDB",
        "ENFDB",
        "jdbc/WSO2SHARED_DB",
        "WSO2SHARED_DB",
        "jdbc/WSO2SharedDB",
        "WSO2SharedDB",
        "jdbc/WSO2IDENTITY_DB",
        "WSO2IDENTITY_DB",
        "jdbc/WSO2IdentityDB",
        "WSO2IdentityDB",
        "jdbc/WSO2_CARBON_DB",
        "WSO2_CARBON_DB"
    };

    private static final String ENV_JDBC_URL = "ENF_DB_URL";
    private static final String ENV_JDBC_USER = "ENF_DB_USER";
    private static final String ENV_JDBC_PASSWORD = "ENF_DB_PASS";

    private static final String DEFAULT_H2_URL = "jdbc:h2:./repository/database/WSO2SHARED_DB;DB_CLOSE_ON_EXIT=FALSE;LOCK_TIMEOUT=60000";
    private static final String DEFAULT_H2_USER = "wso2carbon";
    private static final String DEFAULT_H2_PASS = "wso2carbon";

    private static volatile DataSource dataSource;
    private static volatile boolean jdbcFallbackConfigResolved;
    private static volatile String jdbcUrl;
    private static volatile String jdbcUser;
    private static volatile String jdbcPassword;

    private static volatile boolean tablesInitialized = false;

    public static Connection getConnection() throws SQLException {
        initializeDataSource();

        Connection conn;
        if (dataSource != null) {
            conn = dataSource.getConnection();
        } else {
            resolveJdbcFallbackConfig();
            conn = DriverManager.getConnection(jdbcUrl, jdbcUser, jdbcPassword);
        }

        checkAndCreateTables(conn);

        return conn;
    }

    private static void checkAndCreateTables(Connection conn) {
        if (tablesInitialized) {
            return;
        }

        synchronized (DBUtil.class) {
            if (tablesInitialized) {
                return;
            }

            try (Statement stmt = conn.createStatement()) {
                stmt.executeQuery("SELECT 1 FROM TOPIC WHERE 1=0");
                tablesInitialized = true;
            } catch (SQLException e) {
                LOGGER.log(Level.INFO, "Event Notification tables not found. Auto-creating schema on database...");
                createDatabaseTables(conn);
                tablesInitialized = true;
            }
        }
    }

    private static void createDatabaseTables(Connection conn) {
        String[] ddlStatements = {
            "CREATE TABLE IF NOT EXISTS TOPIC (TOPIC_ID VARCHAR(64) NOT NULL, ORG_ID VARCHAR(128) NOT NULL, NAME VARCHAR(225) NOT NULL, DESCRIPTION VARCHAR(255) DEFAULT NULL, STATUS VARCHAR(32) DEFAULT 'ACTIVE' NOT NULL, PRIMARY KEY (TOPIC_ID), CONSTRAINT UQ_TOPIC_ORG_NAME UNIQUE (ORG_ID, NAME))",
            "CREATE TABLE IF NOT EXISTS EVENT (EVENT_ID VARCHAR(64) NOT NULL, ORG_ID VARCHAR(128) NOT NULL, GROUP_ID VARCHAR(128) NOT NULL, TOPIC_ID VARCHAR(64) NOT NULL, PAYLOAD CLOB NOT NULL, CREATED_AT TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL, PRIMARY KEY (EVENT_ID), CONSTRAINT FK_E_TOPIC FOREIGN KEY (TOPIC_ID) REFERENCES TOPIC (TOPIC_ID))",
            "CREATE TABLE IF NOT EXISTS EVENT_PURPOSE (EVENT_ID VARCHAR(64) NOT NULL, PURPOSE_NAME VARCHAR(128) NOT NULL, PRIMARY KEY (EVENT_ID, PURPOSE_NAME), CONSTRAINT FK_EVENT_PURPOSES_EVENT FOREIGN KEY (EVENT_ID) REFERENCES EVENT (EVENT_ID) ON DELETE CASCADE)",
            "CREATE TABLE IF NOT EXISTS SUBSCRIPTION (SUBSCRIPTION_ID VARCHAR(64) NOT NULL, ORG_ID VARCHAR(128) NOT NULL, GROUP_ID VARCHAR(128) NOT NULL, TOPIC_ID VARCHAR(64) NOT NULL, STATUS VARCHAR(32) DEFAULT 'ACTIVE' NOT NULL, PURPOSE_FILTER_MODE VARCHAR(32) NOT NULL, CALLBACK_URL VARCHAR(512) DEFAULT NULL, SHARED_SECRET VARCHAR(512) DEFAULT NULL, CREATED_AT TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL, UPDATED_AT TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL, DELIVERY_MODE VARCHAR(32) NOT NULL, PRIMARY KEY (SUBSCRIPTION_ID), CONSTRAINT FK_S_TOPIC FOREIGN KEY (TOPIC_ID) REFERENCES TOPIC (TOPIC_ID))",
            "CREATE TABLE IF NOT EXISTS SUBSCRIPTION_PURPOSE (SUBSCRIPTION_ID VARCHAR(64) NOT NULL, PURPOSE_NAME VARCHAR(128) NOT NULL, PRIMARY KEY (SUBSCRIPTION_ID, PURPOSE_NAME), CONSTRAINT FK_SUBSCRIPTION_PURPOSES_SUBSCRIPTION FOREIGN KEY (SUBSCRIPTION_ID) REFERENCES SUBSCRIPTION (SUBSCRIPTION_ID) ON DELETE CASCADE)",
            "CREATE TABLE IF NOT EXISTS WEBHOOK_DELIVERY (DELIVERY_ID VARCHAR(64) NOT NULL, SUBSCRIPTION_ID VARCHAR(64) NOT NULL, EVENT_ID VARCHAR(64) NOT NULL, STATUS VARCHAR(32) DEFAULT 'PENDING' NOT NULL, ATTEMPT_COUNT INT DEFAULT 0 NOT NULL, NEXT_RETRY_AT TIMESTAMP NULL DEFAULT NULL, CREATED_AT TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL, UPDATED_AT TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL, DELIVERED_AT TIMESTAMP NULL DEFAULT NULL, PRIMARY KEY (DELIVERY_ID))",
            "CREATE TABLE IF NOT EXISTS WEBHOOK_DELIVERY_ACK (ACK_ID VARCHAR(64) NOT NULL, DELIVERY_ID VARCHAR(64) NOT NULL, STATUS VARCHAR(32) NOT NULL, RECEIVED_AT TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL, PRIMARY KEY (ACK_ID))",
            "CREATE TABLE IF NOT EXISTS WEBHOOK_DELIVERY_AUDIT (AUDIT_ID VARCHAR(64) NOT NULL, DELIVERY_ID VARCHAR(64) NOT NULL, ATTEMPT_NUMBER INT NOT NULL, RESPONSE_STATUS_CODE INT DEFAULT NULL, ERROR_MESSAGE VARCHAR(512) DEFAULT NULL, CREATED_AT TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL, PRIMARY KEY (AUDIT_ID))",
            "CREATE TABLE IF NOT EXISTS POLL_DELIVERY (DELIVERY_ID VARCHAR(64) NOT NULL, SUBSCRIPTION_ID VARCHAR(64) NOT NULL, EVENT_ID VARCHAR(64) NOT NULL, STATUS VARCHAR(32) DEFAULT 'PENDING' NOT NULL, CREATED_AT TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL, PRIMARY KEY (DELIVERY_ID))"
        };

        try (Statement stmt = conn.createStatement()) {
            for (String sql : ddlStatements) {
                stmt.execute(sql);
            }
            LOGGER.log(Level.INFO, "Event Notification database tables created successfully.");
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Failed to create Event Notification database tables", e);
        }
    }

    private static void initializeDataSource() {
        if (dataSource != null) {
            return;
        }

        synchronized (DBUtil.class) {
            if (dataSource != null) {
                return;
            }

            InitialContext context;
            try {
                context = new InitialContext();
            } catch (NamingException e) {
                LOGGER.log(Level.FINE, "Failed to create InitialContext: " + e.getMessage());
                return;
            }

            for (String jndiName : JNDI_DATASOURCE_NAMES) {
                try {
                    DataSource ds = (DataSource) context.lookup(jndiName);
                    if (ds != null) {
                        dataSource = ds;
                        LOGGER.log(Level.INFO, "Initialized JNDI datasource: " + jndiName);
                        return;
                    }
                } catch (NamingException e) {
                    LOGGER.log(Level.FINE, "JNDI datasource [" + jndiName + "] unavailable.");
                } catch (RuntimeException e) {
                    LOGGER.log(Level.FINE, "JNDI resource [" + jndiName + "] invalid: " + e.getMessage());
                }
            }
        }
    }

    /**
     * Resolves standalone JDBC fallback config lazily (not at class-load time), so
     * that system properties or environment variables set after this class is first
     * loaded are still honored. Defaults to embedded WSO2SHARED_DB if unset.
     */

    private static void resolveJdbcFallbackConfig() {
        if (jdbcFallbackConfigResolved) {
            return;
        }

        synchronized (DBUtil.class) {
            if (jdbcFallbackConfigResolved) {
                return;
            }

            jdbcUrl = getConfig(ENV_JDBC_URL, DEFAULT_H2_URL);
            jdbcUser = getConfig(ENV_JDBC_USER, DEFAULT_H2_USER);
            jdbcPassword = getConfig(ENV_JDBC_PASSWORD, DEFAULT_H2_PASS);

            jdbcFallbackConfigResolved = true;
        }
    }

    /**
     * Resolves a config value, preferring a JVM system property over an environment
     * variable of the same name, falling back to {@code defaultValue} if neither is
     * set.
     */

    private static String getConfig(String key, String defaultValue) {
        String systemProperty = System.getProperty(key);
        if (systemProperty != null && !systemProperty.isBlank()) {
            return systemProperty;
        }

        String environmentVariable = System.getenv(key);
        if (environmentVariable != null && !environmentVariable.isBlank()) {
            return environmentVariable;
        }

        return defaultValue;
    }

    public static void closeAll(
            Connection conn,
            Statement stmt,
            ResultSet rs) {

        if (rs != null) {
            try {
                rs.close();
            } catch (SQLException e) {
                LOGGER.log(Level.WARNING, "Error closing ResultSet", e);
            }
        }

        if (stmt != null) {
            try {
                stmt.close();
            } catch (SQLException e) {
                LOGGER.log(Level.WARNING, "Error closing Statement", e);
            }
        }

        if (conn != null) {
            try {
                conn.close();
            } catch (SQLException e) {
                LOGGER.log(Level.WARNING, "Error closing Connection", e);
            }
        }
    }
}
