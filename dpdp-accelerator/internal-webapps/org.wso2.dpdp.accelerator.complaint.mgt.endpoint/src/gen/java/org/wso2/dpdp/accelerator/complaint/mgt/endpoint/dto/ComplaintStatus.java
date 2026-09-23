package org.wso2.dpdp.accelerator.complaint.mgt.endpoint.dto;


import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Gets or Sets ComplaintStatus
 */
public enum ComplaintStatus {
  
  OPEN("OPEN"),
  
  IN_PROGRESS("IN_PROGRESS"),
  
  WAITING_ON_CLIENT("WAITING_ON_CLIENT"),
  
  AWAITING_INTERNAL_REVIEW("AWAITING_INTERNAL_REVIEW"),
  
  RESOLVED("RESOLVED");

  private String value;

  ComplaintStatus(String value) {
    this.value = value;
  }

  @Override
  @JsonValue
  public String toString() {
    return String.valueOf(value);
  }

  @JsonCreator
  public static ComplaintStatus fromValue(String value) {
    for (ComplaintStatus b : ComplaintStatus.values()) {
      if (b.value.equals(value)) {
        return b;
      }
    }
    throw new IllegalArgumentException("Unexpected value '" + value + "'");
  }

}

