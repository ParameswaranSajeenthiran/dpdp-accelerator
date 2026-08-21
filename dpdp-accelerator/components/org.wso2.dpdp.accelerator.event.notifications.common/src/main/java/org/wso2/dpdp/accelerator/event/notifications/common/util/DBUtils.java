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
import java.sql.SQLException;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * Common Database Connection utility for datasource resolution.
 */
public class DBUtils {

    private static final Log LOG = LogFactory.getLog(DBUtils.class);
    private static volatile DataSource dataSource;
    private static volatile long lastJndiLookupFailedTime = 0L;
    private static final long JNDI_LOOKUP_COOLDOWN_MS = 10000L;

    private DBUtils() {
    }

    public static Connection getConnection() throws SQLException {
        DataSource ds = getDataSource();
        if (ds != null) {
            return ds.getConnection();
        }
        throw new SQLException("IS datasource ["
                + EventNotificationCommonConstants.JDBC_DPDP_DATASOURCE_NAME + "] is unavailable");
    }

    private static DataSource getDataSource() {
        if (dataSource == null) {
            long now = System.currentTimeMillis();
            if (now - lastJndiLookupFailedTime < JNDI_LOOKUP_COOLDOWN_MS) {
                return null;
            }
            synchronized (DBUtils.class) {
                if (dataSource == null) {
                    try {
                        InitialContext ctx = new InitialContext();
                        try {
                            dataSource = (DataSource) ctx.lookup(EventNotificationCommonConstants.JDBC_DPDP_DATASOURCE_NAME);
                        } catch (Exception e) {
                            dataSource = (DataSource) ctx.lookup(EventNotificationCommonConstants.JDBC_DPDP_JNDI_ENV_NAME);
                        }
                    } catch (Exception e) {
                        lastJndiLookupFailedTime = System.currentTimeMillis();
                        if (LOG.isDebugEnabled()) {
                            LOG.debug("JNDI lookup failed for [" + EventNotificationCommonConstants.JDBC_DPDP_DATASOURCE_NAME + "], falling back to direct connection.", e);
                        }
                    }
                    if (dataSource == null) {
                        lastJndiLookupFailedTime = System.currentTimeMillis();
                    }
                }
            }
        }
        return dataSource;
    }
}
