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

import org.h2.jdbcx.JdbcDataSource;

import java.lang.reflect.Field;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Points JDBCPersistenceManager (org.wso2.dpdp.accelerator.common) at a fresh in-memory H2
 * database for a DAO test class - the same reflection-based DataSource injection
 * event-notifications' own DAO tests use (see e.g. EventAndAckDAOImplTest), since
 * JDBCPersistenceManager only resolves its DataSource via JNDI and has no config-based fallback
 * for a plain JUnit JVM the way the old DBUtil did.
 */
public final class H2TestDbSupport {

    private H2TestDbSupport() {
    }

    /** Points JDBCPersistenceManager at a brand-new named in-memory H2 database and runs the given DDL against it. */
    public static void setUpDatabase(String dbName, String... ddl) throws SQLException {
        String url = "jdbc:h2:mem:" + dbName + ";DB_CLOSE_DELAY=-1";

        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL(url);
        dataSource.setUser("sa");
        dataSource.setPassword("");
        setManagerDataSource(dataSource);

        try (Connection conn = DriverManager.getConnection(url, "sa", "");
                Statement stmt = conn.createStatement()) {
            for (String statement : ddl) {
                stmt.execute(statement);
            }
        }
    }

    /** Clears the DataSource set by {@link #setUpDatabase} so it doesn't leak into the next test class. */
    public static void tearDownDatabase() {
        setManagerDataSource(null);
    }

    private static void setManagerDataSource(Object dataSource) {
        try {
            Field field = Class.forName("org.wso2.dpdp.accelerator.common.persistence.JDBCPersistenceManager")
                    .getDeclaredField("dataSource");
            field.setAccessible(true);
            field.set(null, dataSource);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Unable to set JDBCPersistenceManager's test DataSource.", e);
        }
    }
}
