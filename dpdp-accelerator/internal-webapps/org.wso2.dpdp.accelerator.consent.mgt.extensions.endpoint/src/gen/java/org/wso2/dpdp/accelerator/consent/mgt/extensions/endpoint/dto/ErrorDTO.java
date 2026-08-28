/**
 * Copyright (c) 2026, WSO2 LLC. (https://www.wso2.com).
 * <p>
 * WSO2 LLC. licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 *     http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.wso2.dpdp.accelerator.consent.mgt.extensions.endpoint.dto;


import io.swagger.annotations.ApiModelProperty;
import java.util.Objects;
import com.fasterxml.jackson.annotation.JsonProperty;

@com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)

public class ErrorDTO  {
  
  @ApiModelProperty(value = "One of the CH-<HTTP status><sequence> codes above.")
 /**
   * One of the CH-<HTTP status><sequence> codes above.
  **/
  private String code;

  @ApiModelProperty(value = "The HTTP status reason phrase, e.g. \"Not Found\".")
 /**
   * The HTTP status reason phrase, e.g. \"Not Found\".
  **/
  private String message;

  @ApiModelProperty(value = "")
  private String description;

  @ApiModelProperty(value = "A random UUID logged alongside the underlying error, for correlation.")
 /**
   * A random UUID logged alongside the underlying error, for correlation.
  **/
  private String traceId;
 /**
   * One of the CH-&lt;HTTP status&gt;&lt;sequence&gt; codes above.
   * @return code
  **/
  @JsonProperty("code")
  public String getCode() {
    return code;
  }

  public void setCode(String code) {
    this.code = code;
  }

  public ErrorDTO code(String code) {
    this.code = code;
    return this;
  }

 /**
   * The HTTP status reason phrase, e.g. \&quot;Not Found\&quot;.
   * @return message
  **/
  @JsonProperty("message")
  public String getMessage() {
    return message;
  }

  public void setMessage(String message) {
    this.message = message;
  }

  public ErrorDTO message(String message) {
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

  public ErrorDTO description(String description) {
    this.description = description;
    return this;
  }

 /**
   * A random UUID logged alongside the underlying error, for correlation.
   * @return traceId
  **/
  @JsonProperty("traceId")
  public String getTraceId() {
    return traceId;
  }

  public void setTraceId(String traceId) {
    this.traceId = traceId;
  }

  public ErrorDTO traceId(String traceId) {
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
    ErrorDTO errorDTO = (ErrorDTO) o;
    return Objects.equals(code, errorDTO.code) &&
        Objects.equals(message, errorDTO.message) &&
        Objects.equals(description, errorDTO.description) &&
        Objects.equals(traceId, errorDTO.traceId);
  }

  @Override
  public int hashCode() {
    return Objects.hash(code, message, description, traceId);
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append("class ErrorDTO {\n");
    
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
    if (o == null) {
      return "null";
    }
    return o.toString().replace("\n", "\n    ");
  }
}

