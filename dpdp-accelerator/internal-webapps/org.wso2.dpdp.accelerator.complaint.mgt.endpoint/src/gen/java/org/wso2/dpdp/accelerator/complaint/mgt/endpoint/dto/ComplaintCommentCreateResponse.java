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
import org.wso2.dpdp.accelerator.complaint.mgt.endpoint.dto.ComplaintActorRole;
import org.wso2.dpdp.accelerator.complaint.mgt.endpoint.dto.ComplaintStatus;

import io.swagger.annotations.ApiModelProperty;
import java.util.Objects;
import com.fasterxml.jackson.annotation.JsonProperty;


public class ComplaintCommentCreateResponse  {
  
  @ApiModelProperty(required = true, value = "")

  private String id;

  @ApiModelProperty(required = true, value = "")

  private String actorUserId;

  @ApiModelProperty(required = true, value = "")

  private ComplaintActorRole actorRole;

  @ApiModelProperty(required = true, value = "")

  private String message;

  @ApiModelProperty(required = true, value = "")

  private Boolean isPublic;

  @ApiModelProperty(required = true, value = "")

  private ComplaintStatus fromStatus;

  @ApiModelProperty(required = true, value = "")

  private ComplaintStatus toStatus;

 /**
  * Unix timestamp, milliseconds.
  */
  @ApiModelProperty(required = true, value = "Unix timestamp, milliseconds.")

  private Long createdTime;
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

  public ComplaintCommentCreateResponse id(String id) {
    this.id = id;
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

  public ComplaintCommentCreateResponse actorUserId(String actorUserId) {
    this.actorUserId = actorUserId;
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

  public ComplaintCommentCreateResponse actorRole(ComplaintActorRole actorRole) {
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

  public ComplaintCommentCreateResponse message(String message) {
    this.message = message;
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

  public ComplaintCommentCreateResponse isPublic(Boolean isPublic) {
    this.isPublic = isPublic;
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

  public ComplaintCommentCreateResponse fromStatus(ComplaintStatus fromStatus) {
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

  public ComplaintCommentCreateResponse toStatus(ComplaintStatus toStatus) {
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

  public ComplaintCommentCreateResponse createdTime(Long createdTime) {
    this.createdTime = createdTime;
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
    ComplaintCommentCreateResponse complaintCommentCreateResponse = (ComplaintCommentCreateResponse) o;
    return Objects.equals(this.id, complaintCommentCreateResponse.id) &&
        Objects.equals(this.actorUserId, complaintCommentCreateResponse.actorUserId) &&
        Objects.equals(this.actorRole, complaintCommentCreateResponse.actorRole) &&
        Objects.equals(this.message, complaintCommentCreateResponse.message) &&
        Objects.equals(this.isPublic, complaintCommentCreateResponse.isPublic) &&
        Objects.equals(this.fromStatus, complaintCommentCreateResponse.fromStatus) &&
        Objects.equals(this.toStatus, complaintCommentCreateResponse.toStatus) &&
        Objects.equals(this.createdTime, complaintCommentCreateResponse.createdTime);
  }

  @Override
  public int hashCode() {
    return Objects.hash(id, actorUserId, actorRole, message, isPublic, fromStatus, toStatus, createdTime);
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append("class ComplaintCommentCreateResponse {\n");
    
    sb.append("    id: ").append(toIndentedString(id)).append("\n");
    sb.append("    actorUserId: ").append(toIndentedString(actorUserId)).append("\n");
    sb.append("    actorRole: ").append(toIndentedString(actorRole)).append("\n");
    sb.append("    message: ").append(toIndentedString(message)).append("\n");
    sb.append("    isPublic: ").append(toIndentedString(isPublic)).append("\n");
    sb.append("    fromStatus: ").append(toIndentedString(fromStatus)).append("\n");
    sb.append("    toStatus: ").append(toIndentedString(toStatus)).append("\n");
    sb.append("    createdTime: ").append(toIndentedString(createdTime)).append("\n");
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

