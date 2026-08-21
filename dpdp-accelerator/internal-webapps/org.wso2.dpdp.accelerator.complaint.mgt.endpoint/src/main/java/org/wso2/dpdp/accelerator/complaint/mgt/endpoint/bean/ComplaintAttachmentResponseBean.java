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

package org.wso2.dpdp.accelerator.complaint.mgt.endpoint.bean;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.wso2.dpdp.accelerator.complaint.mgt.dao.model.ComplaintAttachment;
import org.wso2.dpdp.accelerator.complaint.mgt.endpoint.util.DateTimeUtil;

public class ComplaintAttachmentResponseBean {

    private String attachmentId;
    private String complaintEventId;
    private String fileName;
    private String contentType;
    private long sizeBytes;
    private boolean isPublic;
    private String uploadedTime;

    public ComplaintAttachmentResponseBean() {
    }

    public ComplaintAttachmentResponseBean(String attachmentId, String complaintEventId, String fileName,
            String contentType, long sizeBytes, boolean isPublic, String uploadedTime) {
        this.attachmentId = attachmentId;
        this.complaintEventId = complaintEventId;
        this.fileName = fileName;
        this.contentType = contentType;
        this.sizeBytes = sizeBytes;
        this.isPublic = isPublic;
        this.uploadedTime = uploadedTime;
    }

    public static ComplaintAttachmentResponseBean from(ComplaintAttachment attachment) {
        return new ComplaintAttachmentResponseBean(attachment.getAttachmentId(), attachment.getComplaintEventId(),
                attachment.getFileName(), attachment.getContentType(), attachment.getSizeBytes(),
                attachment.isPublic(), DateTimeUtil.toIso(attachment.getCreatedTime()));
    }

    public String getAttachmentId() {
        return attachmentId;
    }

    public void setAttachmentId(String attachmentId) {
        this.attachmentId = attachmentId;
    }

    public String getComplaintEventId() {
        return complaintEventId;
    }

    public void setComplaintEventId(String complaintEventId) {
        this.complaintEventId = complaintEventId;
    }

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public String getContentType() {
        return contentType;
    }

    public void setContentType(String contentType) {
        this.contentType = contentType;
    }

    public long getSizeBytes() {
        return sizeBytes;
    }

    public void setSizeBytes(long sizeBytes) {
        this.sizeBytes = sizeBytes;
    }

    @JsonProperty("isPublic")
    public boolean isPublic() {
        return isPublic;
    }

    @JsonProperty("isPublic")
    public void setPublic(boolean isPublic) {
        this.isPublic = isPublic;
    }

    public String getUploadedTime() {
        return uploadedTime;
    }

    public void setUploadedTime(String uploadedTime) {
        this.uploadedTime = uploadedTime;
    }
}
