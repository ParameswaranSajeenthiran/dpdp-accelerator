package org.wso2.dpdp.accelerator.complaint.mgt.endpoint.dto;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import org.wso2.dpdp.accelerator.complaint.mgt.endpoint.dto.ComplaintCategory;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import java.util.Objects;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Pure-JSON body for POST /me/complaints. No userId field — the owner is the authenticated caller, identified via their bearer access token (sub claim). 
 */
@ApiModel(description="Pure-JSON body for POST /me/complaints. No userId field — the owner is the authenticated caller, identified via their bearer access token (sub claim). ")

public class MeComplaintCreateRequest  {
  
  @ApiModelProperty(required = true, value = "")

  private ComplaintCategory subjectCategory;

  @ApiModelProperty(required = true, value = "")

  private String description;
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

  public MeComplaintCreateRequest subjectCategory(ComplaintCategory subjectCategory) {
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

  public MeComplaintCreateRequest description(String description) {
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
    MeComplaintCreateRequest meComplaintCreateRequest = (MeComplaintCreateRequest) o;
    return Objects.equals(this.subjectCategory, meComplaintCreateRequest.subjectCategory) &&
        Objects.equals(this.description, meComplaintCreateRequest.description);
  }

  @Override
  public int hashCode() {
    return Objects.hash(subjectCategory, description);
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append("class MeComplaintCreateRequest {\n");
    
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

