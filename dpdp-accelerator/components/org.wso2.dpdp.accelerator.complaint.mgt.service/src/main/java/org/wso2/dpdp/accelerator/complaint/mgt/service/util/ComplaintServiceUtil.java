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

package org.wso2.dpdp.accelerator.complaint.mgt.service.util;

import org.wso2.dpdp.accelerator.complaint.mgt.dao.ComplaintDAO;
import org.wso2.dpdp.accelerator.complaint.mgt.dao.model.Complaint;
import org.wso2.dpdp.accelerator.complaint.mgt.service.exception.ComplaintErrorCode;
import org.wso2.dpdp.accelerator.complaint.mgt.service.exception.ComplaintException;
import org.wso2.dpdp.accelerator.complaint.mgt.service.exception.ComplaintServiceConstants;

import java.sql.Connection;
import java.util.Optional;

/**
 * Shared "fetch or throw not-found" logic for complaint existence/ownership checks. Every service
 * that needs to compose that check with a write in the same connection/transaction calls this
 * directly, instead of one public service method calling back into another with a shared
 * {@link Connection} - mirrors the Financial Services accelerator's ConsentCoreServiceUtil
 * pattern of a static helper taking the DAO and Connection, which keeps {@link Connection} out of
 * every service interface's public surface.
 */
public final class ComplaintServiceUtil {

    private ComplaintServiceUtil() {

    }

    /** Fetches a complaint, throwing a 404 {@link ComplaintException} if it doesn't exist for this org. */
    public static Complaint getExistingComplaint(ComplaintDAO complaintDAO, Connection conn, String orgId,
            String complaintId) {
        if (complaintId == null || complaintId.trim().isEmpty() || orgId == null || orgId.trim().isEmpty()) {
            throw new ComplaintException(ComplaintErrorCode.COMPLAINT_NOT_FOUND,
                    ComplaintServiceConstants.COMPLAINT_NOT_FOUND_ERROR);
        }
        Optional<Complaint> complaintOpt = complaintDAO.getComplaintById(conn, complaintId.trim(), orgId.trim());
        if (complaintOpt.isEmpty()) {
            throw new ComplaintException(ComplaintErrorCode.COMPLAINT_NOT_FOUND,
                    String.format(ComplaintServiceConstants.COMPLAINT_NOT_FOUND_BY_ID_ERROR, complaintId));
        }
        return complaintOpt.get();
    }

    /**
     * Same as {@link #getExistingComplaint}, additionally raising a 404 (not a 403 - see
     * complaint-server-API.yaml, which is explicit that /me/* must not confirm a complaint's
     * existence to a caller who doesn't own it) if the complaint's userId does not match
     * ownerUserId.
     */
    public static Complaint getOwnedComplaint(ComplaintDAO complaintDAO, Connection conn, String orgId,
            String complaintId, String ownerUserId) {
        Complaint complaint = getExistingComplaint(complaintDAO, conn, orgId, complaintId);
        if (!complaint.getUserId().equals(ownerUserId)) {
            throw new ComplaintException(ComplaintErrorCode.COMPLAINT_NOT_FOUND,
                    String.format(ComplaintServiceConstants.COMPLAINT_NOT_FOUND_BY_ID_ERROR, complaintId));
        }
        return complaint;
    }
}
