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

import javax.naming.InitialContext;
import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;

/**
 * Resolves the shared DPDP datasource and provides JDBC connections to accelerator modules.
 */
public final class JDBCPersistenceManager {

    private static final Log LOG = LogFactory.getLog(JDBCPersistenceManager.class);
    private static volatile DataSource dataSource;

    private JDBCPersistenceManager() {
    }

    public static Connection getConnection() throws SQLException {

        return getDataSource().getConnection();
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
