/*
 * Copyright (c) 2026, WSO2 LLC. (https://www.wso2.com).
 *
 * WSO2 LLC. licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

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

