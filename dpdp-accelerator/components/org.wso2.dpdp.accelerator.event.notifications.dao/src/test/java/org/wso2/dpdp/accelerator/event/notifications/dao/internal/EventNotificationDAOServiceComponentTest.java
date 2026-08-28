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

package org.wso2.dpdp.accelerator.event.notifications.dao.internal;

import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import org.wso2.dpdp.accelerator.common.config.DPDPConfigurationService;
import org.wso2.dpdp.accelerator.common.exception.DPDPCommonRuntimeException;
import org.wso2.dpdp.accelerator.common.persistence.JDBCPersistenceManager;

import javax.sql.DataSource;
import java.lang.reflect.Field;
import java.sql.Connection;
import java.sql.SQLException;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertSame;
import static org.testng.Assert.expectThrows;

/**
 * {@code activate()} now verifies the shared DPDP datasource is reachable before publishing the
 * DAOs, so every test pre-seeds {@link JDBCPersistenceManager}'s static datasource with a mock
 * (the same reflection approach {@code JDBCPersistenceManagerTest} uses) rather than hitting a
 * real JNDI context.
 */
public class EventNotificationDAOServiceComponentTest {

    private Connection connection;

    @BeforeMethod
    public void setUpMockDataSource() throws Exception {

        DataSource dataSource = mock(DataSource.class);
        connection = mock(Connection.class);
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.isValid(anyInt())).thenReturn(true);
        setStaticDataSource(dataSource);
    }

    @AfterMethod
    public void tearDown() throws Exception {

        setStaticDataSource(null);
        setStaticInstance(null);
        EventNotificationDAODataHolder.getInstance().clear();
    }

    @Test
    public void shouldPublishAndClearOneInstanceOfEachDAO() throws SQLException {

        DPDPConfigurationService configurationService = mock(DPDPConfigurationService.class);
        EventNotificationDAOServiceComponent component = new EventNotificationDAOServiceComponent();
        component.setDPDPConfigurationService(configurationService);

        assertSame(EventNotificationDAODataHolder.getInstance().getConfigurationService(), configurationService);

        component.activate();

        assertNotNull(component.getTopicDAO());
        assertNotNull(component.getSubscriptionDAO());
        assertNotNull(component.getEventDAO());
        assertNotNull(component.getDeliveryDAO());
        assertNotNull(component.getDeliveryAckDAO());
        assertSame(component.getTopicDAO(), component.getTopicDAO());
        assertSame(component.getSubscriptionDAO(), component.getSubscriptionDAO());
        assertSame(component.getEventDAO(), component.getEventDAO());
        assertSame(component.getDeliveryDAO(), component.getDeliveryDAO());
        assertSame(component.getDeliveryAckDAO(), component.getDeliveryAckDAO());
        verify(connection).close();

        component.deactivate();

        assertNull(component.getTopicDAO());
        assertNull(component.getSubscriptionDAO());
        assertNull(component.getEventDAO());
        assertNull(component.getDeliveryDAO());
        assertNull(component.getDeliveryAckDAO());
        assertNull(EventNotificationDAODataHolder.getInstance().getConfigurationService());

        component.unsetDPDPConfigurationService(configurationService);
    }

    @Test
    public void activateFailsWhenTheDatabaseConnectionIsNotValid() throws SQLException {

        when(connection.isValid(anyInt())).thenReturn(false);
        DPDPConfigurationService configurationService = mock(DPDPConfigurationService.class);
        EventNotificationDAOServiceComponent component = new EventNotificationDAOServiceComponent();
        component.setDPDPConfigurationService(configurationService);

        expectThrows(DPDPCommonRuntimeException.class, component::activate);
        verify(connection).close();
    }

    @Test
    public void activateFailsWhenTheValidityCheckThrows() throws SQLException {

        when(connection.isValid(anyInt())).thenThrow(new SQLException("boom"));
        DPDPConfigurationService configurationService = mock(DPDPConfigurationService.class);
        EventNotificationDAOServiceComponent component = new EventNotificationDAOServiceComponent();
        component.setDPDPConfigurationService(configurationService);

        expectThrows(DPDPCommonRuntimeException.class, component::activate);
        verify(connection).close();
    }

    @Test
    public void unsetDPDPConfigurationServiceOnlyClearsWhenTheInstanceMatches() {

        DPDPConfigurationService configurationService = mock(DPDPConfigurationService.class);
        EventNotificationDAOServiceComponent component = new EventNotificationDAOServiceComponent();
        component.setDPDPConfigurationService(configurationService);

        component.unsetDPDPConfigurationService(mock(DPDPConfigurationService.class));
        assertSame(EventNotificationDAODataHolder.getInstance().getConfigurationService(), configurationService);

        component.unsetDPDPConfigurationService(configurationService);
        assertNull(EventNotificationDAODataHolder.getInstance().getConfigurationService());
    }

    @Test
    public void dataHolderShouldExposeTheComponentManagedDAOsAndConfigurationService() {

        EventNotificationDAODataHolder dataHolder = EventNotificationDAODataHolder.getInstance();
        DPDPConfigurationService configurationService = mock(DPDPConfigurationService.class);
        EventNotificationDAOServiceComponent component = new EventNotificationDAOServiceComponent();
        component.setDPDPConfigurationService(configurationService);
        component.activate();

        assertSame(dataHolder.getTopicDAO(), component.getTopicDAO());
        assertSame(dataHolder.getSubscriptionDAO(), component.getSubscriptionDAO());
        assertSame(dataHolder.getEventDAO(), component.getEventDAO());
        assertSame(dataHolder.getDeliveryDAO(), component.getDeliveryDAO());
        assertSame(dataHolder.getDeliveryAckDAO(), component.getDeliveryAckDAO());
        assertSame(dataHolder.getConfigurationService(), configurationService);
    }

    private static void setStaticDataSource(DataSource dataSource) throws Exception {

        Field field = JDBCPersistenceManager.class.getDeclaredField("dataSource");
        field.setAccessible(true);
        field.set(null, dataSource);
    }

    private static void setStaticInstance(JDBCPersistenceManager instance) throws Exception {

        Field field = JDBCPersistenceManager.class.getDeclaredField("instance");
        field.setAccessible(true);
        field.set(null, instance);
    }
}
