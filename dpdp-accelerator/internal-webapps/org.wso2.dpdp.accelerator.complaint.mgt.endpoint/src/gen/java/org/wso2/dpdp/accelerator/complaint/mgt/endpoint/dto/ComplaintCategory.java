package org.wso2.dpdp.accelerator.complaint.mgt.endpoint.dto;


import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Gets or Sets ComplaintCategory
 */
public enum ComplaintCategory {
  
  DATA_BREACH("DATA_BREACH"),
  
  UNAUTHORIZED_DATA_SHARING("UNAUTHORIZED_DATA_SHARING"),
  
  CONSENT_WITHDRAWN_DATA_STILL_USED("CONSENT_WITHDRAWN_DATA_STILL_USED"),
  
  PURPOSE_VIOLATION("PURPOSE_VIOLATION"),
  
  DATA_ERASURE_NOT_COMPLETED("DATA_ERASURE_NOT_COMPLETED"),
  
  DATA_CORRECTION_NOT_COMPLETED("DATA_CORRECTION_NOT_COMPLETED"),
  
  CONSENT_LIFECYCLE_ISSUE("CONSENT_LIFECYCLE_ISSUE"),
  
  DATA_ACCESS_DENIED("DATA_ACCESS_DENIED"),
  
  EXCESSIVE_DATA_COLLECTION("EXCESSIVE_DATA_COLLECTION"),
  
  OTHER("OTHER");

  private String value;

  ComplaintCategory(String value) {
    this.value = value;
  }

  @Override
  @JsonValue
  public String toString() {
    return String.valueOf(value);
  }

  @JsonCreator
  public static ComplaintCategory fromValue(String value) {
    for (ComplaintCategory b : ComplaintCategory.values()) {
      if (b.value.equals(value)) {
        return b;
      }
    }
    throw new IllegalArgumentException("Unexpected value '" + value + "'");
  }

}

