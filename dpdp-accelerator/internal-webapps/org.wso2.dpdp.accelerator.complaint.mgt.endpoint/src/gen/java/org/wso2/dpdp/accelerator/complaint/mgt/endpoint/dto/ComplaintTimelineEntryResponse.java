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

package org.wso2.dpdp.accelerator.complaint.mgt.endpoint.dto;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.wso2.dpdp.accelerator.complaint.mgt.endpoint.dto.ComplaintActorRole;
import org.wso2.dpdp.accelerator.complaint.mgt.endpoint.dto.ComplaintAttachmentResponse;
import org.wso2.dpdp.accelerator.complaint.mgt.endpoint.dto.ComplaintStatus;
import org.wso2.dpdp.accelerator.complaint.mgt.endpoint.dto.ComplaintTimelineEntryType;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import java.util.Objects;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Every attachment uploaded alongside this entry (an attachment upload call records exactly one entry and binds every file in that call to it) - empty for entries that are not an attachment upload. Use GET .../attachments/{attachmentId} to download one. 
 */
@ApiModel(description="Every attachment uploaded alongside this entry (an attachment upload call records exactly one entry and binds every file in that call to it) - empty for entries that are not an attachment upload. Use GET .../attachments/{attachmentId} to download one. ")

public class ComplaintTimelineEntryResponse  {
  
  @ApiModelProperty(required = true, value = "")

  private String id;

  @ApiModelProperty(required = true, value = "")

  private ComplaintTimelineEntryType type;

  @ApiModelProperty(required = true, value = "")

  private Boolean isPublic;

  @ApiModelProperty(value = "")

  private String actorUserId;

 /**
  * Human-readable display name for actorUserId, when the identity provider resolved one at the time this entry was recorded. Null for older entries recorded before this field existed, or when no display name could be resolved - callers should fall back to actorUserId for display in that case. 
  */
  @ApiModelProperty(value = "Human-readable display name for actorUserId, when the identity provider resolved one at the time this entry was recorded. Null for older entries recorded before this field existed, or when no display name could be resolved - callers should fall back to actorUserId for display in that case. ")

  private String actorUserName;

  @ApiModelProperty(required = true, value = "")

  private ComplaintActorRole actorRole;

  @ApiModelProperty(required = true, value = "")

  private String message;

  @ApiModelProperty(value = "")

  private ComplaintStatus fromStatus;

  @ApiModelProperty(value = "")

  private ComplaintStatus toStatus;

 /**
  * Unix timestamp, milliseconds.
  */
  @ApiModelProperty(required = true, value = "Unix timestamp, milliseconds.")

  private Long createdTime;

  @ApiModelProperty(required = true, value = "")

  private List<ComplaintAttachmentResponse> attachments;
 /**
   * Get id
   * @return id
  **/
  @JsonProperty("id")
  public String getId() {
    return id;
  }

  public void setId(String id) {
    this.id = id;
  }

  public ComplaintTimelineEntryResponse id(String id) {
    this.id = id;
    return this;
  }

 /**
   * Get type
   * @return type
  **/
  @JsonProperty("type")
  public ComplaintTimelineEntryType getType() {
    return type;
  }

  public void setType(ComplaintTimelineEntryType type) {
    this.type = type;
  }

  public ComplaintTimelineEntryResponse type(ComplaintTimelineEntryType type) {
    this.type = type;
    return this;
  }

 /**
   * Get isPublic
   * @return isPublic
  **/
  @JsonProperty("isPublic")
  public Boolean getIsPublic() {
    return isPublic;
  }

  public void setIsPublic(Boolean isPublic) {
    this.isPublic = isPublic;
  }

  public ComplaintTimelineEntryResponse isPublic(Boolean isPublic) {
    this.isPublic = isPublic;
    return this;
  }

 /**
   * Get actorUserId
   * @return actorUserId
  **/
  @JsonProperty("actorUserId")
  public String getActorUserId() {
    return actorUserId;
  }

  public void setActorUserId(String actorUserId) {
    this.actorUserId = actorUserId;
  }

  public ComplaintTimelineEntryResponse actorUserId(String actorUserId) {
    this.actorUserId = actorUserId;
    return this;
  }

 /**
   * Human-readable display name for actorUserId, when the identity provider resolved one at the time this entry was recorded. Null for older entries recorded before this field existed, or when no display name could be resolved - callers should fall back to actorUserId for display in that case. 
   * @return actorUserName
  **/
  @JsonProperty("actorUserName")
  public String getActorUserName() {
    return actorUserName;
  }

  public void setActorUserName(String actorUserName) {
    this.actorUserName = actorUserName;
  }

  public ComplaintTimelineEntryResponse actorUserName(String actorUserName) {
    this.actorUserName = actorUserName;
    return this;
  }

 /**
   * Get actorRole
   * @return actorRole
  **/
  @JsonProperty("actorRole")
  public ComplaintActorRole getActorRole() {
    return actorRole;
  }

  public void setActorRole(ComplaintActorRole actorRole) {
    this.actorRole = actorRole;
  }

  public ComplaintTimelineEntryResponse actorRole(ComplaintActorRole actorRole) {
    this.actorRole = actorRole;
    return this;
  }

 /**
   * Get message
   * @return message
  **/
  @JsonProperty("message")
  public String getMessage() {
    return message;
  }

  public void setMessage(String message) {
    this.message = message;
  }

  public ComplaintTimelineEntryResponse message(String message) {
    this.message = message;
    return this;
  }

 /**
   * Get fromStatus
   * @return fromStatus
  **/
  @JsonProperty("fromStatus")
  public ComplaintStatus getFromStatus() {
    return fromStatus;
  }

  public void setFromStatus(ComplaintStatus fromStatus) {
    this.fromStatus = fromStatus;
  }

  public ComplaintTimelineEntryResponse fromStatus(ComplaintStatus fromStatus) {
    this.fromStatus = fromStatus;
    return this;
  }

 /**
   * Get toStatus
   * @return toStatus
  **/
  @JsonProperty("toStatus")
  public ComplaintStatus getToStatus() {
    return toStatus;
  }

  public void setToStatus(ComplaintStatus toStatus) {
    this.toStatus = toStatus;
  }

  public ComplaintTimelineEntryResponse toStatus(ComplaintStatus toStatus) {
    this.toStatus = toStatus;
    return this;
  }

 /**
   * Unix timestamp, milliseconds.
   * @return createdTime
  **/
  @JsonProperty("createdTime")
  public Long getCreatedTime() {
    return createdTime;
  }

  public void setCreatedTime(Long createdTime) {
    this.createdTime = createdTime;
  }

  public ComplaintTimelineEntryResponse createdTime(Long createdTime) {
    this.createdTime = createdTime;
    return this;
  }

 /**
   * Get attachments
   * @return attachments
  **/
  @JsonProperty("attachments")
  public List<ComplaintAttachmentResponse> getAttachments() {
    return attachments;
  }

  public void setAttachments(List<ComplaintAttachmentResponse> attachments) {
    this.attachments = attachments;
  }

  public ComplaintTimelineEntryResponse attachments(List<ComplaintAttachmentResponse> attachments) {
    this.attachments = attachments;
    return this;
  }

  public ComplaintTimelineEntryResponse addAttachmentsItem(ComplaintAttachmentResponse attachmentsItem) {
    this.attachments.add(attachmentsItem);
    return this;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    ComplaintTimelineEntryResponse complaintTimelineEntryResponse = (ComplaintTimelineEntryResponse) o;
    return Objects.equals(this.id, complaintTimelineEntryResponse.id) &&
        Objects.equals(this.type, complaintTimelineEntryResponse.type) &&
        Objects.equals(this.isPublic, complaintTimelineEntryResponse.isPublic) &&
        Objects.equals(this.actorUserId, complaintTimelineEntryResponse.actorUserId) &&
        Objects.equals(this.actorUserName, complaintTimelineEntryResponse.actorUserName) &&
        Objects.equals(this.actorRole, complaintTimelineEntryResponse.actorRole) &&
        Objects.equals(this.message, complaintTimelineEntryResponse.message) &&
        Objects.equals(this.fromStatus, complaintTimelineEntryResponse.fromStatus) &&
        Objects.equals(this.toStatus, complaintTimelineEntryResponse.toStatus) &&
        Objects.equals(this.createdTime, complaintTimelineEntryResponse.createdTime) &&
        Objects.equals(this.attachments, complaintTimelineEntryResponse.attachments);
  }

  @Override
  public int hashCode() {
    return Objects.hash(id, type, isPublic, actorUserId, actorUserName, actorRole, message, fromStatus, toStatus, createdTime, attachments);
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append("class ComplaintTimelineEntryResponse {\n");
    
    sb.append("    id: ").append(toIndentedString(id)).append("\n");
    sb.append("    type: ").append(toIndentedString(type)).append("\n");
    sb.append("    isPublic: ").append(toIndentedString(isPublic)).append("\n");
    sb.append("    actorUserId: ").append(toIndentedString(actorUserId)).append("\n");
    sb.append("    actorUserName: ").append(toIndentedString(actorUserName)).append("\n");
    sb.append("    actorRole: ").append(toIndentedString(actorRole)).append("\n");
    sb.append("    message: ").append(toIndentedString(message)).append("\n");
    sb.append("    fromStatus: ").append(toIndentedString(fromStatus)).append("\n");
    sb.append("    toStatus: ").append(toIndentedString(toStatus)).append("\n");
    sb.append("    createdTime: ").append(toIndentedString(createdTime)).append("\n");
    sb.append("    attachments: ").append(toIndentedString(attachments)).append("\n");
    sb.append("}");
    return sb.toString();
  }

  /**
   * Convert the given object to string with each line indented by 4 spaces
   * (except the first line).
   */
  private static String toIndentedString(Object o) {
    return o == null ? "null" : o.toString().replace("\n", "\n    ");
  }
}

