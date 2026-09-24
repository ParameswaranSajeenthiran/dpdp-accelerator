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


import io.swagger.annotations.ApiModelProperty;
import java.util.Objects;
import com.fasterxml.jackson.annotation.JsonProperty;


public class ComplaintQueueStatsResponse  {
  
 /**
  * OPEN and IN_PROGRESS combined.
  */
  @ApiModelProperty(required = true, value = "OPEN and IN_PROGRESS combined.")

  private Integer openCount;

 /**
  * AWAITING_INTERNAL_REVIEW.
  */
  @ApiModelProperty(required = true, value = "AWAITING_INTERNAL_REVIEW.")

  private Integer awaitingInternalReviewCount;

 /**
  * RESOLVED.
  */
  @ApiModelProperty(required = true, value = "RESOLVED.")

  private Integer resolvedCount;

 /**
  * Not yet RESOLVED and past statutoryDueDate.
  */
  @ApiModelProperty(required = true, value = "Not yet RESOLVED and past statutoryDueDate.")

  private Integer slaBreachedCount;
 /**
   * OPEN and IN_PROGRESS combined.
   * @return openCount
  **/
  @JsonProperty("openCount")
  public Integer getOpenCount() {
    return openCount;
  }

  public void setOpenCount(Integer openCount) {
    this.openCount = openCount;
  }

  public ComplaintQueueStatsResponse openCount(Integer openCount) {
    this.openCount = openCount;
    return this;
  }

 /**
   * AWAITING_INTERNAL_REVIEW.
   * @return awaitingInternalReviewCount
  **/
  @JsonProperty("awaitingInternalReviewCount")
  public Integer getAwaitingInternalReviewCount() {
    return awaitingInternalReviewCount;
  }

  public void setAwaitingInternalReviewCount(Integer awaitingInternalReviewCount) {
    this.awaitingInternalReviewCount = awaitingInternalReviewCount;
  }

  public ComplaintQueueStatsResponse awaitingInternalReviewCount(Integer awaitingInternalReviewCount) {
    this.awaitingInternalReviewCount = awaitingInternalReviewCount;
    return this;
  }

 /**
   * RESOLVED.
   * @return resolvedCount
  **/
  @JsonProperty("resolvedCount")
  public Integer getResolvedCount() {
    return resolvedCount;
  }

  public void setResolvedCount(Integer resolvedCount) {
    this.resolvedCount = resolvedCount;
  }

  public ComplaintQueueStatsResponse resolvedCount(Integer resolvedCount) {
    this.resolvedCount = resolvedCount;
    return this;
  }

 /**
   * Not yet RESOLVED and past statutoryDueDate.
   * @return slaBreachedCount
  **/
  @JsonProperty("slaBreachedCount")
  public Integer getSlaBreachedCount() {
    return slaBreachedCount;
  }

  public void setSlaBreachedCount(Integer slaBreachedCount) {
    this.slaBreachedCount = slaBreachedCount;
  }

  public ComplaintQueueStatsResponse slaBreachedCount(Integer slaBreachedCount) {
    this.slaBreachedCount = slaBreachedCount;
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
    ComplaintQueueStatsResponse complaintQueueStatsResponse = (ComplaintQueueStatsResponse) o;
    return Objects.equals(this.openCount, complaintQueueStatsResponse.openCount) &&
        Objects.equals(this.awaitingInternalReviewCount, complaintQueueStatsResponse.awaitingInternalReviewCount) &&
        Objects.equals(this.resolvedCount, complaintQueueStatsResponse.resolvedCount) &&
        Objects.equals(this.slaBreachedCount, complaintQueueStatsResponse.slaBreachedCount);
  }

  @Override
  public int hashCode() {
    return Objects.hash(openCount, awaitingInternalReviewCount, resolvedCount, slaBreachedCount);
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append("class ComplaintQueueStatsResponse {\n");
    
    sb.append("    openCount: ").append(toIndentedString(openCount)).append("\n");
    sb.append("    awaitingInternalReviewCount: ").append(toIndentedString(awaitingInternalReviewCount)).append("\n");
    sb.append("    resolvedCount: ").append(toIndentedString(resolvedCount)).append("\n");
    sb.append("    slaBreachedCount: ").append(toIndentedString(slaBreachedCount)).append("\n");
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

