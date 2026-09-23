package org.wso2.dpdp.accelerator.complaint.mgt.endpoint.dto;


import io.swagger.annotations.ApiModelProperty;
import java.util.Objects;
import com.fasterxml.jackson.annotation.JsonProperty;


public class ErrorEnvelope  {
  
  @ApiModelProperty(required = true, value = "")

  private String code;

  @ApiModelProperty(required = true, value = "")

  private String message;

  @ApiModelProperty(required = true, value = "")

  private String description;

  @ApiModelProperty(required = true, value = "")

  private String traceId;
 /**
   * Get code
   * @return code
  **/
  @JsonProperty("code")
  public String getCode() {
    return code;
  }

  public void setCode(String code) {
    this.code = code;
  }

  public ErrorEnvelope code(String code) {
    this.code = code;
    return this;
  }

 /**
   * Get message
   * @return message
  **/
  @JsonProperty("message")
  public String getMessage() {
    return message;
  }

  public void setMessage(String message) {
    this.message = message;
  }

  public ErrorEnvelope message(String message) {
    this.message = message;
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

  public ErrorEnvelope description(String description) {
    this.description = description;
    return this;
  }

 /**
   * Get traceId
   * @return traceId
  **/
  @JsonProperty("traceId")
  public String getTraceId() {
    return traceId;
  }

  public void setTraceId(String traceId) {
    this.traceId = traceId;
  }

  public ErrorEnvelope traceId(String traceId) {
    this.traceId = traceId;
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
    ErrorEnvelope errorEnvelope = (ErrorEnvelope) o;
    return Objects.equals(this.code, errorEnvelope.code) &&
        Objects.equals(this.message, errorEnvelope.message) &&
        Objects.equals(this.description, errorEnvelope.description) &&
        Objects.equals(this.traceId, errorEnvelope.traceId);
  }

  @Override
  public int hashCode() {
    return Objects.hash(code, message, description, traceId);
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append("class ErrorEnvelope {\n");
    
    sb.append("    code: ").append(toIndentedString(code)).append("\n");
    sb.append("    message: ").append(toIndentedString(message)).append("\n");
    sb.append("    description: ").append(toIndentedString(description)).append("\n");
    sb.append("    traceId: ").append(toIndentedString(traceId)).append("\n");
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

