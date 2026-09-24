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
import org.wso2.dpdp.accelerator.complaint.mgt.endpoint.dto.ComplaintStatus;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import java.util.Objects;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Pure-JSON body for POST /me/complaints/{complaintId}/comments. actorRole is implicitly USER and isPublic is implicitly true — a normal user cannot post an internal note. toStatus is optional; when supplied, the complaint is transitioned to that status in the same call (see MeComplaintStatusUpdateRequest for the status-only equivalent). 
 */
@ApiModel(description="Pure-JSON body for POST /me/complaints/{complaintId}/comments. actorRole is implicitly USER and isPublic is implicitly true — a normal user cannot post an internal note. toStatus is optional; when supplied, the complaint is transitioned to that status in the same call (see MeComplaintStatusUpdateRequest for the status-only equivalent). ")

public class MeComplaintMessageRequest  {
  
  @ApiModelProperty(required = true, value = "")

  private String message;

 /**
  * Optional. If present, transitions the complaint to this status in the same call as posting the reply. Omit for a plain reply with no status change. 
  */
  @ApiModelProperty(value = "Optional. If present, transitions the complaint to this status in the same call as posting the reply. Omit for a plain reply with no status change. ")

  private ComplaintStatus toStatus;
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

  public MeComplaintMessageRequest message(String message) {
    this.message = message;
    return this;
  }

 /**
   * Optional. If present, transitions the complaint to this status in the same call as posting the reply. Omit for a plain reply with no status change. 
   * @return toStatus
  **/
  @JsonProperty("toStatus")
  public ComplaintStatus getToStatus() {
    return toStatus;
  }

  public void setToStatus(ComplaintStatus toStatus) {
    this.toStatus = toStatus;
  }

  public MeComplaintMessageRequest toStatus(ComplaintStatus toStatus) {
    this.toStatus = toStatus;
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
    MeComplaintMessageRequest meComplaintMessageRequest = (MeComplaintMessageRequest) o;
    return Objects.equals(this.message, meComplaintMessageRequest.message) &&
        Objects.equals(this.toStatus, meComplaintMessageRequest.toStatus);
  }

  @Override
  public int hashCode() {
    return Objects.hash(message, toStatus);
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append("class MeComplaintMessageRequest {\n");
    
    sb.append("    message: ").append(toIndentedString(message)).append("\n");
    sb.append("    toStatus: ").append(toIndentedString(toStatus)).append("\n");
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

