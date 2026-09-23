package org.wso2.dpdp.accelerator.complaint.mgt.endpoint.dto;


import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Server-derived, never client-supplied
 */
public enum ComplaintPriority {
  
  CRITICAL("CRITICAL"),
  
  HIGH("HIGH"),
  
  MEDIUM("MEDIUM"),
  
  LOW("LOW");

  private String value;

  ComplaintPriority(String value) {
    this.value = value;
  }

  @Override
  @JsonValue
  public String toString() {
    return String.valueOf(value);
  }

  @JsonCreator
  public static ComplaintPriority fromValue(String value) {
    for (ComplaintPriority b : ComplaintPriority.values()) {
      if (b.value.equals(value)) {
        return b;
      }
    }
    throw new IllegalArgumentException("Unexpected value '" + value + "'");
  }

}

