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
import org.wso2.dpdp.accelerator.complaint.mgt.dao.model.Complaint;
import org.wso2.dpdp.accelerator.complaint.mgt.dao.model.ComplaintAttachment;
import org.wso2.dpdp.accelerator.complaint.mgt.endpoint.dto.CategoryListResponse;
import org.wso2.dpdp.accelerator.complaint.mgt.endpoint.dto.ComplaintCreateRequest;
import org.wso2.dpdp.accelerator.complaint.mgt.endpoint.dto.ComplaintCreateResponse;
import org.wso2.dpdp.accelerator.complaint.mgt.endpoint.dto.ComplaintListResponse;
import org.wso2.dpdp.accelerator.complaint.mgt.endpoint.dto.ComplaintQueueStatsResponse;
import org.wso2.dpdp.accelerator.complaint.mgt.endpoint.dto.ComplaintRecord;
import org.wso2.dpdp.accelerator.complaint.mgt.endpoint.dto.ComplaintStatusUpdateRequest;
import org.wso2.dpdp.accelerator.complaint.mgt.endpoint.dto.ComplaintStatusUpdateResponse;
import org.wso2.dpdp.accelerator.complaint.mgt.endpoint.dto.MeComplaintCreateRequest;
import org.wso2.dpdp.accelerator.complaint.mgt.endpoint.dto.MeComplaintStatusUpdateRequest;
import org.wso2.dpdp.accelerator.complaint.mgt.endpoint.util.ComplaintDtoMapper;
import org.wso2.dpdp.accelerator.complaint.mgt.service.ComplaintAttachmentService;
import org.wso2.dpdp.accelerator.complaint.mgt.service.ComplaintEventService;
import org.wso2.dpdp.accelerator.complaint.mgt.service.ComplaintService;
import org.wso2.dpdp.accelerator.complaint.mgt.service.util.ComplaintServiceUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.TreeMap;
import java.util.stream.Collectors;

/**
 * Shared business logic behind both the /me/complaints/* (Data Principal) and /complaints/*
 * (officer/admin) resource classes. The "own*"-prefixed methods enforce ownership (404, not 403,
 * on a mismatch - see complaint-server-API.yaml) and filter attachments to isPublic=true; the
 * plain methods are unrestricted and used only by the officer/admin (any-scope) endpoints.
 */
public class ComplaintHandler {

    private final ComplaintService complaintService;
    private final ComplaintEventService complaintEventService;
    private final ComplaintAttachmentService complaintAttachmentService;

    public ComplaintHandler() {
        this.complaintService = getOSGiService(ComplaintService.class);
        this.complaintEventService = getOSGiService(ComplaintEventService.class);
        this.complaintAttachmentService = getOSGiService(ComplaintAttachmentService.class);
    }

    private static <T> T getOSGiService(Class<T> serviceClass) {
        T service = (T) PrivilegedCarbonContext.getThreadLocalCarbonContext().getOSGiService(serviceClass, null);
        if (service == null) {
            throw new IllegalStateException(serviceClass.getName() + " OSGi service not available");
        }
        return service;
    }

    public ComplaintHandler(ComplaintService complaintService, ComplaintEventService complaintEventService,
            ComplaintAttachmentService complaintAttachmentService) {
        this.complaintService = complaintService;
        this.complaintEventService = complaintEventService;
        this.complaintAttachmentService = complaintAttachmentService;
    }

    // ---- Officer/admin (/complaints/*) ----

    public ComplaintCreateResponse createComplaint(String orgId, String actorUserId, String actorRole,
            ComplaintCreateRequest request) {
        String userId = request != null ? request.getUserId() : null;
        String subjectCategory = request != null ? ComplaintDtoMapper.value(request.getSubjectCategory()) : null;
        String description = request != null ? request.getDescription() : null;
        // No resolvable display name here - the officer supplies only the Data Principal's userId,
        // not a token belonging to that user. actorUserId/actorRole identify the officer performing
        // the intake for the audit trail - resolved by the caller from the bearer token, never from
        // the request body.
        return ComplaintDtoMapper.toCreateResponse(complaintService.createComplaint(orgId, userId, null,
                subjectCategory, description, actorUserId, actorRole));
    }

    public ComplaintRecord getComplaint(String orgId, String complaintId) {
        Complaint complaint = complaintService.getComplaint(orgId, complaintId);
        List<ComplaintAttachment> attachments =
                complaintAttachmentService.listAttachmentsForComplaint(orgId, complaintId);
        return ComplaintDtoMapper.toRecord(complaint, attachments);
    }

    public ComplaintListResponse listComplaints(String orgId, String status, String priority, String userId,
            String search, Integer limit, Integer offset, String sort) {
        return listComplaints(orgId, status, priority, userId, search, limit, offset, sort, false);
    }

    public ComplaintQueueStatsResponse getQueueStats(String orgId) {
        return ComplaintDtoMapper.toQueueStats(complaintService.getQueueStats(orgId));
    }

    public CategoryListResponse getCategories() {
        return ComplaintDtoMapper.toCategories(new TreeMap<>(ComplaintServiceUtil.getCategoryPriorities()));
    }

    public ComplaintStatusUpdateResponse updateStatus(String orgId, String complaintId, String actorUserId,
            String actorUserName, String actorRole, ComplaintStatusUpdateRequest request) {
        String toStatus = request != null ? ComplaintDtoMapper.value(request.getToStatus()) : null;
        String note = request != null ? request.getNote() : null;

        return ComplaintDtoMapper.toStatusUpdateResponse(complaintEventService.updateStatus(orgId, complaintId,
                actorUserId, actorUserName, actorRole, toStatus, note));
    }

    // ---- Data Principal (/me/complaints/*) ----

    public ComplaintCreateResponse createOwnComplaint(String orgId, String ownerUserId, String ownerUserName,
            MeComplaintCreateRequest request) {
        String subjectCategory = request != null ? ComplaintDtoMapper.value(request.getSubjectCategory()) : null;
        String description = request != null ? request.getDescription() : null;
        return ComplaintDtoMapper.toCreateResponse(
                complaintService.createComplaint(orgId, ownerUserId, ownerUserName, subjectCategory, description));
    }

    public ComplaintRecord getOwnComplaint(String orgId, String complaintId, String ownerUserId) {
        Complaint complaint = complaintService.getOwnedComplaint(orgId, complaintId, ownerUserId);
        List<ComplaintAttachment> attachments =
                complaintAttachmentService.listAttachmentsForComplaint(orgId, complaintId);
        return ComplaintDtoMapper.toRecord(complaint, publicOnly(attachments));
    }

    public ComplaintListResponse listOwnComplaints(String orgId, String ownerUserId, String status,
            Integer limit, Integer offset, String sort) {
        // No free-text search here - a Data Principal's own list is already scoped to a single
        // userId, so there's nothing for search to narrow down further.
        return listComplaints(orgId, status, null, ownerUserId, null, limit, offset, sort, true);
    }

    public ComplaintStatusUpdateResponse updateOwnStatus(String orgId, String complaintId, String ownerUserId,
            String ownerUserName, MeComplaintStatusUpdateRequest request) {
        complaintService.getOwnedComplaint(orgId, complaintId, ownerUserId);
        String toStatus = request != null ? ComplaintDtoMapper.value(request.getToStatus()) : null;
        return ComplaintDtoMapper.toStatusUpdateResponse(complaintEventService.updateStatus(orgId, complaintId,
                ownerUserId, ownerUserName, "USER", toStatus, null));
    }

    // ---- shared ----

    private ComplaintListResponse listComplaints(String orgId, String status, String priority, String userId,
            String search, Integer limit, Integer offset, String sort, boolean restrictToPublicAttachments) {
        int lim = limit != null && limit > 0 ? Math.min(limit, 100) : 10;
        int off = offset != null && offset >= 0 ? offset : 0;
        int[] totalOut = new int[]{0};

        List<Complaint> list = complaintService.listComplaints(orgId, status, priority, userId, search, lim, off,
                sort, totalOut);

        List<ComplaintRecord> records = new ArrayList<>();
        for (Complaint complaint : list) {
            List<ComplaintAttachment> attachments = complaintAttachmentService
                    .listAttachmentsForComplaint(orgId, complaint.getComplaintId());
            records.add(ComplaintDtoMapper.toRecord(complaint,
                    restrictToPublicAttachments ? publicOnly(attachments) : attachments));
        }

        return new ComplaintListResponse()
                .data(records)
                .metadata(ComplaintDtoMapper.toPage(totalOut[0], off, records.size(), lim));
    }

    private List<ComplaintAttachment> publicOnly(List<ComplaintAttachment> attachments) {
        return attachments.stream().filter(ComplaintAttachment::isPublic).collect(Collectors.toList());
    }
}
