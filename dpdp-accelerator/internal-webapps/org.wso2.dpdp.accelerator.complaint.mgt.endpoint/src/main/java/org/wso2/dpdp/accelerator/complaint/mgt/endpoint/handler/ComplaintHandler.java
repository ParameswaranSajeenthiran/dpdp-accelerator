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

import org.wso2.dpdp.accelerator.complaint.mgt.dao.model.Complaint;
import org.wso2.dpdp.accelerator.complaint.mgt.service.ComplaintAttachmentService;
import org.wso2.dpdp.accelerator.complaint.mgt.service.ComplaintEventService;
import org.wso2.dpdp.accelerator.complaint.mgt.service.ComplaintService;
import org.wso2.dpdp.accelerator.complaint.mgt.service.dto.CategoryListResponseBean;
import org.wso2.dpdp.accelerator.complaint.mgt.service.dto.ComplaintAttachmentResponseBean;
import org.wso2.dpdp.accelerator.complaint.mgt.service.dto.ComplaintCategoryBean;
import org.wso2.dpdp.accelerator.complaint.mgt.service.dto.ComplaintCreateRequestBean;
import org.wso2.dpdp.accelerator.complaint.mgt.service.dto.ComplaintCreateResponseBean;
import org.wso2.dpdp.accelerator.complaint.mgt.service.dto.ComplaintListResponseBean;
import org.wso2.dpdp.accelerator.complaint.mgt.service.dto.ComplaintQueueStatsResponseBean;
import org.wso2.dpdp.accelerator.complaint.mgt.service.dto.ComplaintRecordBean;
import org.wso2.dpdp.accelerator.complaint.mgt.service.dto.ComplaintStatusUpdateRequestBean;
import org.wso2.dpdp.accelerator.complaint.mgt.service.dto.ComplaintStatusUpdateResponseBean;
import org.wso2.dpdp.accelerator.complaint.mgt.service.dto.MeComplaintCreateRequestBean;
import org.wso2.dpdp.accelerator.complaint.mgt.service.dto.MeComplaintStatusUpdateRequestBean;
import org.wso2.dpdp.accelerator.complaint.mgt.service.dto.PageMetadataBean;
import org.wso2.dpdp.accelerator.complaint.mgt.service.impl.ComplaintAttachmentServiceImpl;
import org.wso2.dpdp.accelerator.complaint.mgt.service.impl.ComplaintEventServiceImpl;
import org.wso2.dpdp.accelerator.complaint.mgt.service.impl.ComplaintServiceImpl;
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
 */
public class ComplaintHandler {

    private final ComplaintService complaintService;
    private final ComplaintEventService complaintEventService;
    private final ComplaintAttachmentService complaintAttachmentService;

    public ComplaintHandler() {
        this.complaintService = new ComplaintServiceImpl();
        this.complaintEventService = new ComplaintEventServiceImpl();
        this.complaintAttachmentService = new ComplaintAttachmentServiceImpl(complaintService);
    }

    public ComplaintHandler(ComplaintService complaintService, ComplaintEventService complaintEventService,
            ComplaintAttachmentService complaintAttachmentService) {
        this.complaintService = complaintService;
        this.complaintEventService = complaintEventService;
        this.complaintAttachmentService = complaintAttachmentService;
    }

    // ---- Officer/admin (/complaints/*) ----

    public ComplaintCreateResponseBean createComplaint(String orgId, String actorUserId, String actorRole,
            ComplaintCreateRequestBean request) {
        String userId = request != null ? request.getUserId() : null;
        String subjectCategory = request != null ? request.getSubjectCategory() : null;
        String description = request != null ? request.getDescription() : null;
        // No resolvable display name here - the officer supplies only the Data Principal's userId,
        // not a token belonging to that user. actorUserId/actorRole identify the officer performing
        // the intake for the audit trail - resolved by the caller from the bearer token, never from
        // the request body.
        return complaintService.createComplaint(orgId, userId, null, subjectCategory, description, actorUserId,
                actorRole);
    }

    public ComplaintRecordBean getComplaint(String orgId, String complaintId) {
        Complaint complaint = complaintService.getComplaint(orgId, complaintId);
        List<ComplaintAttachmentResponseBean> attachments =
                complaintAttachmentService.listAttachmentsForComplaint(orgId, complaintId);
        return ComplaintRecordBean.from(complaint, attachments);
    }

    public ComplaintListResponseBean listComplaints(String orgId, String status, String priority, String userId,
            Integer limit, Integer offset, String sort) {
        return listComplaints(orgId, status, priority, userId, limit, offset, sort, false);
    }

    public ComplaintQueueStatsResponseBean getQueueStats(String orgId) {
        return complaintService.getQueueStats(orgId);
    }

    public CategoryListResponseBean getCategories() {
        Map<String, String> categoryPriorities = new TreeMap<>(PriorityMapper.getCategoryPriorities());
        List<ComplaintCategoryBean> beanList = new ArrayList<>();
        for (Map.Entry<String, String> entry : categoryPriorities.entrySet()) {
            beanList.add(new ComplaintCategoryBean(entry.getKey(), entry.getValue()));
        }
        return new CategoryListResponseBean(beanList);
    }

    public ComplaintStatusUpdateResponseBean updateStatus(String orgId, String complaintId, String actorUserId,
            String actorUserName, String actorRole, ComplaintStatusUpdateRequestBean request) {
        String toStatus = request != null ? request.getToStatus() : null;
        String note = request != null ? request.getNote() : null;

        return complaintEventService.updateStatus(orgId, complaintId, actorUserId, actorUserName, actorRole,
                toStatus, note);
    }

    // ---- Data Principal (/me/complaints/*) ----

    public ComplaintCreateResponseBean createOwnComplaint(String orgId, String ownerUserId, String ownerUserName,
            MeComplaintCreateRequestBean request) {
        String subjectCategory = request != null ? request.getSubjectCategory() : null;
        String description = request != null ? request.getDescription() : null;
        return complaintService.createComplaint(orgId, ownerUserId, ownerUserName, subjectCategory, description);
    }

    public ComplaintRecordBean getOwnComplaint(String orgId, String complaintId, String ownerUserId) {
        Complaint complaint = complaintService.requireOwnedComplaint(orgId, complaintId, ownerUserId);
        List<ComplaintAttachmentResponseBean> attachments =
                complaintAttachmentService.listAttachmentsForComplaint(orgId, complaintId);
        return ComplaintRecordBean.from(complaint, publicOnly(attachments));
    }

    public ComplaintListResponseBean listOwnComplaints(String orgId, String ownerUserId, String status,
            Integer limit, Integer offset, String sort) {
        return listComplaints(orgId, status, null, ownerUserId, limit, offset, sort, true);
    }

    public ComplaintStatusUpdateResponseBean updateOwnStatus(String orgId, String complaintId, String ownerUserId,
            String ownerUserName, MeComplaintStatusUpdateRequestBean request) {
        complaintService.requireOwnedComplaint(orgId, complaintId, ownerUserId);
        String toStatus = request != null ? request.getToStatus() : null;
        return complaintEventService.updateStatus(orgId, complaintId, ownerUserId, ownerUserName, "USER", toStatus,
                null);
    }

    // ---- shared ----

    private ComplaintListResponseBean listComplaints(String orgId, String status, String priority, String userId,
            Integer limit, Integer offset, String sort, boolean restrictToPublicAttachments) {
        int lim = limit != null && limit > 0 ? Math.min(limit, 100) : 10;
        int off = offset != null && offset >= 0 ? offset : 0;
        int[] totalOut = new int[]{0};

        List<Complaint> list = complaintService.listComplaints(orgId, status, priority, userId, lim, off, sort,
                totalOut);

        List<ComplaintRecordBean> beanList = new ArrayList<>();
        for (Complaint complaint : list) {
            List<ComplaintAttachmentResponseBean> attachments = complaintAttachmentService
                    .listAttachmentsForComplaint(orgId, complaint.getComplaintId());
            beanList.add(ComplaintRecordBean.from(complaint,
                    restrictToPublicAttachments ? publicOnly(attachments) : attachments));
        }

        PageMetadataBean metadata = new PageMetadataBean(totalOut[0], off, beanList.size(), lim);
        return new ComplaintListResponseBean(beanList, metadata);
    }

    private List<ComplaintAttachmentResponseBean> publicOnly(List<ComplaintAttachmentResponseBean> attachments) {
        return attachments.stream().filter(ComplaintAttachmentResponseBean::isPublic).collect(Collectors.toList());
    }
}
