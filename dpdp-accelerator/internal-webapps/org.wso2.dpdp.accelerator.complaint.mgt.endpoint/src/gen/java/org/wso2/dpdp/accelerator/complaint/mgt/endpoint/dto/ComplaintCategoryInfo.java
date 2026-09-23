package org.wso2.dpdp.accelerator.complaint.mgt.endpoint.dto;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import org.wso2.dpdp.accelerator.complaint.mgt.endpoint.dto.ComplaintCategory;
import org.wso2.dpdp.accelerator.complaint.mgt.endpoint.dto.ComplaintPriority;

import io.swagger.annotations.ApiModelProperty;
import java.util.Objects;
import com.fasterxml.jackson.annotation.JsonProperty;


public class ComplaintCategoryInfo  {
  
  @ApiModelProperty(required = true, value = "")

  private ComplaintCategory category;

  @ApiModelProperty(required = true, value = "")

  private ComplaintPriority priority;
 /**
   * Get category
   * @return category
  **/
  @JsonProperty("category")
  public ComplaintCategory getCategory() {
    return category;
  }

  public void setCategory(ComplaintCategory category) {
    this.category = category;
  }

  public ComplaintCategoryInfo category(ComplaintCategory category) {
    this.category = category;
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

  public ComplaintCategoryInfo priority(ComplaintPriority priority) {
    this.priority = priority;
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
    ComplaintCategoryInfo complaintCategoryInfo = (ComplaintCategoryInfo) o;
    return Objects.equals(this.category, complaintCategoryInfo.category) &&
        Objects.equals(this.priority, complaintCategoryInfo.priority);
  }

  @Override
  public int hashCode() {
    return Objects.hash(category, priority);
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append("class ComplaintCategoryInfo {\n");
    
    sb.append("    category: ").append(toIndentedString(category)).append("\n");
    sb.append("    priority: ").append(toIndentedString(priority)).append("\n");
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

