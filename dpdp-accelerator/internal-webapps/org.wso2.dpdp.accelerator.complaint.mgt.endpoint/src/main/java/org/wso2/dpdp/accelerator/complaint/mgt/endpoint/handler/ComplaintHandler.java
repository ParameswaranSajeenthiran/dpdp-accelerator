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
import org.wso2.dpdp.accelerator.complaint.mgt.dao.model.ComplaintQueueStats;
import org.wso2.dpdp.accelerator.complaint.mgt.endpoint.dto.CategoryListResponseDTO;
import org.wso2.dpdp.accelerator.complaint.mgt.endpoint.dto.ComplaintAttachmentResponseDTO;
import org.wso2.dpdp.accelerator.complaint.mgt.endpoint.dto.ComplaintCategoryDTO;
import org.wso2.dpdp.accelerator.complaint.mgt.endpoint.dto.ComplaintCreateRequestDTO;
import org.wso2.dpdp.accelerator.complaint.mgt.endpoint.dto.ComplaintCreateResponseDTO;
import org.wso2.dpdp.accelerator.complaint.mgt.endpoint.dto.ComplaintListResponseDTO;
import org.wso2.dpdp.accelerator.complaint.mgt.endpoint.dto.ComplaintQueueStatsResponseDTO;
import org.wso2.dpdp.accelerator.complaint.mgt.endpoint.dto.ComplaintRecordDTO;
import org.wso2.dpdp.accelerator.complaint.mgt.endpoint.dto.ComplaintStatusUpdateRequestDTO;
import org.wso2.dpdp.accelerator.complaint.mgt.endpoint.dto.ComplaintStatusUpdateResponseDTO;
import org.wso2.dpdp.accelerator.complaint.mgt.endpoint.dto.MeComplaintCreateRequestDTO;
import org.wso2.dpdp.accelerator.complaint.mgt.endpoint.dto.MeComplaintStatusUpdateRequestDTO;
import org.wso2.dpdp.accelerator.complaint.mgt.endpoint.dto.PageMetadataDTO;
import org.wso2.dpdp.accelerator.complaint.mgt.service.ComplaintAttachmentService;
import org.wso2.dpdp.accelerator.complaint.mgt.service.ComplaintEventService;
import org.wso2.dpdp.accelerator.complaint.mgt.service.ComplaintService;
import org.wso2.dpdp.accelerator.complaint.mgt.service.util.PriorityMapper;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

/**
 * Shared business logic behind both the /me/complaints/* (Data Principal) and /complaints/*
 * (officer/admin) resource classes. The "own*"-prefixed methods enforce ownership (404, not 403,
 * on a mismatch - see complaint-server-API.yaml) and filter attachments to isPublic=true; the
 * plain methods are unrestricted and used only by the officer/admin (any-scope) endpoints.
 *
 * <p>The complaint service layer returns DAO models (Complaint, ComplaintAttachment,
 * ComplaintQueueStats) - this handler owns turning those into the DTOs the REST layer serializes.
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

    public ComplaintCreateResponseDTO createComplaint(String orgId, String actorUserId, String actorRole,
            ComplaintCreateRequestDTO request) {
        String userId = request != null ? request.getUserId() : null;
        String subjectCategory = request != null ? request.getSubjectCategory() : null;
        String description = request != null ? request.getDescription() : null;
        // No resolvable display name here - the officer supplies only the Data Principal's userId,
        // not a token belonging to that user. actorUserId/actorRole identify the officer performing
        // the intake for the audit trail - resolved by the caller from the bearer token, never from
        // the request body.
        Complaint complaint = complaintService.createComplaint(orgId, userId, null, subjectCategory, description,
                actorUserId, actorRole);
        return ComplaintCreateResponseDTO.from(complaint);
    }

    public ComplaintRecordDTO getComplaint(String orgId, String complaintId) {
        Complaint complaint = complaintService.getComplaint(orgId, complaintId);
        List<ComplaintAttachment> attachments =
                complaintAttachmentService.listAttachmentsForComplaint(orgId, complaintId);
        return ComplaintRecordDTO.from(complaint, toAttachmentDtos(attachments));
    }

    public ComplaintListResponseDTO listComplaints(String orgId, String status, String priority, String userId,
            Integer limit, Integer offset, String sort) {
        return listComplaints(orgId, status, priority, userId, limit, offset, sort, false);
    }

    public ComplaintQueueStatsResponseDTO getQueueStats(String orgId) {
        ComplaintQueueStats stats = complaintService.getQueueStats(orgId);
        return ComplaintQueueStatsResponseDTO.from(stats);
    }

    public CategoryListResponseDTO getCategories() {
        Map<String, String> categoryPriorities = new TreeMap<>(PriorityMapper.getCategoryPriorities());
        List<ComplaintCategoryDTO> beanList = new ArrayList<>();
        for (Map.Entry<String, String> entry : categoryPriorities.entrySet()) {
            beanList.add(new ComplaintCategoryDTO(entry.getKey(), entry.getValue()));
        }
        return new CategoryListResponseDTO(beanList);
    }

    public ComplaintStatusUpdateResponseDTO updateStatus(String orgId, String complaintId, String actorUserId,
            String actorUserName, String actorRole, ComplaintStatusUpdateRequestDTO request) {
        String toStatus = request != null ? request.getToStatus() : null;
        String note = request != null ? request.getNote() : null;

        Complaint complaint = complaintEventService.updateStatus(orgId, complaintId, actorUserId, actorUserName,
                actorRole, toStatus, note);
        return ComplaintStatusUpdateResponseDTO.from(complaint);
    }

    // ---- Data Principal (/me/complaints/*) ----

    public ComplaintCreateResponseDTO createOwnComplaint(String orgId, String ownerUserId, String ownerUserName,
            MeComplaintCreateRequestDTO request) {
        String subjectCategory = request != null ? request.getSubjectCategory() : null;
        String description = request != null ? request.getDescription() : null;
        Complaint complaint = complaintService.createComplaint(orgId, ownerUserId, ownerUserName, subjectCategory,
                description);
        return ComplaintCreateResponseDTO.from(complaint);
    }

    public ComplaintRecordDTO getOwnComplaint(String orgId, String complaintId, String ownerUserId) {
        Complaint complaint = complaintService.requireOwnedComplaint(orgId, complaintId, ownerUserId);
        List<ComplaintAttachment> attachments =
                complaintAttachmentService.listAttachmentsForComplaint(orgId, complaintId);
        return ComplaintRecordDTO.from(complaint, toAttachmentDtos(publicOnly(attachments)));
    }

    public ComplaintListResponseDTO listOwnComplaints(String orgId, String ownerUserId, String status,
            Integer limit, Integer offset, String sort) {
        return listComplaints(orgId, status, null, ownerUserId, limit, offset, sort, true);
    }

    public ComplaintStatusUpdateResponseDTO updateOwnStatus(String orgId, String complaintId, String ownerUserId,
            String ownerUserName, MeComplaintStatusUpdateRequestDTO request) {
        complaintService.requireOwnedComplaint(orgId, complaintId, ownerUserId);
        String toStatus = request != null ? request.getToStatus() : null;
        Complaint complaint = complaintEventService.updateStatus(orgId, complaintId, ownerUserId, ownerUserName,
                "USER", toStatus, null);
        return ComplaintStatusUpdateResponseDTO.from(complaint);
    }

    // ---- shared ----

    private ComplaintListResponseDTO listComplaints(String orgId, String status, String priority, String userId,
            Integer limit, Integer offset, String sort, boolean restrictToPublicAttachments) {
        int lim = limit != null && limit > 0 ? Math.min(limit, 100) : 10;
        int off = offset != null && offset >= 0 ? offset : 0;
        int[] totalOut = new int[]{0};

        List<Complaint> list = complaintService.listComplaints(orgId, status, priority, userId, lim, off, sort,
                totalOut);

        List<ComplaintRecordDTO> beanList = new ArrayList<>();
        for (Complaint complaint : list) {
            List<ComplaintAttachment> attachments = complaintAttachmentService
                    .listAttachmentsForComplaint(orgId, complaint.getComplaintId());
            beanList.add(ComplaintRecordDTO.from(complaint,
                    toAttachmentDtos(restrictToPublicAttachments ? publicOnly(attachments) : attachments)));
        }

        PageMetadataDTO metadata = new PageMetadataDTO(totalOut[0], off, beanList.size(), lim);
        return new ComplaintListResponseDTO(beanList, metadata);
    }

    private List<ComplaintAttachment> publicOnly(List<ComplaintAttachment> attachments) {
        return attachments.stream().filter(ComplaintAttachment::isPublic).collect(Collectors.toList());
    }

    private List<ComplaintAttachmentResponseDTO> toAttachmentDtos(List<ComplaintAttachment> attachments) {
        return attachments.stream().map(ComplaintAttachmentResponseDTO::from).collect(Collectors.toList());
    }
}
