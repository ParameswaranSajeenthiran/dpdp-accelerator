package org.wso2.dpdp.accelerator.complaint.mgt.endpoint.dto;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.wso2.dpdp.accelerator.complaint.mgt.endpoint.dto.ComplaintTimelineEntryResponse;
import org.wso2.dpdp.accelerator.complaint.mgt.endpoint.dto.PageMetadata;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import java.util.Objects;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Each entry in `data` carries the attachments uploaded alongside it under `attachments` (see ComplaintTimelineEntryResponse) - there is no separate complaint-wide attachments list here; use GET /complaints/{complaintId} for that. Filtered to isPublic=true for /me callers; full list for officer/admin callers. 
 */
@ApiModel(description="Each entry in `data` carries the attachments uploaded alongside it under `attachments` (see ComplaintTimelineEntryResponse) - there is no separate complaint-wide attachments list here; use GET /complaints/{complaintId} for that. Filtered to isPublic=true for /me callers; full list for officer/admin callers. ")

public class TimelineListResponse  {
  
  @ApiModelProperty(required = true, value = "")

  private List<ComplaintTimelineEntryResponse> data;

  @ApiModelProperty(required = true, value = "")

  private PageMetadata metadata;
 /**
   * Get data
   * @return data
  **/
  @JsonProperty("data")
  public List<ComplaintTimelineEntryResponse> getData() {
    return data;
  }

  public void setData(List<ComplaintTimelineEntryResponse> data) {
    this.data = data;
  }

  public TimelineListResponse data(List<ComplaintTimelineEntryResponse> data) {
    this.data = data;
    return this;
  }

  public TimelineListResponse addDataItem(ComplaintTimelineEntryResponse dataItem) {
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

  public TimelineListResponse metadata(PageMetadata metadata) {
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
    TimelineListResponse timelineListResponse = (TimelineListResponse) o;
    return Objects.equals(this.data, timelineListResponse.data) &&
        Objects.equals(this.metadata, timelineListResponse.metadata);
  }

  @Override
  public int hashCode() {
    return Objects.hash(data, metadata);
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append("class TimelineListResponse {\n");
    
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

