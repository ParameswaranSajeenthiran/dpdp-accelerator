package org.wso2.dpdp.accelerator.complaint.mgt.endpoint.dto;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import org.wso2.dpdp.accelerator.complaint.mgt.endpoint.dto.ComplaintStatus;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import java.util.Objects;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Pure-JSON body for POST /complaints/{complaintId}/status — a status-only transition with no accompanying message. No actorUserId/actorRole - the acting officer's identity (role COMPLAINT_OFFICER) is resolved from the bearer token, never accepted from the request body. 
 */
@ApiModel(description="Pure-JSON body for POST /complaints/{complaintId}/status — a status-only transition with no accompanying message. No actorUserId/actorRole - the acting officer's identity (role COMPLAINT_OFFICER) is resolved from the bearer token, never accepted from the request body. ")

public class ComplaintStatusUpdateRequest  {
  
  @ApiModelProperty(required = true, value = "")

  private ComplaintStatus toStatus;

  @ApiModelProperty(required = true, value = "")

  private String note;
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

  public ComplaintStatusUpdateRequest toStatus(ComplaintStatus toStatus) {
    this.toStatus = toStatus;
    return this;
  }

 /**
   * Get note
   * @return note
  **/
  @JsonProperty("note")
  public String getNote() {
    return note;
  }

  public void setNote(String note) {
    this.note = note;
  }

  public ComplaintStatusUpdateRequest note(String note) {
    this.note = note;
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
    ComplaintStatusUpdateRequest complaintStatusUpdateRequest = (ComplaintStatusUpdateRequest) o;
    return Objects.equals(this.toStatus, complaintStatusUpdateRequest.toStatus) &&
        Objects.equals(this.note, complaintStatusUpdateRequest.note);
  }

  @Override
  public int hashCode() {
    return Objects.hash(toStatus, note);
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append("class ComplaintStatusUpdateRequest {\n");
    
    sb.append("    toStatus: ").append(toIndentedString(toStatus)).append("\n");
    sb.append("    note: ").append(toIndentedString(note)).append("\n");
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

