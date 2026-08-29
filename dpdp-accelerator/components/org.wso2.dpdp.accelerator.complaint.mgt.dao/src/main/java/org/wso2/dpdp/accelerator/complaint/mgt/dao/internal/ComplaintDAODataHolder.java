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

import org.wso2.dpdp.accelerator.common.config.DPDPConfigurationService;
import org.wso2.dpdp.accelerator.complaint.mgt.dao.ComplaintAttachmentDAO;
import org.wso2.dpdp.accelerator.complaint.mgt.dao.ComplaintDAO;
import org.wso2.dpdp.accelerator.complaint.mgt.dao.ComplaintEventDAO;

/**
 * Holds this bundle's own DAOs plus the external OSGi services it depends on, mirroring
 * {@code EventNotificationDAODataHolder} in the event-notifications module.
 */
public final class ComplaintDAODataHolder {

    private static final ComplaintDAODataHolder INSTANCE = new ComplaintDAODataHolder();

    private volatile DPDPConfigurationService configurationService;
    private volatile ComplaintDAO complaintDAO;
    private volatile ComplaintEventDAO complaintEventDAO;
    private volatile ComplaintAttachmentDAO complaintAttachmentDAO;

    private ComplaintDAODataHolder() {
    }

    public static ComplaintDAODataHolder getInstance() {
        return INSTANCE;
    }

    public DPDPConfigurationService getConfigurationService() {
        return configurationService;
    }

    public void setConfigurationService(DPDPConfigurationService configurationService) {
        this.configurationService = configurationService;
    }

    public ComplaintDAO getComplaintDAO() {
        return complaintDAO;
    }

    public void setComplaintDAO(ComplaintDAO complaintDAO) {
        this.complaintDAO = complaintDAO;
    }

    public ComplaintEventDAO getComplaintEventDAO() {
        return complaintEventDAO;
    }

    public void setComplaintEventDAO(ComplaintEventDAO complaintEventDAO) {
        this.complaintEventDAO = complaintEventDAO;
    }

    public ComplaintAttachmentDAO getComplaintAttachmentDAO() {
        return complaintAttachmentDAO;
    }

    public void setComplaintAttachmentDAO(ComplaintAttachmentDAO complaintAttachmentDAO) {
        this.complaintAttachmentDAO = complaintAttachmentDAO;
    }

    /** Clears every held reference - used on bundle deactivation. */
    public void clear() {
        configurationService = null;
        complaintDAO = null;
        complaintEventDAO = null;
        complaintAttachmentDAO = null;
    }
}
