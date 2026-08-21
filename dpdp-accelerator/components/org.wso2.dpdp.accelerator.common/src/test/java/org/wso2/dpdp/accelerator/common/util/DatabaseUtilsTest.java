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

import org.testng.annotations.Test;

import java.sql.Connection;
import java.sql.SQLException;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

public class DatabaseUtilsTest {

    @Test
    public void closeConnectionClosesANonNullConnection() throws SQLException {

        Connection connection = mock(Connection.class);
        DatabaseUtils.closeConnection(connection);
        verify(connection).close();
    }

    @Test
    public void closeConnectionToleratesNull() {

        DatabaseUtils.closeConnection(null);
    }

    @Test
    public void closeConnectionSwallowsSqlException() throws SQLException {

        Connection connection = mock(Connection.class);
        doThrow(new SQLException("boom")).when(connection).close();
        DatabaseUtils.closeConnection(connection);
    }
}
