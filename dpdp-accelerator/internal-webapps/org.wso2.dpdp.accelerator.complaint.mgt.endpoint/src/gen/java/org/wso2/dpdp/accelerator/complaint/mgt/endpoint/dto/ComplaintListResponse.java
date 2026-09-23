package org.wso2.dpdp.accelerator.complaint.mgt.endpoint.dto;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.wso2.dpdp.accelerator.complaint.mgt.endpoint.dto.ComplaintRecord;
import org.wso2.dpdp.accelerator.complaint.mgt.endpoint.dto.PageMetadata;

import io.swagger.annotations.ApiModelProperty;
import java.util.Objects;
import com.fasterxml.jackson.annotation.JsonProperty;


public class ComplaintListResponse  {
  
  @ApiModelProperty(required = true, value = "")

  private List<ComplaintRecord> data;

  @ApiModelProperty(required = true, value = "")

  private PageMetadata metadata;
 /**
   * Get data
   * @return data
  **/
  @JsonProperty("data")
  public List<ComplaintRecord> getData() {
    return data;
  }

  public void setData(List<ComplaintRecord> data) {
    this.data = data;
  }

  public ComplaintListResponse data(List<ComplaintRecord> data) {
    this.data = data;
    return this;
  }

  public ComplaintListResponse addDataItem(ComplaintRecord dataItem) {
    this.data.add(dataItem);
    return this;
  }

 /**
   * Get metadata
   * @return metadata
  **/
  @JsonProperty("metadata")
  public PageMetadata getMetadata() {
    return metadata;
  }

  public void setMetadata(PageMetadata metadata) {
    this.metadata = metadata;
  }

  public ComplaintListResponse metadata(PageMetadata metadata) {
    this.metadata = metadata;
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
    ComplaintListResponse complaintListResponse = (ComplaintListResponse) o;
    return Objects.equals(this.data, complaintListResponse.data) &&
        Objects.equals(this.metadata, complaintListResponse.metadata);
  }

  @Override
  public int hashCode() {
    return Objects.hash(data, metadata);
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append("class ComplaintListResponse {\n");
    
    sb.append("    data: ").append(toIndentedString(data)).append("\n");
    sb.append("    metadata: ").append(toIndentedString(metadata)).append("\n");
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

