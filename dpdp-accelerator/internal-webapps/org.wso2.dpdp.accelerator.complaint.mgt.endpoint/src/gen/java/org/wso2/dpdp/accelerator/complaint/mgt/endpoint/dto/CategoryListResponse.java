package org.wso2.dpdp.accelerator.complaint.mgt.endpoint.dto;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.wso2.dpdp.accelerator.complaint.mgt.endpoint.dto.ComplaintCategoryInfo;

import io.swagger.annotations.ApiModelProperty;
import java.util.Objects;
import com.fasterxml.jackson.annotation.JsonProperty;


public class CategoryListResponse  {
  
  @ApiModelProperty(required = true, value = "")

  private List<ComplaintCategoryInfo> data;
 /**
   * Get data
   * @return data
  **/
  @JsonProperty("data")
  public List<ComplaintCategoryInfo> getData() {
    return data;
  }

  public void setData(List<ComplaintCategoryInfo> data) {
    this.data = data;
  }

  public CategoryListResponse data(List<ComplaintCategoryInfo> data) {
    this.data = data;
    return this;
  }

  public CategoryListResponse addDataItem(ComplaintCategoryInfo dataItem) {
    this.data.add(dataItem);
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
    CategoryListResponse categoryListResponse = (CategoryListResponse) o;
    return Objects.equals(this.data, categoryListResponse.data);
  }

  @Override
  public int hashCode() {
    return Objects.hash(data);
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append("class CategoryListResponse {\n");
    
    sb.append("    data: ").append(toIndentedString(data)).append("\n");
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

