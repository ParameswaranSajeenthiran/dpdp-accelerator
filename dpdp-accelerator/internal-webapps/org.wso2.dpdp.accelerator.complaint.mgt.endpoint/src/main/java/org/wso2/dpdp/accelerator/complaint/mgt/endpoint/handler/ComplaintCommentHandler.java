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

package org.wso2.dpdp.accelerator.complaint.mgt.endpoint.handler;

import org.wso2.carbon.context.PrivilegedCarbonContext;
import org.wso2.dpdp.accelerator.complaint.mgt.endpoint.dto.ComplaintMessageRequest;
import org.wso2.dpdp.accelerator.complaint.mgt.endpoint.dto.ComplaintCommentCreateResponse;
import org.wso2.dpdp.accelerator.complaint.mgt.endpoint.dto.MeComplaintMessageRequest;
import org.wso2.dpdp.accelerator.complaint.mgt.endpoint.util.ComplaintDtoMapper;
import org.wso2.dpdp.accelerator.complaint.mgt.service.ComplaintEventService;
import org.wso2.dpdp.accelerator.complaint.mgt.service.ComplaintService;
import org.wso2.dpdp.accelerator.complaint.mgt.service.exception.ComplaintErrorCode;
import org.wso2.dpdp.accelerator.complaint.mgt.service.exception.ComplaintException;
import org.wso2.dpdp.accelerator.complaint.mgt.service.exception.ComplaintServiceConstants;

/**
 * Shared business logic behind both /me/complaints/{id}/comments (Data Principal, always
 * isPublic=true, role USER) and /complaints/{id}/comments (officer/admin, isPublic and toStatus
 * caller-controlled). actorUserId/actorRole are always resolved by the endpoint layer from the
 * caller's bearer token - never accepted from the request body, per the API spec.
 */
public class ComplaintCommentHandler {

    private final ComplaintService complaintService;
    private final ComplaintEventService complaintEventService;

    public ComplaintCommentHandler() {
        this.complaintService = getOSGiService(ComplaintService.class);
        this.complaintEventService = getOSGiService(ComplaintEventService.class);
    }

    private static <T> T getOSGiService(Class<T> serviceClass) {
        T service = (T) PrivilegedCarbonContext.getThreadLocalCarbonContext().getOSGiService(serviceClass, null);
        if (service == null) {
            throw new IllegalStateException(serviceClass.getName() + " OSGi service not available");
        }
        return service;
    }

    public ComplaintCommentHandler(ComplaintService complaintService, ComplaintEventService complaintEventService) {
        this.complaintService = complaintService;
        this.complaintEventService = complaintEventService;
    }

    public ComplaintCommentCreateResponse addComment(String orgId, String complaintId, String actorUserId,
            String actorUserName, String actorRole, ComplaintMessageRequest request) {
        String message = request != null ? request.getMessage() : null;
        Boolean requestedIsPublic = request != null ? request.getIsPublic() : null;
        if (requestedIsPublic == null) {
            throw new ComplaintException(ComplaintErrorCode.VALIDATION_FAILED,
                    ComplaintServiceConstants.IS_PUBLIC_REQUIRED_ERROR);
        }
        boolean isPublic = requestedIsPublic;
        String toStatus = request != null ? ComplaintDtoMapper.value(request.getToStatus()) : null;

        return ComplaintDtoMapper.toCommentResponse(complaintEventService.addComment(orgId, complaintId,
                actorUserId, actorUserName, actorRole, message, isPublic, toStatus));
    }

    public ComplaintCommentCreateResponse addOwnComment(String orgId, String complaintId, String ownerUserId,
            String ownerUserName, MeComplaintMessageRequest request) {
        complaintService.getOwnedComplaint(orgId, complaintId, ownerUserId);
        String message = request != null ? request.getMessage() : null;
        String toStatus = request != null ? ComplaintDtoMapper.value(request.getToStatus()) : null;

        return ComplaintDtoMapper.toCommentResponse(complaintEventService.addComment(orgId, complaintId,
                ownerUserId, ownerUserName, "USER", message, true, toStatus));
    }
}
