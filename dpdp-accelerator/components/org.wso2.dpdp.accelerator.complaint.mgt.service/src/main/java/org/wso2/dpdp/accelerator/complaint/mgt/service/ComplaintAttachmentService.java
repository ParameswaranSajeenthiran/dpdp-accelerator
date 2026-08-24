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

package org.wso2.dpdp.accelerator.complaint.mgt.service;

import org.wso2.dpdp.accelerator.complaint.mgt.dao.model.ComplaintAttachment;

import java.util.List;

public interface ComplaintAttachmentService {

    /**
     * Uploads and binds one or more files to the complaint, recording one timeline event for the
     * upload and linking every attachment to it. isPublic controls visibility to the Data
     * Principal via the /me/* endpoints - true (the only value the /me upload endpoint ever
     * passes) is visible there, false marks officer-internal evidence. actorUserId/actorRole
     * identify who performed the upload, the same as {@code ComplaintEventService#addComment}.
     */
    List<ComplaintAttachment> uploadComplaintAttachments(String orgId, String complaintId,
            List<UploadedFile> files, boolean isPublic, String actorUserId, String actorUserName, String actorRole);

    /**
     * Same as {@link #uploadComplaintAttachments}, for the /me/* upload endpoint - additionally
     * verifies the complaint belongs to ownerUserId before uploading (isPublic is always true,
     * actorRole always USER). This is defense-in-depth alongside the handler's own ownership check
     * - the service must not rely solely on callers remembering to check first.
     */
    List<ComplaintAttachment> uploadOwnComplaintAttachments(String orgId, String complaintId, String ownerUserId,
            String ownerUserName, List<UploadedFile> files);

    /** Metadata (no file content) for attachments bound to the complaint. */
    List<ComplaintAttachment> listAttachmentsForComplaint(String orgId, String complaintId);

    /**
     * Downloads an attachment including its file content. When restrictToPublicOnly is true (the
     * /me/* download endpoint), a 403 is raised if the attachment's isPublic flag is false - this
     * is how officer-internal evidence stays hidden from the Data Principal. Officer/admin callers
     * pass false and see every attachment regardless of isPublic.
     */
    ComplaintAttachment downloadAttachment(String orgId, String complaintId, String attachmentId,
            boolean restrictToPublicOnly);

    /**
     * Same as {@code downloadAttachment(orgId, complaintId, attachmentId, true)}, for the /me/*
     * download endpoint - additionally verifies the complaint belongs to ownerUserId first, as
     * defense-in-depth alongside the handler's own ownership check.
     */
    ComplaintAttachment downloadOwnAttachment(String orgId, String complaintId, String ownerUserId,
            String attachmentId);

    /** A single uploaded multipart file, decoupled from any particular HTTP framework's bean type. */
    class UploadedFile {
        private final String fileName;
        private final String contentType;
        private final byte[] data;

        public UploadedFile(String fileName, String contentType, byte[] data) {
            this.fileName = fileName;
            this.contentType = contentType;
            this.data = data;
        }

        public String getFileName() {
            return fileName;
        }

        public String getContentType() {
            return contentType;
        }

        public byte[] getData() {
            return data;
        }
    }
}
