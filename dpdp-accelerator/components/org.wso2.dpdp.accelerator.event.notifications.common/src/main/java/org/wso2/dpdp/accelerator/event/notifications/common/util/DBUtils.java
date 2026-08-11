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

package org.wso2.dpdp.accelerator.event.notifications.common.util;

import org.wso2.dpdp.accelerator.event.notifications.common.constants.EventNotificationCommonConstants;

import javax.naming.InitialContext;
import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Common Database Connection utility for datasource resolution.
 */
public class DBUtils {

    private static final Logger LOG = Logger.getLogger(DBUtils.class.getName());
    private static volatile DataSource dataSource;

    private DBUtils() {
    }

    public static Connection getConnection() throws SQLException {
        String envUrl = System.getenv(EventNotificationCommonConstants.ENV_JDBC_URL);
        String envUser = System.getenv(EventNotificationCommonConstants.ENV_JDBC_USER);
        String envPass = System.getenv(EventNotificationCommonConstants.ENV_JDBC_PASS);

        if (envUrl != null && !envUrl.trim().isEmpty()) {
            return DriverManager.getConnection(
                    envUrl.trim(),
                    envUser != null ? envUser.trim() : EventNotificationCommonConstants.DEFAULT_H2_USER,
                    envPass != null ? envPass.trim() : EventNotificationCommonConstants.DEFAULT_H2_PASS
            );
        }

        DataSource ds = getDataSource();
        if (ds != null) {
            return ds.getConnection();
        }

        return DriverManager.getConnection(
                EventNotificationCommonConstants.DEFAULT_H2_URL,
                EventNotificationCommonConstants.DEFAULT_H2_USER,
                EventNotificationCommonConstants.DEFAULT_H2_PASS
        );
    }

    private static DataSource getDataSource() {
        if (dataSource == null) {
            synchronized (DBUtils.class) {
                if (dataSource == null) {
                    try {
                        InitialContext ctx = new InitialContext();
                        try {
                            dataSource = (DataSource) ctx.lookup(EventNotificationCommonConstants.JDBC_EVENT_NOTIFICATION_DATASOURCE_NAME);
                        } catch (Exception e) {
                            try {
                                dataSource = (DataSource) ctx.lookup(EventNotificationCommonConstants.JDBC_EVENT_NOTIFICATION_JNDI_ENV_NAME);
                            } catch (Exception ex) {
                                dataSource = (DataSource) ctx.lookup(EventNotificationCommonConstants.JDBC_SHARED_DATASOURCE_NAME);
                            }
                        }
                    } catch (Exception e) {
                        LOG.log(Level.FINE, "JNDI lookup failed for [" + EventNotificationCommonConstants.JDBC_EVENT_NOTIFICATION_DATASOURCE_NAME + "], falling back to direct connection.", e);
                    }
                }
            }
        }
        return dataSource;
    }
}
