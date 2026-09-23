package org.wso2.dpdp.accelerator.complaint.mgt.endpoint.dto;


import io.swagger.annotations.ApiModelProperty;
import java.util.Objects;
import com.fasterxml.jackson.annotation.JsonProperty;


public class ComplaintAttachmentDownloadResponse  {
  
  @ApiModelProperty(required = true, value = "")

  private String attachmentId;

  @ApiModelProperty(required = true, value = "")

  private String fileName;

  @ApiModelProperty(required = true, value = "")

  private String contentType;

 /**
  * Unix timestamp, milliseconds.
  */
  @ApiModelProperty(required = true, value = "Unix timestamp, milliseconds.")

  private Long uploadedTime;

  @ApiModelProperty(required = true, value = "")

  private String content;
 /**
   * Get attachmentId
   * @return attachmentId
  **/
  @JsonProperty("attachmentId")
  public String getAttachmentId() {
    return attachmentId;
  }

  public void setAttachmentId(String attachmentId) {
    this.attachmentId = attachmentId;
  }

  public ComplaintAttachmentDownloadResponse attachmentId(String attachmentId) {
    this.attachmentId = attachmentId;
    return this;
  }

 /**
   * Get fileName
   * @return fileName
  **/
  @JsonProperty("fileName")
  public String getFileName() {
    return fileName;
  }

  public void setFileName(String fileName) {
    this.fileName = fileName;
  }

  public ComplaintAttachmentDownloadResponse fileName(String fileName) {
    this.fileName = fileName;
    return this;
  }

 /**
   * Get contentType
   * @return contentType
  **/
  @JsonProperty("contentType")
  public String getContentType() {
    return contentType;
  }

  public void setContentType(String contentType) {
    this.contentType = contentType;
  }

  public ComplaintAttachmentDownloadResponse contentType(String contentType) {
    this.contentType = contentType;
    return this;
  }

 /**
   * Unix timestamp, milliseconds.
   * @return uploadedTime
  **/
  @JsonProperty("uploadedTime")
  public Long getUploadedTime() {
    return uploadedTime;
  }

  public void setUploadedTime(Long uploadedTime) {
    this.uploadedTime = uploadedTime;
  }

  public ComplaintAttachmentDownloadResponse uploadedTime(Long uploadedTime) {
    this.uploadedTime = uploadedTime;
    return this;
  }

 /**
   * Get content
   * @return content
  **/
  @JsonProperty("content")
  public String getContent() {
    return content;
  }

  public void setContent(String content) {
    this.content = content;
  }

  public ComplaintAttachmentDownloadResponse content(String content) {
    this.content = content;
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
    ComplaintAttachmentDownloadResponse complaintAttachmentDownloadResponse = (ComplaintAttachmentDownloadResponse) o;
    return Objects.equals(this.attachmentId, complaintAttachmentDownloadResponse.attachmentId) &&
        Objects.equals(this.fileName, complaintAttachmentDownloadResponse.fileName) &&
        Objects.equals(this.contentType, complaintAttachmentDownloadResponse.contentType) &&
        Objects.equals(this.uploadedTime, complaintAttachmentDownloadResponse.uploadedTime) &&
        Objects.equals(this.content, complaintAttachmentDownloadResponse.content);
  }

  @Override
  public int hashCode() {
    return Objects.hash(attachmentId, fileName, contentType, uploadedTime, content);
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append("class ComplaintAttachmentDownloadResponse {\n");
    
    sb.append("    attachmentId: ").append(toIndentedString(attachmentId)).append("\n");
    sb.append("    fileName: ").append(toIndentedString(fileName)).append("\n");
    sb.append("    contentType: ").append(toIndentedString(contentType)).append("\n");
    sb.append("    uploadedTime: ").append(toIndentedString(uploadedTime)).append("\n");
    sb.append("    content: ").append(toIndentedString(content)).append("\n");
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

