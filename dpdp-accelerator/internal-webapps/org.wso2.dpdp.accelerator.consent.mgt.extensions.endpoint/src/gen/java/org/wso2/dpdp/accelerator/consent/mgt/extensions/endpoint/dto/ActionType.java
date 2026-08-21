package org.wso2.dpdp.accelerator.consent.mgt.extensions.endpoint.dto;

import io.swagger.annotations.ApiModel;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * What triggered this entry. No EXPIRE value - expiry handling is a separate, later task.
 */
public enum ActionType {
  
  CREATE("CREATE"),
  
  UPDATE("UPDATE"),
  
  REVOKE("REVOKE"),
  
  AUTHORIZE("AUTHORIZE"),
  
  DELETE("DELETE");

  private String value;

  ActionType(String value) {
    this.value = value;
  }

  @Override
  @JsonValue
  public String toString() {
    return String.valueOf(value);
  }

  @JsonCreator
  public static ActionType fromValue(String value) {
    for (ActionType b : ActionType.values()) {
      if (b.value.equals(value)) {
        return b;
      }
    }
    throw new IllegalArgumentException("Unexpected value '" + value + "'");
  }

}

