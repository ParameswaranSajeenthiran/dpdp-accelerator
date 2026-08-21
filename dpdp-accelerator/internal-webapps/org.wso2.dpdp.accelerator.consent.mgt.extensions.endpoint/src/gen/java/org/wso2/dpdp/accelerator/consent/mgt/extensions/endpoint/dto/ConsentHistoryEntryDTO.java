package org.wso2.dpdp.accelerator.consent.mgt.extensions.endpoint.dto;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import org.wso2.dpdp.accelerator.consent.mgt.extensions.endpoint.dto.ActionType;

import io.swagger.annotations.ApiModelProperty;
import java.util.Objects;
import com.fasterxml.jackson.annotation.JsonProperty;

@com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)

public class ConsentHistoryEntryDTO  {
  
  @ApiModelProperty(value = "")
  private ActionType actionType;

  @ApiModelProperty(value = "")
  private String actionBy;

  @ApiModelProperty(value = "Epoch milliseconds.")
 /**
   * Epoch milliseconds.
  **/
  private Long actionTime;

  @ApiModelProperty(value = "Parsed from the stored JSON string back into a tree before being placed here, so Jackson serializes it as a nested object in the response rather than an escaped string.")
 /**
   * Parsed from the stored JSON string back into a tree before being placed here, so Jackson serializes it as a nested object in the response rather than an escaped string.
  **/
  private Object snapshot;
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

  public ConsentHistoryEntryDTO actionType(ActionType actionType) {
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

  public ConsentHistoryEntryDTO actionBy(String actionBy) {
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

  public ConsentHistoryEntryDTO actionTime(Long actionTime) {
    this.actionTime = actionTime;
    return this;
  }

 /**
   * Parsed from the stored JSON string back into a tree before being placed here, so Jackson serializes it as a nested object in the response rather than an escaped string.
   * @return snapshot
  **/
  @JsonProperty("snapshot")
  public Object getSnapshot() {
    return snapshot;
  }

  public void setSnapshot(Object snapshot) {
    this.snapshot = snapshot;
  }

  public ConsentHistoryEntryDTO snapshot(Object snapshot) {
    this.snapshot = snapshot;
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
    ConsentHistoryEntryDTO consentHistoryEntryDTO = (ConsentHistoryEntryDTO) o;
    return Objects.equals(actionType, consentHistoryEntryDTO.actionType) &&
        Objects.equals(actionBy, consentHistoryEntryDTO.actionBy) &&
        Objects.equals(actionTime, consentHistoryEntryDTO.actionTime) &&
        Objects.equals(snapshot, consentHistoryEntryDTO.snapshot);
  }

  @Override
  public int hashCode() {
    return Objects.hash(actionType, actionBy, actionTime, snapshot);
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append("class ConsentHistoryEntryDTO {\n");
    
    sb.append("    actionType: ").append(toIndentedString(actionType)).append("\n");
    sb.append("    actionBy: ").append(toIndentedString(actionBy)).append("\n");
    sb.append("    actionTime: ").append(toIndentedString(actionTime)).append("\n");
    sb.append("    snapshot: ").append(toIndentedString(snapshot)).append("\n");
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

