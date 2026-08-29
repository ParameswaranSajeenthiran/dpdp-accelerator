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

import org.wso2.dpdp.accelerator.common.util.DatabaseUtils;
import org.wso2.dpdp.accelerator.complaint.mgt.dao.ComplaintAttachmentDAO;
import org.wso2.dpdp.accelerator.complaint.mgt.dao.ComplaintEventDAO;
import org.wso2.dpdp.accelerator.complaint.mgt.dao.constants.ComplaintActorRole;
import org.wso2.dpdp.accelerator.complaint.mgt.dao.model.ComplaintAttachment;
import org.wso2.dpdp.accelerator.complaint.mgt.dao.model.ComplaintEvent;
import org.wso2.dpdp.accelerator.complaint.mgt.service.ComplaintAttachmentService;
import org.wso2.dpdp.accelerator.complaint.mgt.service.ComplaintService;
import org.wso2.dpdp.accelerator.complaint.mgt.service.dto.ComplaintAttachmentDownloadResponseDTO;
import org.wso2.dpdp.accelerator.complaint.mgt.service.dto.ComplaintAttachmentResponseDTO;
import org.wso2.dpdp.accelerator.complaint.mgt.service.exception.ComplaintErrorCode;
import org.wso2.dpdp.accelerator.complaint.mgt.service.exception.ComplaintException;
import org.wso2.dpdp.accelerator.complaint.mgt.service.exception.ComplaintServiceConstants;
import org.wso2.dpdp.accelerator.complaint.mgt.service.util.AttachmentPolicy;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public class ComplaintAttachmentServiceImpl implements ComplaintAttachmentService {

    private final ComplaintAttachmentDAO attachmentDAO;
    private final ComplaintEventDAO complaintEventDAO;
    private final ComplaintService complaintService;

    public ComplaintAttachmentServiceImpl(ComplaintAttachmentDAO attachmentDAO, ComplaintEventDAO complaintEventDAO,
            ComplaintService complaintService) {
        this.attachmentDAO = attachmentDAO;
        this.complaintEventDAO = complaintEventDAO;
        this.complaintService = complaintService;
    }

    @Override
    public List<ComplaintAttachmentResponseDTO> uploadComplaintAttachments(String orgId, String complaintId,
            List<UploadedFile> files, boolean isPublic, String actorUserId, String actorUserName,
            String actorRole) {
        complaintService.requireComplaint(orgId, complaintId);
        validateFiles(files);
        validateActor(actorUserId, actorRole);

        long now = System.currentTimeMillis();

        // The upload event and every attachment it anchors must land together - see
        // DatabaseUtils#commitTransaction/rollbackTransaction - otherwise a failure partway
        // through a multi-file upload could leave some attachments stored against an event that
        // was never actually committed, or vice versa.
        List<ComplaintAttachment> stored = new ArrayList<>();
        Connection conn = DatabaseUtils.getDBConnection();
        try {
            String complaintEventId = recordUploadEvent(conn, orgId, complaintId, isPublic, actorUserId,
                    actorUserName, actorRole, now);
            for (UploadedFile file : files) {
                stored.add(store(conn, orgId, complaintId, complaintEventId, file, isPublic, now));
            }
            DatabaseUtils.commitTransaction(conn);
        } catch (RuntimeException e) {
            DatabaseUtils.rollbackTransaction(conn);
            throw e;
        } catch (SQLException e) {
            DatabaseUtils.rollbackTransaction(conn);
            throw new ComplaintException(ComplaintErrorCode.INTERNAL_ERROR,
                    ComplaintServiceConstants.ATTACHMENT_STORE_FAILED_ERROR, e);
        } finally {
            DatabaseUtils.closeConnection(conn);
        }

        List<ComplaintAttachmentResponseDTO> result = new ArrayList<>();
        for (ComplaintAttachment attachment : stored) {
            result.add(ComplaintAttachmentResponseDTO.from(attachment));
        }
        return result;
    }

    @Override
    public List<ComplaintAttachmentResponseDTO> uploadOwnComplaintAttachments(String orgId, String complaintId,
            String ownerUserId, String ownerUserName, List<UploadedFile> files) {
        complaintService.requireOwnedComplaint(orgId, complaintId, ownerUserId);
        return uploadComplaintAttachments(orgId, complaintId, files, true, ownerUserId, ownerUserName,
                ComplaintActorRole.USER.name());
    }

    @Override
    public ComplaintAttachmentDownloadResponseDTO downloadOwnAttachment(String orgId, String complaintId,
            String ownerUserId, String attachmentId) {
        complaintService.requireOwnedComplaint(orgId, complaintId, ownerUserId);
        return downloadAttachment(orgId, complaintId, attachmentId, true);
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

    private String recordUploadEvent(Connection conn, String orgId, String complaintId, boolean isPublic,
            String actorUserId, String actorUserName, String actorRole, long now) throws SQLException {
        String complaintEventId = UUID.randomUUID().toString();
        // No comment text - this event exists purely to anchor the uploaded attachments on the
        // timeline; the attachments themselves (via ComplaintAttachment#complaintEventId) are what
        // the UI renders under it.
        ComplaintEvent event = new ComplaintEvent(complaintEventId, orgId, complaintId, actorUserId, actorUserName,
                actorRole, isPublic, null, null, null, now);

        boolean added = complaintEventDAO.addEvent(conn, event);
        if (!added) {
            throw new ComplaintException(ComplaintErrorCode.INTERNAL_ERROR,
                    ComplaintServiceConstants.ATTACHMENT_EVENT_STORE_FAILED_ERROR);
        }
        return complaintEventId;
    }

    @Override
    public List<ComplaintAttachmentResponseDTO> listAttachmentsForComplaint(String orgId, String complaintId) {
        List<ComplaintAttachmentResponseDTO> beans = new ArrayList<>();
        for (ComplaintAttachment attachment : attachmentDAO.listAttachmentsForComplaint(orgId, complaintId)) {
            beans.add(ComplaintAttachmentResponseDTO.from(attachment));
        }
        return beans;
    }

    @Override
    public ComplaintAttachmentDownloadResponseDTO downloadAttachment(String orgId, String complaintId,
            String attachmentId, boolean restrictToPublicOnly) {
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

        return new ComplaintAttachmentDownloadResponseDTO(attachment.getAttachmentId(), attachment.getFileName(),
                attachment.getContentType(), attachment.getFileData());
    }

    private void validateFiles(List<UploadedFile> files) {
        if (files == null || files.isEmpty()) {
            throw new ComplaintException(ComplaintErrorCode.VALIDATION_FAILED,
                    ComplaintServiceConstants.FILE_LIST_REQUIRED_ERROR);
        }
        int maxFiles = AttachmentPolicy.getMaxFilesPerUpload();
        if (files.size() > maxFiles) {
            throw new ComplaintException(ComplaintErrorCode.VALIDATION_FAILED,
                    String.format(ComplaintServiceConstants.TOO_MANY_FILES_ERROR, maxFiles, files.size()));
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

    private ComplaintAttachment store(Connection conn, String orgId, String complaintId, String complaintEventId,
            UploadedFile file, boolean isPublic, long now) throws SQLException {
        String attachmentId = UUID.randomUUID().toString();
        ComplaintAttachment attachment = new ComplaintAttachment(attachmentId, orgId, complaintId,
                file.getFileName(), file.getContentType(), file.getData(), isPublic, now);
        attachment.setComplaintEventId(complaintEventId);

        boolean added = attachmentDAO.addAttachment(conn, attachment);
        if (!added) {
            throw new ComplaintException(ComplaintErrorCode.INTERNAL_ERROR,
                    ComplaintServiceConstants.ATTACHMENT_STORE_FAILED_ERROR);
        }
        return attachment;
    }
}
