package org.wso2.dpdp.accelerator.consent.mgt.extensions.endpoint.dto;

import java.util.ArrayList;
import java.util.List;
import org.wso2.dpdp.accelerator.consent.mgt.extensions.endpoint.dto.ConsentHistoryEntryDTO;
import org.wso2.dpdp.accelerator.consent.mgt.extensions.endpoint.dto.PaginationDTO;

import io.swagger.annotations.ApiModelProperty;
import java.util.Objects;
import com.fasterxml.jackson.annotation.JsonProperty;

@com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)

public class ConsentHistoryResponseDTO  {
  
  @ApiModelProperty(value = "")
  private String consentId;

  @ApiModelProperty(value = "")
  private List<ConsentHistoryEntryDTO> history;

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

  public ConsentHistoryResponseDTO consentId(String consentId) {
    this.consentId = consentId;
    return this;
  }

 /**
   * Get history
   * @return history
  **/
  @JsonProperty("history")
  public List<ConsentHistoryEntryDTO> getHistory() {
    return history;
  }

  public void setHistory(List<ConsentHistoryEntryDTO> history) {
    this.history = history;
  }

  public ConsentHistoryResponseDTO history(List<ConsentHistoryEntryDTO> history) {
    this.history = history;
    return this;
  }

  public ConsentHistoryResponseDTO addHistoryItem(ConsentHistoryEntryDTO historyItem) {
    this.history.add(historyItem);
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

  public ConsentHistoryResponseDTO pagination(PaginationDTO pagination) {
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
    ConsentHistoryResponseDTO consentHistoryResponseDTO = (ConsentHistoryResponseDTO) o;
    return Objects.equals(consentId, consentHistoryResponseDTO.consentId) &&
        Objects.equals(history, consentHistoryResponseDTO.history) &&
        Objects.equals(pagination, consentHistoryResponseDTO.pagination);
  }

  @Override
  public int hashCode() {
    return Objects.hash(consentId, history, pagination);
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append("class ConsentHistoryResponseDTO {\n");
    
    sb.append("    consentId: ").append(toIndentedString(consentId)).append("\n");
    sb.append("    history: ").append(toIndentedString(history)).append("\n");
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

