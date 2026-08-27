/*
 * Copyright (c) 2026, WSO2 LLC. (https://www.wso2.com).
 *
 * WSO2 LLC. licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License.
 */
package org.wso2.dpdp.accelerator.common.persistence;

import org.mockito.Mockito;
import org.mockito.InOrder;
import org.h2.jdbcx.JdbcDataSource;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.Test;
import org.wso2.dpdp.accelerator.common.util.DatabaseUtils;

import javax.sql.DataSource;
import java.lang.reflect.Field;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import static org.testng.Assert.expectThrows;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertSame;
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
    public void executeInTransactionRollsBackErrorBeforeRestoringAutoCommit() throws Exception {
        DataSource dataSource = Mockito.mock(DataSource.class);
        Connection connection = Mockito.mock(Connection.class);
        Mockito.when(dataSource.getConnection()).thenReturn(connection);
        Mockito.when(connection.getAutoCommit()).thenReturn(true);
        setDataSource(dataSource);
        AssertionError expected = new AssertionError("expected");

        AssertionError actual = expectThrows(AssertionError.class, () ->
                JDBCPersistenceManager.getInstance().executeInTransaction(conn -> {
                    throw expected;
                }));

        assertSame(actual, expected);
        InOrder lifecycle = Mockito.inOrder(connection);
        lifecycle.verify(connection).setAutoCommit(false);
        lifecycle.verify(connection).rollback();
        lifecycle.verify(connection).setAutoCommit(true);
        lifecycle.verify(connection).close();
        Mockito.verify(connection, Mockito.never()).commit();
    }

    @Test
    public void executeInTransactionDoesNotPersistH2ChangesWhenErrorIsThrown() throws Exception {
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:dpdp_error_" + System.nanoTime() + ";DB_CLOSE_DELAY=-1");
        setDataSource(dataSource);
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {
            statement.execute("CREATE TABLE TX_ERROR_TEST (ID INT PRIMARY KEY)");
        }

        expectThrows(AssertionError.class, () ->
                JDBCPersistenceManager.getInstance().executeInTransaction(connection -> {
                    try (PreparedStatement statement = connection.prepareStatement(
                            "INSERT INTO TX_ERROR_TEST (ID) VALUES (?)")) {
                        statement.setInt(1, 1);
                        statement.executeUpdate();
                    }
                    throw new AssertionError("expected");
                }));

        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement("SELECT COUNT(*) FROM TX_ERROR_TEST");
             ResultSet resultSet = statement.executeQuery()) {
            assertTrue(resultSet.next());
            assertEquals(resultSet.getInt(1), 0);
        }
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
