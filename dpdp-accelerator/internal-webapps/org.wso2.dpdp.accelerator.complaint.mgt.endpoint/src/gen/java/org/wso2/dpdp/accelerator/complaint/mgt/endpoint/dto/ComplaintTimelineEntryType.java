package org.wso2.dpdp.accelerator.complaint.mgt.endpoint.dto;


import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Gets or Sets ComplaintTimelineEntryType
 */
public enum ComplaintTimelineEntryType {
  
  STATUS_CHANGE("STATUS_CHANGE"),
  
  COMMENT("COMMENT"),
  
  INTERNAL_NOTE("INTERNAL_NOTE");

  private String value;

  ComplaintTimelineEntryType(String value) {
    this.value = value;
  }

  @Override
  @JsonValue
  public String toString() {
    return String.valueOf(value);
  }

  @JsonCreator
  public static ComplaintTimelineEntryType fromValue(String value) {
    for (ComplaintTimelineEntryType b : ComplaintTimelineEntryType.values()) {
      if (b.value.equals(value)) {
        return b;
      }
    }
    throw new IllegalArgumentException("Unexpected value '" + value + "'");
  }

}

