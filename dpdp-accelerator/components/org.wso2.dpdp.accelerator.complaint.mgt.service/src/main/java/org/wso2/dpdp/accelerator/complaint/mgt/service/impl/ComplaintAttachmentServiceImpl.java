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

package org.wso2.dpdp.accelerator.complaint.mgt.service.impl;

import org.wso2.dpdp.accelerator.complaint.mgt.dao.ComplaintAttachmentDAO;
import org.wso2.dpdp.accelerator.complaint.mgt.dao.ComplaintEventDAO;
import org.wso2.dpdp.accelerator.complaint.mgt.dao.constants.ComplaintActorRole;
import org.wso2.dpdp.accelerator.complaint.mgt.dao.impl.ComplaintAttachmentDAOImpl;
import org.wso2.dpdp.accelerator.complaint.mgt.dao.impl.ComplaintEventDAOImpl;
import org.wso2.dpdp.accelerator.complaint.mgt.dao.model.ComplaintAttachment;
import org.wso2.dpdp.accelerator.complaint.mgt.dao.model.ComplaintEvent;
import org.wso2.dpdp.accelerator.complaint.mgt.service.ComplaintAttachmentService;
import org.wso2.dpdp.accelerator.complaint.mgt.service.ComplaintService;
import org.wso2.dpdp.accelerator.complaint.mgt.service.exception.ComplaintErrorCode;
import org.wso2.dpdp.accelerator.complaint.mgt.service.exception.ComplaintException;
import org.wso2.dpdp.accelerator.complaint.mgt.service.exception.ComplaintServiceConstants;
import org.wso2.dpdp.accelerator.complaint.mgt.service.util.AttachmentPolicy;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public class ComplaintAttachmentServiceImpl implements ComplaintAttachmentService {

    private final ComplaintAttachmentDAO attachmentDAO;
    private final ComplaintEventDAO complaintEventDAO;
    private final ComplaintService complaintService;

    public ComplaintAttachmentServiceImpl(ComplaintService complaintService) {
        this.attachmentDAO = new ComplaintAttachmentDAOImpl();
        this.complaintEventDAO = new ComplaintEventDAOImpl();
        this.complaintService = complaintService;
    }

    public ComplaintAttachmentServiceImpl(ComplaintAttachmentDAO attachmentDAO, ComplaintEventDAO complaintEventDAO,
            ComplaintService complaintService) {
        this.attachmentDAO = attachmentDAO;
        this.complaintEventDAO = complaintEventDAO;
        this.complaintService = complaintService;
    }

    @Override
    public List<ComplaintAttachment> uploadComplaintAttachments(String orgId, String complaintId,
            List<UploadedFile> files, boolean isPublic, String actorUserId, String actorUserName,
            String actorRole) {
        complaintService.requireComplaint(orgId, complaintId);
        validateFiles(files);
        validateActor(actorUserId, actorRole);

        long now = System.currentTimeMillis();
        String complaintEventId = recordUploadEvent(orgId, complaintId, isPublic, actorUserId, actorUserName,
                actorRole, now);

        List<ComplaintAttachment> result = new ArrayList<>();
        for (UploadedFile file : files) {
            result.add(store(orgId, complaintId, complaintEventId, file, isPublic, now));
        }
        return result;
    }

    private void validateActor(String actorUserId, String actorRole) {
        if (actorUserId == null || actorUserId.trim().isEmpty()) {
            throw new ComplaintException(ComplaintErrorCode.VALIDATION_FAILED,
                    ComplaintServiceConstants.ACTOR_USER_ID_REQUIRED_ERROR);
        }
        // SYSTEM is deliberately excluded - only ever written by the server itself, the same
        // restriction ComplaintEventServiceImpl#addComment applies to caller-supplied actor roles.
        if (!ComplaintActorRole.USER.name().equals(actorRole)
                && !ComplaintActorRole.COMPLAINT_OFFICER.name().equals(actorRole)) {
            throw new ComplaintException(ComplaintErrorCode.VALIDATION_FAILED,
                    ComplaintServiceConstants.ACTOR_ROLE_INVALID_ERROR);
        }
    }

    private String recordUploadEvent(String orgId, String complaintId, boolean isPublic,
            String actorUserId, String actorUserName, String actorRole, long now) {
        String complaintEventId = UUID.randomUUID().toString();
        // No comment text - this event exists purely to anchor the uploaded attachments on the
        // timeline; the attachments themselves (via ComplaintAttachment#complaintEventId) are what
        // the UI renders under it.
        ComplaintEvent event = new ComplaintEvent(complaintEventId, orgId, complaintId, actorUserId, actorUserName,
                actorRole, isPublic, null, null, null, now);

        boolean added = complaintEventDAO.addEvent(event);
        if (!added) {
            throw new ComplaintException(ComplaintErrorCode.INTERNAL_ERROR,
                    ComplaintServiceConstants.ATTACHMENT_EVENT_STORE_FAILED_ERROR);
        }
        return complaintEventId;
    }

    @Override
    public List<ComplaintAttachment> listAttachmentsForComplaint(String orgId, String complaintId) {
        return attachmentDAO.listAttachmentsForComplaint(orgId, complaintId);
    }

    @Override
    public ComplaintAttachment downloadAttachment(String orgId, String complaintId, String attachmentId,
            boolean restrictToPublicOnly) {
        Optional<ComplaintAttachment> attachmentOpt =
                attachmentDAO.getAttachmentWithDataById(attachmentId, orgId, complaintId);
        if (attachmentOpt.isEmpty()) {
            throw new ComplaintException(ComplaintErrorCode.ATTACHMENT_NOT_FOUND,
                    String.format(ComplaintServiceConstants.ATTACHMENT_NOT_FOUND_ERROR, attachmentId));
        }
        ComplaintAttachment attachment = attachmentOpt.get();

        if (restrictToPublicOnly && !attachment.isPublic()) {
            throw new ComplaintException(ComplaintErrorCode.FORBIDDEN,
                    ComplaintServiceConstants.INTERNAL_ATTACHMENT_ACCESS_DENIED_ERROR);
        }

        return attachment;
    }

    private void validateFiles(List<UploadedFile> files) {
        if (files == null || files.isEmpty()) {
            throw new ComplaintException(ComplaintErrorCode.VALIDATION_FAILED,
                    ComplaintServiceConstants.FILE_LIST_REQUIRED_ERROR);
        }
        long maxSize = AttachmentPolicy.getMaxSizeBytes();
        for (UploadedFile file : files) {
            if (file.getData() == null || file.getData().length == 0) {
                throw new ComplaintException(ComplaintErrorCode.VALIDATION_FAILED,
                        ComplaintServiceConstants.UPLOADED_FILE_EMPTY_ERROR);
            }
            if (!AttachmentPolicy.isAllowedContentType(file.getContentType())) {
                throw new ComplaintException(ComplaintErrorCode.VALIDATION_FAILED,
                        String.format(ComplaintServiceConstants.UNSUPPORTED_CONTENT_TYPE_ERROR,
                                file.getContentType()));
            }
            if (file.getData().length > maxSize) {
                throw new ComplaintException(ComplaintErrorCode.VALIDATION_FAILED,
                        String.format(ComplaintServiceConstants.FILE_SIZE_EXCEEDED_ERROR, file.getFileName(),
                                maxSize));
            }
        }
    }

    private ComplaintAttachment store(String orgId, String complaintId, String complaintEventId, UploadedFile file,
            boolean isPublic, long now) {
        String attachmentId = UUID.randomUUID().toString();
        ComplaintAttachment attachment = new ComplaintAttachment(attachmentId, orgId, complaintId,
                file.getFileName(), file.getContentType(), file.getData(), isPublic, now);
        attachment.setComplaintEventId(complaintEventId);

        boolean added = attachmentDAO.addAttachment(attachment);
        if (!added) {
            throw new ComplaintException(ComplaintErrorCode.INTERNAL_ERROR,
                    ComplaintServiceConstants.ATTACHMENT_STORE_FAILED_ERROR);
        }
        return attachment;
    }
}
