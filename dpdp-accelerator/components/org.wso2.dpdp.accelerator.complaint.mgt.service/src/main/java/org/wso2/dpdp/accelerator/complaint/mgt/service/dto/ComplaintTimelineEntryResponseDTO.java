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

package org.wso2.dpdp.accelerator.complaint.mgt.service.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.wso2.dpdp.accelerator.complaint.mgt.dao.model.ComplaintEvent;

import java.util.Collections;
import java.util.List;

public class ComplaintTimelineEntryResponseDTO {

    private String id;
    private String type;
    private boolean isPublic;
    private String actorUserId;
    private String actorUserName;
    private String actorRole;
    private String message;
    private String fromStatus;
    private String toStatus;
    private long createdTime;
    private List<ComplaintAttachmentResponseDTO> attachments;

    public ComplaintTimelineEntryResponseDTO() {
    }

    /** No attachments - see {@link #from(ComplaintEvent, List)} for an entry that has any. */
    public static ComplaintTimelineEntryResponseDTO from(ComplaintEvent event) {
        return from(event, Collections.emptyList());
    }

    /** attachments are the ones bound to this event - see ComplaintAttachmentServiceImpl#uploadComplaintAttachments. */
    public static ComplaintTimelineEntryResponseDTO from(ComplaintEvent event,
            List<ComplaintAttachmentResponseDTO> attachments) {
        ComplaintTimelineEntryResponseDTO bean = new ComplaintTimelineEntryResponseDTO();
        bean.id = event.getComplaintEventId();
        bean.type = event.deriveEntryType();
        bean.isPublic = event.isPublic();
        bean.actorUserId = event.getActorUserId();
        bean.actorUserName = event.getActorUserName();
        bean.actorRole = event.getActorRole();
        bean.message = event.getComment();
        bean.fromStatus = event.getFromStatus();
        bean.toStatus = event.getToStatus();
        bean.createdTime = event.getActionTime();
        bean.attachments = attachments;
        return bean;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    @JsonProperty("isPublic")
    public boolean isPublic() {
        return isPublic;
    }

    @JsonProperty("isPublic")
    public void setPublic(boolean isPublic) {
        this.isPublic = isPublic;
    }

    public String getActorUserId() {
        return actorUserId;
    }

    public void setActorUserId(String actorUserId) {
        this.actorUserId = actorUserId;
    }

    public String getActorUserName() {
        return actorUserName;
    }

    public void setActorUserName(String actorUserName) {
        this.actorUserName = actorUserName;
    }

    public String getActorRole() {
        return actorRole;
    }

    public void setActorRole(String actorRole) {
        this.actorRole = actorRole;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public String getFromStatus() {
        return fromStatus;
    }

    public void setFromStatus(String fromStatus) {
        this.fromStatus = fromStatus;
    }

    public String getToStatus() {
        return toStatus;
    }

    public void setToStatus(String toStatus) {
        this.toStatus = toStatus;
    }

    public long getCreatedTime() {
        return createdTime;
    }

    public void setCreatedTime(long createdTime) {
        this.createdTime = createdTime;
    }

    public List<ComplaintAttachmentResponseDTO> getAttachments() {
        return attachments;
    }

    public void setAttachments(List<ComplaintAttachmentResponseDTO> attachments) {
        this.attachments = attachments;
    }
}
