/*
 * Copyright (c) 2026, WSO2 LLC. (https://www.wso2.com).
 *
 * WSO2 LLC. licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License.
 */
package org.wso2.dpdp.accelerator.common.persistence;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.wso2.dpdp.accelerator.common.constant.DPDPCommonConstants;
import org.wso2.dpdp.accelerator.common.exception.DPDPCommonRuntimeException;

import javax.naming.InitialContext;
import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;

/**
 * Resolves the shared DPDP datasource and provides JDBC connections to accelerator modules.
 */
public final class JDBCPersistenceManager implements TransactionManager {

    private static final Log LOG = LogFactory.getLog(JDBCPersistenceManager.class);
    private static volatile DataSource dataSource;
    private static final JDBCPersistenceManager INSTANCE = new JDBCPersistenceManager();

    private JDBCPersistenceManager() {
    }

    public static JDBCPersistenceManager getInstance() {

        return INSTANCE;
    }

    public static Connection getConnection() throws SQLException {

        return getDataSource().getConnection();
    }

    /**
     * Executes a non-transactional operation with a centrally managed connection.
     * The callback must not close the supplied connection.
     */
    public <T> T executeWithConnection(ConnectionCallback<T> callback) {

        if (callback == null) {
            throw new IllegalArgumentException("Connection callback cannot be null.");
        }
        Connection connection = null;
        try {
            connection = getConnection();
            return callback.execute(connection);
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new DPDPCommonRuntimeException("DPDP connection operation failed.", e);
        } finally {
            if (connection != null) {
                try {
                    connection.close();
                } catch (SQLException e) {
                    LOG.debug("Unable to close the DPDP connection.", e);
                }
            }
        }
    }

    /**
     * Executes an operation in a single transaction and owns the complete JDBC lifecycle.
     * DAO methods invoked by the callback must use the supplied connection and must not
     * commit, roll back, or close it.
     *
     * @param callback operation to execute
     * @param <T> result type
     * @return callback result
     */
    @Override
    public <T> T executeInTransaction(TransactionCallback<T> callback) {

        if (callback == null) {
            throw new IllegalArgumentException("Transaction callback cannot be null.");
        }
        Connection connection = null;
        try {
            connection = getConnection();
            boolean originalAutoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);
            try {
                T result = callback.execute(connection);
                connection.commit();
                return result;
            } catch (RuntimeException e) {
                rollback(connection, e);
                throw e;
            } catch (Exception e) {
                rollback(connection, e);
                throw new DPDPCommonRuntimeException("DPDP transaction operation failed.", e);
            } finally {
                try {
                    connection.setAutoCommit(originalAutoCommit);
                } catch (SQLException e) {
                    LOG.debug("Unable to restore the JDBC auto-commit state.", e);
                }
            }
        } catch (SQLException e) {
            throw new DPDPCommonRuntimeException("Unable to execute the DPDP transaction.", e);
        } finally {
            if (connection != null) {
                try {
                    connection.close();
                } catch (SQLException e) {
                    LOG.debug("Unable to close the DPDP transaction connection.", e);
                }
            }
        }
    }

    private static void rollback(Connection connection, Throwable original) {

        try {
            connection.rollback();
        } catch (SQLException rollbackFailure) {
            original.addSuppressed(rollbackFailure);
        }
    }

    private static DataSource getDataSource() throws SQLException {

        if (dataSource == null) {
            synchronized (JDBCPersistenceManager.class) {
                if (dataSource == null) {
                    try {
                        InitialContext context = new InitialContext();
                        try {
                            dataSource = (DataSource) context.lookup(
                                    DPDPCommonConstants.JDBC_DPDP_DATASOURCE_NAME);
                        } catch (Exception e) {
                            dataSource = (DataSource) context.lookup(
                                    DPDPCommonConstants.JDBC_DPDP_JNDI_ENV_NAME);
                        }
                        LOG.debug("Resolved shared DPDP datasource: "
                                + DPDPCommonConstants.JDBC_DPDP_DATASOURCE_NAME);
                    } catch (Exception e) {
                        throw new SQLException("Unable to resolve shared DPDP datasource ["
                                + DPDPCommonConstants.JDBC_DPDP_DATASOURCE_NAME + "]", e);
                    }
                }
            }
        }
        return dataSource;
    }
}
