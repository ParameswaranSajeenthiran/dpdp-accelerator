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
 * Pure-JSON body for POST /complaints/{complaintId}/comments. No actorUserId/actorRole — the acting officer/system's identity and role are resolved server-side from the caller's bearer token, same as the /me endpoints. toStatus is optional; when supplied, the complaint is transitioned to that status as part of the same call and the transition is recorded on this same timeline entry. 
 */
@ApiModel(description="Pure-JSON body for POST /complaints/{complaintId}/comments. No actorUserId/actorRole — the acting officer/system's identity and role are resolved server-side from the caller's bearer token, same as the /me endpoints. toStatus is optional; when supplied, the complaint is transitioned to that status as part of the same call and the transition is recorded on this same timeline entry. ")

public class ComplaintMessageRequest  {
  
  @ApiModelProperty(required = true, value = "")

  private String message;

 /**
  * true to post a shared reply visible to the Data Principal; false for an officer-internal note. 
  */
  @ApiModelProperty(required = true, value = "true to post a shared reply visible to the Data Principal; false for an officer-internal note. ")

  private Boolean isPublic;

  @ApiModelProperty(value = "")

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

  public ComplaintMessageRequest message(String message) {
    this.message = message;
    return this;
  }

 /**
   * true to post a shared reply visible to the Data Principal; false for an officer-internal note. 
   * @return isPublic
  **/
  @JsonProperty("isPublic")
  public Boolean getIsPublic() {
    return isPublic;
  }

  public void setIsPublic(Boolean isPublic) {
    this.isPublic = isPublic;
  }

  public ComplaintMessageRequest isPublic(Boolean isPublic) {
    this.isPublic = isPublic;
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

  public ComplaintMessageRequest toStatus(ComplaintStatus toStatus) {
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
    ComplaintMessageRequest complaintMessageRequest = (ComplaintMessageRequest) o;
    return Objects.equals(this.message, complaintMessageRequest.message) &&
        Objects.equals(this.isPublic, complaintMessageRequest.isPublic) &&
        Objects.equals(this.toStatus, complaintMessageRequest.toStatus);
  }

  @Override
  public int hashCode() {
    return Objects.hash(message, isPublic, toStatus);
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append("class ComplaintMessageRequest {\n");
    
    sb.append("    message: ").append(toIndentedString(message)).append("\n");
    sb.append("    isPublic: ").append(toIndentedString(isPublic)).append("\n");
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

