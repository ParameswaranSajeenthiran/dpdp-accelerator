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

import org.glassfish.jersey.media.multipart.FormDataBodyPart;
import org.wso2.dpdp.accelerator.complaint.mgt.dao.constants.ComplaintActorRole;
import org.wso2.dpdp.accelerator.complaint.mgt.service.ComplaintAttachmentService;
import org.wso2.dpdp.accelerator.complaint.mgt.service.dto.ComplaintAttachmentDownloadResponseBean;
import org.wso2.dpdp.accelerator.complaint.mgt.service.dto.ComplaintAttachmentResponseBean;
import org.wso2.dpdp.accelerator.complaint.mgt.service.ComplaintAttachmentService.UploadedFile;
import org.wso2.dpdp.accelerator.complaint.mgt.service.ComplaintService;
import org.wso2.dpdp.accelerator.complaint.mgt.service.exception.ComplaintErrorCode;
import org.wso2.dpdp.accelerator.complaint.mgt.service.exception.ComplaintException;
import org.wso2.dpdp.accelerator.complaint.mgt.service.exception.ComplaintServiceConstants;
import org.wso2.dpdp.accelerator.complaint.mgt.service.impl.ComplaintAttachmentServiceImpl;
import org.wso2.dpdp.accelerator.complaint.mgt.service.impl.ComplaintServiceImpl;
import org.wso2.dpdp.accelerator.complaint.mgt.service.util.AttachmentPolicy;

import javax.ws.rs.core.MediaType;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * Shared business logic behind both /me/complaints/{id}/attachments (Data Principal) and
 * /complaints/{id}/attachments (officer/admin). The "own*" methods enforce complaint ownership and
 * force isPublic=true (upload) / deny access to non-public attachments (download); the plain
 * methods are unrestricted and used only by officer/admin (any-scope) endpoints.
 */
public class ComplaintAttachmentHandler {

    private final ComplaintService complaintService;
    private final ComplaintAttachmentService complaintAttachmentService;

    public ComplaintAttachmentHandler() {
        this.complaintService = new ComplaintServiceImpl();
        this.complaintAttachmentService = new ComplaintAttachmentServiceImpl(complaintService);
    }

    public ComplaintAttachmentHandler(ComplaintService complaintService,
            ComplaintAttachmentService complaintAttachmentService) {
        this.complaintService = complaintService;
        this.complaintAttachmentService = complaintAttachmentService;
    }

    // ---- Officer/admin ----

    public List<ComplaintAttachmentResponseBean> uploadComplaintAttachments(String orgId, String complaintId,
            List<FormDataBodyPart> fileParts, Boolean isPublic, String actorUserId, String actorUserName) {
        List<UploadedFile> files = toUploadedFiles(fileParts);
        return complaintAttachmentService.uploadComplaintAttachments(orgId, complaintId, files,
                isPublic == null || isPublic, actorUserId, actorUserName,
                ComplaintActorRole.COMPLAINT_OFFICER.name());
    }

    public ComplaintAttachmentDownloadResponseBean downloadAttachment(String orgId, String complaintId,
            String attachmentId) {
        return complaintAttachmentService.downloadAttachment(orgId, complaintId, attachmentId, false);
    }

    // ---- Data Principal ----

    public List<ComplaintAttachmentResponseBean> uploadOwnComplaintAttachments(String orgId, String complaintId,
            String ownerUserId, String ownerUserName, List<FormDataBodyPart> fileParts) {
        List<UploadedFile> files = toUploadedFiles(fileParts);
        return complaintAttachmentService.uploadOwnComplaintAttachments(orgId, complaintId, ownerUserId,
                ownerUserName, files);
    }

    public ComplaintAttachmentDownloadResponseBean downloadOwnAttachment(String orgId, String complaintId,
            String ownerUserId, String attachmentId) {
        return complaintAttachmentService.downloadOwnAttachment(orgId, complaintId, ownerUserId, attachmentId);
    }

    // ---- shared ----

    private List<UploadedFile> toUploadedFiles(List<FormDataBodyPart> fileParts) {
        List<UploadedFile> files = new ArrayList<>();
        if (fileParts == null) {
            return files;
        }
        // Enforced here, before a single byte of any part is read, not just later on the
        // materialized list in ComplaintAttachmentServiceImpl#validateFiles - otherwise many
        // individually-small parts would still force this method to buffer all of them in heap
        // before the count is ever checked.
        int maxFiles = AttachmentPolicy.getMaxFilesPerUpload();
        if (fileParts.size() > maxFiles) {
            throw new ComplaintException(ComplaintErrorCode.VALIDATION_FAILED,
                    String.format(ComplaintServiceConstants.TOO_MANY_FILES_ERROR, maxFiles, fileParts.size()));
        }

        for (FormDataBodyPart part : fileParts) {
            String contentType = part.getMediaType() != null
                    ? part.getMediaType().toString()
                    : MediaType.APPLICATION_OCTET_STREAM;
            String fileName = part.getContentDisposition() != null
                    ? part.getContentDisposition().getFileName()
                    : null;
            try (InputStream in = part.getValueAs(InputStream.class)) {
                byte[] data = readAllBytes(in, fileName);
                files.add(new UploadedFile(fileName, contentType, data));
            } catch (IOException e) {
                throw new ComplaintException(ComplaintErrorCode.VALIDATION_FAILED,
                        ComplaintServiceConstants.FILE_READ_FAILED_ERROR);
            }
        }
        return files;
    }

    /**
     * Enforces {@link AttachmentPolicy#getMaxSizeBytes()} while reading, not after - buffering an
     * entire oversized part into a byte[] first (then rejecting it) still lets one request force the
     * JVM to hold the whole thing in heap, defeating the point of a size cap.
     */
    private byte[] readAllBytes(InputStream in, String fileName) throws IOException {
        long maxSize = AttachmentPolicy.getMaxSizeBytes();
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        byte[] chunk = new byte[8192];
        long total = 0;
        int read;
        while ((read = in.read(chunk)) != -1) {
            total += read;
            if (total > maxSize) {
                throw new ComplaintException(ComplaintErrorCode.VALIDATION_FAILED,
                        String.format(ComplaintServiceConstants.FILE_SIZE_EXCEEDED_ERROR, fileName, maxSize));
            }
            buffer.write(chunk, 0, read);
        }
        return buffer.toByteArray();
    }
}
