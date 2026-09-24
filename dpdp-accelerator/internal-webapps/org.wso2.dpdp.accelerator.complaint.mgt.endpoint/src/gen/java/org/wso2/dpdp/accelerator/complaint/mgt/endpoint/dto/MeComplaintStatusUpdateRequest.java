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
 * Pure-JSON body for POST /me/complaints/{complaintId}/status. Only toStatus is accepted — no note field, and no actorUserId/actorRole (the caller's identity, role USER, is resolved from the bearer token, same as every other /me endpoint). Subject to the same state-machine rules as POST /complaints/{complaintId}/status (invalid transitions return 409 CO-4090). 
 */
@ApiModel(description="Pure-JSON body for POST /me/complaints/{complaintId}/status. Only toStatus is accepted — no note field, and no actorUserId/actorRole (the caller's identity, role USER, is resolved from the bearer token, same as every other /me endpoint). Subject to the same state-machine rules as POST /complaints/{complaintId}/status (invalid transitions return 409 CO-4090). ")

public class MeComplaintStatusUpdateRequest  {
  
  @ApiModelProperty(required = true, value = "")

  private ComplaintStatus toStatus;
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

  public MeComplaintStatusUpdateRequest toStatus(ComplaintStatus toStatus) {
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
    MeComplaintStatusUpdateRequest meComplaintStatusUpdateRequest = (MeComplaintStatusUpdateRequest) o;
    return Objects.equals(this.toStatus, meComplaintStatusUpdateRequest.toStatus);
  }

  @Override
  public int hashCode() {
    return Objects.hash(toStatus);
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append("class MeComplaintStatusUpdateRequest {\n");
    
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

