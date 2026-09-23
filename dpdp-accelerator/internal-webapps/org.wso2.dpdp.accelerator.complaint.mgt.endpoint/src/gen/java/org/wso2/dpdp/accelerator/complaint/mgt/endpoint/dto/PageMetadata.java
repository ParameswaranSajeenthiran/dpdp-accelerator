package org.wso2.dpdp.accelerator.complaint.mgt.endpoint.dto;


import io.swagger.annotations.ApiModelProperty;
import java.util.Objects;
import com.fasterxml.jackson.annotation.JsonProperty;


public class PageMetadata  {
  
  @ApiModelProperty(required = true, value = "")

  private Integer total;

  @ApiModelProperty(required = true, value = "")

  private Integer offset;

  @ApiModelProperty(required = true, value = "")

  private Integer count;

  @ApiModelProperty(required = true, value = "")

  private Integer limit;
 /**
   * Get total
   * @return total
  **/
  @JsonProperty("total")
  public Integer getTotal() {
    return total;
  }

  public void setTotal(Integer total) {
    this.total = total;
  }

  public PageMetadata total(Integer total) {
    this.total = total;
    return this;
  }

 /**
   * Get offset
   * @return offset
  **/
  @JsonProperty("offset")
  public Integer getOffset() {
    return offset;
  }

  public void setOffset(Integer offset) {
    this.offset = offset;
  }

  public PageMetadata offset(Integer offset) {
    this.offset = offset;
    return this;
  }

 /**
   * Get count
   * @return count
  **/
  @JsonProperty("count")
  public Integer getCount() {
    return count;
  }

  public void setCount(Integer count) {
    this.count = count;
  }

  public PageMetadata count(Integer count) {
    this.count = count;
    return this;
  }

 /**
   * Get limit
   * @return limit
  **/
  @JsonProperty("limit")
  public Integer getLimit() {
    return limit;
  }

  public void setLimit(Integer limit) {
    this.limit = limit;
  }

  public PageMetadata limit(Integer limit) {
    this.limit = limit;
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
    PageMetadata pageMetadata = (PageMetadata) o;
    return Objects.equals(this.total, pageMetadata.total) &&
        Objects.equals(this.offset, pageMetadata.offset) &&
        Objects.equals(this.count, pageMetadata.count) &&
        Objects.equals(this.limit, pageMetadata.limit);
  }

  @Override
  public int hashCode() {
    return Objects.hash(total, offset, count, limit);
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append("class PageMetadata {\n");
    
    sb.append("    total: ").append(toIndentedString(total)).append("\n");
    sb.append("    offset: ").append(toIndentedString(offset)).append("\n");
    sb.append("    count: ").append(toIndentedString(count)).append("\n");
    sb.append("    limit: ").append(toIndentedString(limit)).append("\n");
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

