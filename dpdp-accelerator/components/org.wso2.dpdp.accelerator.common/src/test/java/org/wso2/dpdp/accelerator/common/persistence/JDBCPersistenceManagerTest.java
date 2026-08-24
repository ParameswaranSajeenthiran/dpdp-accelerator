/*
 * Copyright (c) 2026, WSO2 LLC. (https://www.wso2.com).
 *
 * WSO2 LLC. licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License.
 */
package org.wso2.dpdp.accelerator.common.persistence;

import org.mockito.Mockito;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.Test;
import org.wso2.dpdp.accelerator.common.util.DatabaseUtils;

import javax.sql.DataSource;
import java.lang.reflect.Field;
import java.sql.Connection;
import java.sql.SQLException;

import static org.testng.Assert.expectThrows;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

public class JDBCPersistenceManagerTest {

    @AfterMethod
    public void clearDataSource() throws Exception {
        setDataSource(null);
    }

    @Test
    public void getConnectionFailsWhenDatasourceIsUnavailable() {

        expectThrows(SQLException.class, JDBCPersistenceManager::getConnection);
    }

    @Test
    public void executeWithConnectionClosesAndReturnsCallbackValue() throws Exception {
        DataSource dataSource = Mockito.mock(DataSource.class);
        Connection connection = Mockito.mock(Connection.class);
        Mockito.when(dataSource.getConnection()).thenReturn(connection);
        setDataSource(dataSource);

        String value = JDBCPersistenceManager.getInstance().executeWithConnection(conn -> {
            assertEquals(conn, connection);
            return "ok";
        });

        assertEquals(value, "ok");
        Mockito.verify(connection).close();
    }

    @Test
    public void executeInTransactionCommitsAndRestoresAutoCommit() throws Exception {
        DataSource dataSource = Mockito.mock(DataSource.class);
        Connection connection = Mockito.mock(Connection.class);
        Mockito.when(dataSource.getConnection()).thenReturn(connection);
        Mockito.when(connection.getAutoCommit()).thenReturn(true);
        setDataSource(dataSource);

        assertTrue(JDBCPersistenceManager.getInstance().executeInTransaction(conn -> true));

        Mockito.verify(connection).setAutoCommit(false);
        Mockito.verify(connection).commit();
        Mockito.verify(connection).setAutoCommit(true);
        Mockito.verify(connection).close();
    }

    @Test
    public void executeInTransactionRollsBackRuntimeFailure() throws Exception {
        DataSource dataSource = Mockito.mock(DataSource.class);
        Connection connection = Mockito.mock(Connection.class);
        Mockito.when(dataSource.getConnection()).thenReturn(connection);
        Mockito.when(connection.getAutoCommit()).thenReturn(true);
        setDataSource(dataSource);

        expectThrows(IllegalStateException.class, () ->
                JDBCPersistenceManager.getInstance().executeInTransaction(conn -> {
                    throw new IllegalStateException("expected");
                }));

        Mockito.verify(connection).rollback();
        Mockito.verify(connection).setAutoCommit(true);
        Mockito.verify(connection).close();
    }

    @Test
    public void checkedCallbackFailureIsWrappedAndConnectionIsClosed() throws Exception {
        DataSource dataSource = Mockito.mock(DataSource.class);
        Connection connection = Mockito.mock(Connection.class);
        Mockito.when(dataSource.getConnection()).thenReturn(connection);
        setDataSource(dataSource);

        expectThrows(org.wso2.dpdp.accelerator.common.exception.DPDPCommonRuntimeException.class, () ->
                JDBCPersistenceManager.getInstance().executeWithConnection(conn -> {
                    throw new SQLException("expected");
                }));
        Mockito.verify(connection).close();
    }

    @Test
    public void transactionCheckedFailureRollsBackAndIsWrapped() throws Exception {
        DataSource dataSource = Mockito.mock(DataSource.class);
        Connection connection = Mockito.mock(Connection.class);
        Mockito.when(dataSource.getConnection()).thenReturn(connection);
        Mockito.when(connection.getAutoCommit()).thenReturn(true);
        setDataSource(dataSource);

        expectThrows(org.wso2.dpdp.accelerator.common.exception.DPDPCommonRuntimeException.class, () ->
                JDBCPersistenceManager.getInstance().executeInTransaction(conn -> {
                    throw new SQLException("expected");
                }));
        Mockito.verify(connection).rollback();
        Mockito.verify(connection).setAutoCommit(true);
        Mockito.verify(connection).close();
    }

    @Test
    public void transactionRejectsNullCallback() {
        expectThrows(IllegalArgumentException.class,
                () -> JDBCPersistenceManager.getInstance().executeInTransaction(null));
        expectThrows(IllegalArgumentException.class,
                () -> JDBCPersistenceManager.getInstance().executeWithConnection(null));
    }

    @Test
    public void databaseUtilsDelegatesConnectionAndTransactionOwnership() throws Exception {
        DataSource dataSource = Mockito.mock(DataSource.class);
        Connection firstConnection = Mockito.mock(Connection.class);
        Connection secondConnection = Mockito.mock(Connection.class);
        Mockito.when(dataSource.getConnection()).thenReturn(firstConnection, secondConnection);
        Mockito.when(secondConnection.getAutoCommit()).thenReturn(true);
        setDataSource(dataSource);

        assertEquals(DatabaseUtils.executeWithConnection(connection -> "read"), "read");
        assertTrue(DatabaseUtils.executeInTransaction(connection -> true));

        Mockito.verify(firstConnection).close();
        Mockito.verify(secondConnection).commit();
        Mockito.verify(secondConnection).close();
    }

    private void setDataSource(DataSource dataSource) throws Exception {
        Field field = JDBCPersistenceManager.class.getDeclaredField("dataSource");
        field.setAccessible(true);
        field.set(null, dataSource);
    }
}
