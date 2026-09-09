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

import org.wso2.dpdp.accelerator.common.util.DatabaseUtils;

import java.sql.Connection;
import java.sql.SQLException;

/**
 * Stands in for the service layer in DAO tests: the DAOs take a {@link Connection} and never open
 * one themselves, so something has to own the transaction. Commits reads as well as writes, for
 * the reason spelled out on {@code ComplaintDAO}.
 */
public final class TestTransaction {

    /** A DAO call. Separate from {@link java.util.function.Function} only to allow SQLException. */
    public interface Work<T> {

        T run(Connection conn) throws SQLException;
    }

    private TestTransaction() {
    }

    public static <T> T run(Work<T> work) {
        Connection conn = DatabaseUtils.getDBConnection();
        try {
            T result = work.run(conn);
            DatabaseUtils.commitTransaction(conn);
            return result;
        } catch (RuntimeException e) {
            DatabaseUtils.rollbackTransaction(conn);
            throw e;
        } catch (SQLException e) {
            DatabaseUtils.rollbackTransaction(conn);
            throw new IllegalStateException("A DAO call failed in a test transaction.", e);
        } finally {
            DatabaseUtils.closeConnection(conn);
        }
    }
}
