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


public class ComplaintAttachmentResponse  {
  
  @ApiModelProperty(required = true, value = "")

  private String attachmentId;

 /**
  * The timeline entry this attachment was uploaded under - see ComplaintTimelineEntryResponse.attachments. 
  */
  @ApiModelProperty(value = "The timeline entry this attachment was uploaded under - see ComplaintTimelineEntryResponse.attachments. ")

  private String complaintEventId;

  @ApiModelProperty(required = true, value = "")

  private String fileName;

  @ApiModelProperty(required = true, value = "")

  private String contentType;

  @ApiModelProperty(required = true, value = "")

  private Long sizeBytes;

 /**
  * Whether this attachment is visible to the Data Principal via the /me endpoints. Always true for attachments uploaded through /me/complaints/{complaintId}/attachments. 
  */
  @ApiModelProperty(required = true, value = "Whether this attachment is visible to the Data Principal via the /me endpoints. Always true for attachments uploaded through /me/complaints/{complaintId}/attachments. ")

  private Boolean isPublic;

 /**
  * Unix timestamp, milliseconds.
  */
  @ApiModelProperty(required = true, value = "Unix timestamp, milliseconds.")

  private Long uploadedTime;
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

  public ComplaintAttachmentResponse attachmentId(String attachmentId) {
    this.attachmentId = attachmentId;
    return this;
  }

 /**
   * The timeline entry this attachment was uploaded under - see ComplaintTimelineEntryResponse.attachments. 
   * @return complaintEventId
  **/
  @JsonProperty("complaintEventId")
  public String getComplaintEventId() {
    return complaintEventId;
  }

  public void setComplaintEventId(String complaintEventId) {
    this.complaintEventId = complaintEventId;
  }

  public ComplaintAttachmentResponse complaintEventId(String complaintEventId) {
    this.complaintEventId = complaintEventId;
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

  public ComplaintAttachmentResponse fileName(String fileName) {
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

  public ComplaintAttachmentResponse contentType(String contentType) {
    this.contentType = contentType;
    return this;
  }

 /**
   * Get sizeBytes
   * @return sizeBytes
  **/
  @JsonProperty("sizeBytes")
  public Long getSizeBytes() {
    return sizeBytes;
  }

  public void setSizeBytes(Long sizeBytes) {
    this.sizeBytes = sizeBytes;
  }

  public ComplaintAttachmentResponse sizeBytes(Long sizeBytes) {
    this.sizeBytes = sizeBytes;
    return this;
  }

 /**
   * Whether this attachment is visible to the Data Principal via the /me endpoints. Always true for attachments uploaded through /me/complaints/{complaintId}/attachments. 
   * @return isPublic
  **/
  @JsonProperty("isPublic")
  public Boolean getIsPublic() {
    return isPublic;
  }

  public void setIsPublic(Boolean isPublic) {
    this.isPublic = isPublic;
  }

  public ComplaintAttachmentResponse isPublic(Boolean isPublic) {
    this.isPublic = isPublic;
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

  public ComplaintAttachmentResponse uploadedTime(Long uploadedTime) {
    this.uploadedTime = uploadedTime;
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
    ComplaintAttachmentResponse complaintAttachmentResponse = (ComplaintAttachmentResponse) o;
    return Objects.equals(this.attachmentId, complaintAttachmentResponse.attachmentId) &&
        Objects.equals(this.complaintEventId, complaintAttachmentResponse.complaintEventId) &&
        Objects.equals(this.fileName, complaintAttachmentResponse.fileName) &&
        Objects.equals(this.contentType, complaintAttachmentResponse.contentType) &&
        Objects.equals(this.sizeBytes, complaintAttachmentResponse.sizeBytes) &&
        Objects.equals(this.isPublic, complaintAttachmentResponse.isPublic) &&
        Objects.equals(this.uploadedTime, complaintAttachmentResponse.uploadedTime);
  }

  @Override
  public int hashCode() {
    return Objects.hash(attachmentId, complaintEventId, fileName, contentType, sizeBytes, isPublic, uploadedTime);
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append("class ComplaintAttachmentResponse {\n");
    
    sb.append("    attachmentId: ").append(toIndentedString(attachmentId)).append("\n");
    sb.append("    complaintEventId: ").append(toIndentedString(complaintEventId)).append("\n");
    sb.append("    fileName: ").append(toIndentedString(fileName)).append("\n");
    sb.append("    contentType: ").append(toIndentedString(contentType)).append("\n");
    sb.append("    sizeBytes: ").append(toIndentedString(sizeBytes)).append("\n");
    sb.append("    isPublic: ").append(toIndentedString(isPublic)).append("\n");
    sb.append("    uploadedTime: ").append(toIndentedString(uploadedTime)).append("\n");
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

