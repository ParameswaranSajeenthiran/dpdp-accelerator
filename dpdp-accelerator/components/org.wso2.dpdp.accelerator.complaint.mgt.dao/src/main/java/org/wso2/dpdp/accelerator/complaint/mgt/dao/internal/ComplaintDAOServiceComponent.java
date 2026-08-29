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

package org.wso2.dpdp.accelerator.complaint.mgt.dao.internal;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.component.annotations.ReferenceCardinality;
import org.osgi.service.component.annotations.ReferencePolicy;
import org.wso2.dpdp.accelerator.common.config.DPDPConfigurationService;
import org.wso2.dpdp.accelerator.common.exception.DPDPCommonRuntimeException;
import org.wso2.dpdp.accelerator.common.util.DatabaseUtils;
import org.wso2.dpdp.accelerator.complaint.mgt.dao.ComplaintAttachmentDAO;
import org.wso2.dpdp.accelerator.complaint.mgt.dao.ComplaintDAO;
import org.wso2.dpdp.accelerator.complaint.mgt.dao.ComplaintDAOProvider;
import org.wso2.dpdp.accelerator.complaint.mgt.dao.ComplaintEventDAO;
import org.wso2.dpdp.accelerator.complaint.mgt.dao.impl.ComplaintAttachmentDAOImpl;
import org.wso2.dpdp.accelerator.complaint.mgt.dao.impl.ComplaintDAOImpl;
import org.wso2.dpdp.accelerator.complaint.mgt.dao.impl.ComplaintEventDAOImpl;

import java.sql.Connection;
import java.sql.SQLException;

/**
 * Creates the complaint DAOs and publishes them through a single OSGi provider service,
 * mirroring {@code EventNotificationDAOServiceComponent} in the event-notifications module - this
 * bundle previously ran as a plain (non-OSGi) jar consumed directly by complaint.mgt.endpoint,
 * which needed its own webapp-local JNDI ResourceLink (META-INF/context.xml) to resolve the
 * shared DPDP datasource. As an OSGi bundle it runs in Carbon's own JNDI space instead, the same
 * space {@link DatabaseUtils#getDBConnection()} already resolves against - no webapp-local JNDI
 * binding needed any more.
 */
@Component(
        name = "org.wso2.dpdp.accelerator.complaint.mgt.dao.internal.ComplaintDAOServiceComponent",
        service = ComplaintDAOProvider.class,
        immediate = true
)
public class ComplaintDAOServiceComponent implements ComplaintDAOProvider {

    private static final Log LOG = LogFactory.getLog(ComplaintDAOServiceComponent.class);

    @Activate
    protected void activate() {

        ComplaintDAODataHolder dataHolder = ComplaintDAODataHolder.getInstance();
        verifyDatabaseConnection(dataHolder.getConfigurationService());
        dataHolder.setComplaintDAO(new ComplaintDAOImpl());
        dataHolder.setComplaintEventDAO(new ComplaintEventDAOImpl());
        dataHolder.setComplaintAttachmentDAO(new ComplaintAttachmentDAOImpl());
        LOG.debug("Complaint DAO services are activated successfully.");
    }

    /**
     * Fails bundle activation immediately when the shared DPDP datasource is not reachable,
     * rather than surfacing that failure later on the first DAO call.
     */
    private void verifyDatabaseConnection(DPDPConfigurationService configurationService) {

        verifyDatabaseConnection(DatabaseUtils.getDBConnection(), configurationService);
    }

    void verifyDatabaseConnection(Connection connection, DPDPConfigurationService configurationService) {

        try {
            int timeoutSeconds = configurationService.getJdbcConnectionVerificationTimeoutSeconds();
            if (!connection.isValid(timeoutSeconds)) {
                throw new DPDPCommonRuntimeException("The DPDP database connection is not active.");
            }
            LOG.debug("Verified the DPDP database connection is active.");
        } catch (SQLException e) {
            throw new DPDPCommonRuntimeException("Error while verifying the DPDP database connection.", e);
        } finally {
            DatabaseUtils.closeConnection(connection);
        }
    }

    @Deactivate
    protected void deactivate() {

        ComplaintDAODataHolder.getInstance().clear();
        LOG.debug("Complaint DAO services are deactivated successfully.");
    }

    @Reference(
            service = DPDPConfigurationService.class,
            cardinality = ReferenceCardinality.MANDATORY,
            policy = ReferencePolicy.STATIC,
            unbind = "unsetDPDPConfigurationService"
    )
    protected void setDPDPConfigurationService(DPDPConfigurationService configurationService) {

        ComplaintDAODataHolder.getInstance().setConfigurationService(configurationService);
    }

    protected void unsetDPDPConfigurationService(DPDPConfigurationService configurationService) {

        ComplaintDAODataHolder dataHolder = ComplaintDAODataHolder.getInstance();
        if (dataHolder.getConfigurationService() == configurationService) {
            dataHolder.setConfigurationService(null);
        }
    }

    @Override
    public ComplaintDAO getComplaintDAO() {

        return ComplaintDAODataHolder.getInstance().getComplaintDAO();
    }

    @Override
    public ComplaintEventDAO getComplaintEventDAO() {

        return ComplaintDAODataHolder.getInstance().getComplaintEventDAO();
    }

    @Override
    public ComplaintAttachmentDAO getComplaintAttachmentDAO() {

        return ComplaintDAODataHolder.getInstance().getComplaintAttachmentDAO();
    }
}
