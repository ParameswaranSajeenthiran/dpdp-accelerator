package org.wso2.dpdp.accelerator.consent.mgt.extensions.endpoint.dto;

import io.swagger.annotations.ApiModel;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * What triggered this entry. AUTHORIZE_APPROVE/AUTHORIZE_REJECT/AUTHORIZE_REVOKE reflect the specific per-authorizer decision rather than a single generic AUTHORIZE, since one value covering all three reads ambiguously. EXPIRE is written by the consent expiry job/lazy reconciliation, with actionBy&#x3D;SYSTEM rather than a real user.
 */
public enum ActionType {
  
  CREATE("CREATE"),
  
  UPDATE("UPDATE"),
  
  REVOKE("REVOKE"),
  
  AUTHORIZE_APPROVE("AUTHORIZE_APPROVE"),
  
  AUTHORIZE_REJECT("AUTHORIZE_REJECT"),
  
  AUTHORIZE_REVOKE("AUTHORIZE_REVOKE"),
  
  DELETE("DELETE"),
  
  EXPIRE("EXPIRE");

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

