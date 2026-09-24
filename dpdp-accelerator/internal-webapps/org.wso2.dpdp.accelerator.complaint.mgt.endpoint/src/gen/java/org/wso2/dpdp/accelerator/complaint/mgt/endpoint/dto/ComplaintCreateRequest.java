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
import org.wso2.dpdp.accelerator.complaint.mgt.endpoint.dto.ComplaintCategory;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import java.util.Objects;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Pure-JSON body for POST /complaints (officer-assisted intake). Unlike MeComplaintCreateRequest, userId is required — the officer is creating this on behalf of a Data Principal who is not the caller. Who performed the intake is resolved from the caller's bearer token (never accepted from this body) and recorded as a CREATE audit event on the new complaint's timeline. 
 */
@ApiModel(description="Pure-JSON body for POST /complaints (officer-assisted intake). Unlike MeComplaintCreateRequest, userId is required — the officer is creating this on behalf of a Data Principal who is not the caller. Who performed the intake is resolved from the caller's bearer token (never accepted from this body) and recorded as a CREATE audit event on the new complaint's timeline. ")

public class ComplaintCreateRequest  {
  
 /**
  * The Data Principal on whose behalf this complaint is being lodged.
  */
  @ApiModelProperty(required = true, value = "The Data Principal on whose behalf this complaint is being lodged.")

  private String userId;

  @ApiModelProperty(required = true, value = "")

  private ComplaintCategory subjectCategory;

  @ApiModelProperty(required = true, value = "")

  private String description;
 /**
   * The Data Principal on whose behalf this complaint is being lodged.
   * @return userId
  **/
  @JsonProperty("userId")
  public String getUserId() {
    return userId;
  }

  public void setUserId(String userId) {
    this.userId = userId;
  }

  public ComplaintCreateRequest userId(String userId) {
    this.userId = userId;
    return this;
  }

 /**
   * Get subjectCategory
   * @return subjectCategory
  **/
  @JsonProperty("subjectCategory")
  public ComplaintCategory getSubjectCategory() {
    return subjectCategory;
  }

  public void setSubjectCategory(ComplaintCategory subjectCategory) {
    this.subjectCategory = subjectCategory;
  }

  public ComplaintCreateRequest subjectCategory(ComplaintCategory subjectCategory) {
    this.subjectCategory = subjectCategory;
    return this;
  }

 /**
   * Get description
   * @return description
  **/
  @JsonProperty("description")
  public String getDescription() {
    return description;
  }

  public void setDescription(String description) {
    this.description = description;
  }

  public ComplaintCreateRequest description(String description) {
    this.description = description;
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
    ComplaintCreateRequest complaintCreateRequest = (ComplaintCreateRequest) o;
    return Objects.equals(this.userId, complaintCreateRequest.userId) &&
        Objects.equals(this.subjectCategory, complaintCreateRequest.subjectCategory) &&
        Objects.equals(this.description, complaintCreateRequest.description);
  }

  @Override
  public int hashCode() {
    return Objects.hash(userId, subjectCategory, description);
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append("class ComplaintCreateRequest {\n");
    
    sb.append("    userId: ").append(toIndentedString(userId)).append("\n");
    sb.append("    subjectCategory: ").append(toIndentedString(subjectCategory)).append("\n");
    sb.append("    description: ").append(toIndentedString(description)).append("\n");
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

