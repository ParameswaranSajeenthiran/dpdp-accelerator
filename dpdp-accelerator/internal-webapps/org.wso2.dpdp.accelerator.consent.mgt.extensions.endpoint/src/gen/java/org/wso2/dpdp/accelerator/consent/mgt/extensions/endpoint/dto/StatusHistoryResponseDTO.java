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

import java.util.ArrayList;
import java.util.List;
import org.wso2.dpdp.accelerator.consent.mgt.extensions.endpoint.dto.PaginationDTO;
import org.wso2.dpdp.accelerator.consent.mgt.extensions.endpoint.dto.StatusAuditEntryDTO;

import io.swagger.annotations.ApiModelProperty;
import java.util.Objects;
import com.fasterxml.jackson.annotation.JsonProperty;

@com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)

public class StatusHistoryResponseDTO  {
  
  @ApiModelProperty(value = "")
  private String consentId;

  @ApiModelProperty(value = "")
  private List<StatusAuditEntryDTO> statusHistory;

  @ApiModelProperty(value = "")
  private PaginationDTO pagination;
 /**
   * Get consentId
   * @return consentId
  **/
  @JsonProperty("consentId")
  public String getConsentId() {
    return consentId;
  }

  public void setConsentId(String consentId) {
    this.consentId = consentId;
  }

  public StatusHistoryResponseDTO consentId(String consentId) {
    this.consentId = consentId;
    return this;
  }

 /**
   * Get statusHistory
   * @return statusHistory
  **/
  @JsonProperty("statusHistory")
  public List<StatusAuditEntryDTO> getStatusHistory() {
    return statusHistory;
  }

  public void setStatusHistory(List<StatusAuditEntryDTO> statusHistory) {
    this.statusHistory = statusHistory;
  }

  public StatusHistoryResponseDTO statusHistory(List<StatusAuditEntryDTO> statusHistory) {
    this.statusHistory = statusHistory;
    return this;
  }

  public StatusHistoryResponseDTO addStatusHistoryItem(StatusAuditEntryDTO statusHistoryItem) {
    this.statusHistory.add(statusHistoryItem);
    return this;
  }

 /**
   * Get pagination
   * @return pagination
  **/
  @JsonProperty("pagination")
  public PaginationDTO getPagination() {
    return pagination;
  }

  public void setPagination(PaginationDTO pagination) {
    this.pagination = pagination;
  }

  public StatusHistoryResponseDTO pagination(PaginationDTO pagination) {
    this.pagination = pagination;
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
    StatusHistoryResponseDTO statusHistoryResponseDTO = (StatusHistoryResponseDTO) o;
    return Objects.equals(consentId, statusHistoryResponseDTO.consentId) &&
        Objects.equals(statusHistory, statusHistoryResponseDTO.statusHistory) &&
        Objects.equals(pagination, statusHistoryResponseDTO.pagination);
  }

  @Override
  public int hashCode() {
    return Objects.hash(consentId, statusHistory, pagination);
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append("class StatusHistoryResponseDTO {\n");
    
    sb.append("    consentId: ").append(toIndentedString(consentId)).append("\n");
    sb.append("    statusHistory: ").append(toIndentedString(statusHistory)).append("\n");
    sb.append("    pagination: ").append(toIndentedString(pagination)).append("\n");
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

