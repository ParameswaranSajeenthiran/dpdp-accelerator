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
import org.wso2.dpdp.accelerator.complaint.mgt.endpoint.dto.ComplaintPriority;
import org.wso2.dpdp.accelerator.complaint.mgt.endpoint.dto.ComplaintStatus;

import io.swagger.annotations.ApiModelProperty;
import java.util.Objects;
import com.fasterxml.jackson.annotation.JsonProperty;


public class ComplaintCreateResponse  {
  
  @ApiModelProperty(required = true, value = "")

  private String id;

 /**
  * Human-readable complaint reference shown to the Data Principal.
  */
  @ApiModelProperty(required = true, value = "Human-readable complaint reference shown to the Data Principal.")

  private String referenceId;

  @ApiModelProperty(required = true, value = "")

  private ComplaintCategory subjectCategory;

  @ApiModelProperty(required = true, value = "")

  private ComplaintPriority priority;

  @ApiModelProperty(required = true, value = "")

  private ComplaintStatus status;

  @ApiModelProperty(required = true, value = "")

  private String userId;

 /**
  * Human-readable display name for userId, resolved from the submitter's token at creation time when available. Null when an officer lodged this on the Data Principal's behalf (no resolvable name for that user) or for complaints predating this field. 
  */
  @ApiModelProperty(value = "Human-readable display name for userId, resolved from the submitter's token at creation time when available. Null when an officer lodged this on the Data Principal's behalf (no resolvable name for that user) or for complaints predating this field. ")

  private String userName;

  @ApiModelProperty(required = true, value = "")

  private String description;

 /**
  * Unix timestamp, milliseconds.
  */
  @ApiModelProperty(required = true, value = "Unix timestamp, milliseconds.")

  private Long submittedAt;

 /**
  * Unix timestamp, milliseconds.
  */
  @ApiModelProperty(required = true, value = "Unix timestamp, milliseconds.")

  private Long updatedAt;

 /**
  * Unix timestamp, milliseconds.
  */
  @ApiModelProperty(required = true, value = "Unix timestamp, milliseconds.")

  private Long statutoryDueDate;
 /**
   * Get id
   * @return id
  **/
  @JsonProperty("id")
  public String getId() {
    return id;
  }

  public void setId(String id) {
    this.id = id;
  }

  public ComplaintCreateResponse id(String id) {
    this.id = id;
    return this;
  }

 /**
   * Human-readable complaint reference shown to the Data Principal.
   * @return referenceId
  **/
  @JsonProperty("referenceId")
  public String getReferenceId() {
    return referenceId;
  }

  public void setReferenceId(String referenceId) {
    this.referenceId = referenceId;
  }

  public ComplaintCreateResponse referenceId(String referenceId) {
    this.referenceId = referenceId;
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

  public ComplaintCreateResponse subjectCategory(ComplaintCategory subjectCategory) {
    this.subjectCategory = subjectCategory;
    return this;
  }

 /**
   * Get priority
   * @return priority
  **/
  @JsonProperty("priority")
  public ComplaintPriority getPriority() {
    return priority;
  }

  public void setPriority(ComplaintPriority priority) {
    this.priority = priority;
  }

  public ComplaintCreateResponse priority(ComplaintPriority priority) {
    this.priority = priority;
    return this;
  }

 /**
   * Get status
   * @return status
  **/
  @JsonProperty("status")
  public ComplaintStatus getStatus() {
    return status;
  }

  public void setStatus(ComplaintStatus status) {
    this.status = status;
  }

  public ComplaintCreateResponse status(ComplaintStatus status) {
    this.status = status;
    return this;
  }

 /**
   * Get userId
   * @return userId
  **/
  @JsonProperty("userId")
  public String getUserId() {
    return userId;
  }

  public void setUserId(String userId) {
    this.userId = userId;
  }

  public ComplaintCreateResponse userId(String userId) {
    this.userId = userId;
    return this;
  }

 /**
   * Human-readable display name for userId, resolved from the submitter&#39;s token at creation time when available. Null when an officer lodged this on the Data Principal&#39;s behalf (no resolvable name for that user) or for complaints predating this field. 
   * @return userName
  **/
  @JsonProperty("userName")
  public String getUserName() {
    return userName;
  }

  public void setUserName(String userName) {
    this.userName = userName;
  }

  public ComplaintCreateResponse userName(String userName) {
    this.userName = userName;
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

  public ComplaintCreateResponse description(String description) {
    this.description = description;
    return this;
  }

 /**
   * Unix timestamp, milliseconds.
   * @return submittedAt
  **/
  @JsonProperty("submittedAt")
  public Long getSubmittedAt() {
    return submittedAt;
  }

  public void setSubmittedAt(Long submittedAt) {
    this.submittedAt = submittedAt;
  }

  public ComplaintCreateResponse submittedAt(Long submittedAt) {
    this.submittedAt = submittedAt;
    return this;
  }

 /**
   * Unix timestamp, milliseconds.
   * @return updatedAt
  **/
  @JsonProperty("updatedAt")
  public Long getUpdatedAt() {
    return updatedAt;
  }

  public void setUpdatedAt(Long updatedAt) {
    this.updatedAt = updatedAt;
  }

  public ComplaintCreateResponse updatedAt(Long updatedAt) {
    this.updatedAt = updatedAt;
    return this;
  }

 /**
   * Unix timestamp, milliseconds.
   * @return statutoryDueDate
  **/
  @JsonProperty("statutoryDueDate")
  public Long getStatutoryDueDate() {
    return statutoryDueDate;
  }

  public void setStatutoryDueDate(Long statutoryDueDate) {
    this.statutoryDueDate = statutoryDueDate;
  }

  public ComplaintCreateResponse statutoryDueDate(Long statutoryDueDate) {
    this.statutoryDueDate = statutoryDueDate;
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
    ComplaintCreateResponse complaintCreateResponse = (ComplaintCreateResponse) o;
    return Objects.equals(this.id, complaintCreateResponse.id) &&
        Objects.equals(this.referenceId, complaintCreateResponse.referenceId) &&
        Objects.equals(this.subjectCategory, complaintCreateResponse.subjectCategory) &&
        Objects.equals(this.priority, complaintCreateResponse.priority) &&
        Objects.equals(this.status, complaintCreateResponse.status) &&
        Objects.equals(this.userId, complaintCreateResponse.userId) &&
        Objects.equals(this.userName, complaintCreateResponse.userName) &&
        Objects.equals(this.description, complaintCreateResponse.description) &&
        Objects.equals(this.submittedAt, complaintCreateResponse.submittedAt) &&
        Objects.equals(this.updatedAt, complaintCreateResponse.updatedAt) &&
        Objects.equals(this.statutoryDueDate, complaintCreateResponse.statutoryDueDate);
  }

  @Override
  public int hashCode() {
    return Objects.hash(id, referenceId, subjectCategory, priority, status, userId, userName, description, submittedAt, updatedAt, statutoryDueDate);
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append("class ComplaintCreateResponse {\n");
    
    sb.append("    id: ").append(toIndentedString(id)).append("\n");
    sb.append("    referenceId: ").append(toIndentedString(referenceId)).append("\n");
    sb.append("    subjectCategory: ").append(toIndentedString(subjectCategory)).append("\n");
    sb.append("    priority: ").append(toIndentedString(priority)).append("\n");
    sb.append("    status: ").append(toIndentedString(status)).append("\n");
    sb.append("    userId: ").append(toIndentedString(userId)).append("\n");
    sb.append("    userName: ").append(toIndentedString(userName)).append("\n");
    sb.append("    description: ").append(toIndentedString(description)).append("\n");
    sb.append("    submittedAt: ").append(toIndentedString(submittedAt)).append("\n");
    sb.append("    updatedAt: ").append(toIndentedString(updatedAt)).append("\n");
    sb.append("    statutoryDueDate: ").append(toIndentedString(statutoryDueDate)).append("\n");
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

