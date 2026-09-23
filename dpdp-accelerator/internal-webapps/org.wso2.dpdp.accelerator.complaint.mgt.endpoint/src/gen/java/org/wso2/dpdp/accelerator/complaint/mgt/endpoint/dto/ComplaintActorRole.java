package org.wso2.dpdp.accelerator.complaint.mgt.endpoint.dto;


import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Gets or Sets ComplaintActorRole
 */
public enum ComplaintActorRole {
  
  USER("USER"),
  
  COMPLAINT_OFFICER("COMPLAINT_OFFICER"),
  
  SYSTEM("SYSTEM");

  private String value;

  ComplaintActorRole(String value) {
    this.value = value;
  }

  @Override
  @JsonValue
  public String toString() {
    return String.valueOf(value);
  }

  @JsonCreator
  public static ComplaintActorRole fromValue(String value) {
    for (ComplaintActorRole b : ComplaintActorRole.values()) {
      if (b.value.equals(value)) {
        return b;
      }
    }
    throw new IllegalArgumentException("Unexpected value '" + value + "'");
  }

}

