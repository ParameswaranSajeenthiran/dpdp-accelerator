/**
 * Copyright (c) 2026, WSO2 LLC. (https://www.wso2.com).
 * <p>
 * WSO2 LLC. licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 *     http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.wso2.dpdp.accelerator.consent.mgt.extensions.endpoint.dto;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import org.wso2.dpdp.accelerator.consent.mgt.extensions.endpoint.dto.ActionType;

import io.swagger.annotations.ApiModelProperty;
import java.util.Objects;
import com.fasterxml.jackson.annotation.JsonProperty;

@com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)

public class StatusAuditEntryDTO  {
  
  @ApiModelProperty(value = "Absent for the initial CREATE entry.")
 /**
   * Absent for the initial CREATE entry.
  **/
  private String previousStatus;

  @ApiModelProperty(value = "")
  private String currentStatus;

  @ApiModelProperty(value = "")
  private ActionType actionType;

  @ApiModelProperty(value = "")
  private String actionBy;

  @ApiModelProperty(value = "Epoch milliseconds.")
 /**
   * Epoch milliseconds.
  **/
  private Long actionTime;
 /**
   * Absent for the initial CREATE entry.
   * @return previousStatus
  **/
  @JsonProperty("previousStatus")
  public String getPreviousStatus() {
    return previousStatus;
  }

  public void setPreviousStatus(String previousStatus) {
    this.previousStatus = previousStatus;
  }

  public StatusAuditEntryDTO previousStatus(String previousStatus) {
    this.previousStatus = previousStatus;
    return this;
  }

 /**
   * Get currentStatus
   * @return currentStatus
  **/
  @JsonProperty("currentStatus")
  public String getCurrentStatus() {
    return currentStatus;
  }

  public void setCurrentStatus(String currentStatus) {
    this.currentStatus = currentStatus;
  }

  public StatusAuditEntryDTO currentStatus(String currentStatus) {
    this.currentStatus = currentStatus;
    return this;
  }

 /**
   * Get actionType
   * @return actionType
  **/
  @JsonProperty("actionType")
  public ActionType getActionType() {
    return actionType;
  }

  public void setActionType(ActionType actionType) {
    this.actionType = actionType;
  }

  public StatusAuditEntryDTO actionType(ActionType actionType) {
    this.actionType = actionType;
    return this;
  }

 /**
   * Get actionBy
   * @return actionBy
  **/
  @JsonProperty("actionBy")
  public String getActionBy() {
    return actionBy;
  }

  public void setActionBy(String actionBy) {
    this.actionBy = actionBy;
  }

  public StatusAuditEntryDTO actionBy(String actionBy) {
    this.actionBy = actionBy;
    return this;
  }

 /**
   * Epoch milliseconds.
   * @return actionTime
  **/
  @JsonProperty("actionTime")
  public Long getActionTime() {
    return actionTime;
  }

  public void setActionTime(Long actionTime) {
    this.actionTime = actionTime;
  }

  public StatusAuditEntryDTO actionTime(Long actionTime) {
    this.actionTime = actionTime;
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
    StatusAuditEntryDTO statusAuditEntryDTO = (StatusAuditEntryDTO) o;
    return Objects.equals(previousStatus, statusAuditEntryDTO.previousStatus) &&
        Objects.equals(currentStatus, statusAuditEntryDTO.currentStatus) &&
        Objects.equals(actionType, statusAuditEntryDTO.actionType) &&
        Objects.equals(actionBy, statusAuditEntryDTO.actionBy) &&
        Objects.equals(actionTime, statusAuditEntryDTO.actionTime);
  }

  @Override
  public int hashCode() {
    return Objects.hash(previousStatus, currentStatus, actionType, actionBy, actionTime);
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append("class StatusAuditEntryDTO {\n");
    
    sb.append("    previousStatus: ").append(toIndentedString(previousStatus)).append("\n");
    sb.append("    currentStatus: ").append(toIndentedString(currentStatus)).append("\n");
    sb.append("    actionType: ").append(toIndentedString(actionType)).append("\n");
    sb.append("    actionBy: ").append(toIndentedString(actionBy)).append("\n");
    sb.append("    actionTime: ").append(toIndentedString(actionTime)).append("\n");
    sb.append("}");
    return sb.toString();
  }

  /**
   * Convert the given object to string with each line indented by 4 spaces
   * (except the first line).
   */
  private static String toIndentedString(Object o) {
    if (o == null) {
      return "null";
    }
    return o.toString().replace("\n", "\n    ");
  }
}

