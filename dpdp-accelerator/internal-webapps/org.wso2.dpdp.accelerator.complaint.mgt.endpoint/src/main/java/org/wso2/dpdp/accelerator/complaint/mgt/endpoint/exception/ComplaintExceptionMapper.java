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

package org.wso2.dpdp.accelerator.complaint.mgt.endpoint.exception;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.exc.InvalidFormatException;
import com.fasterxml.jackson.databind.exc.ValueInstantiationException;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.wso2.dpdp.accelerator.common.util.LogSanitizer;
import org.wso2.dpdp.accelerator.complaint.mgt.endpoint.dto.ErrorEnvelope;
import org.wso2.dpdp.accelerator.complaint.mgt.service.constants.ComplaintErrorCode;
import org.wso2.dpdp.accelerator.complaint.mgt.service.exception.ComplaintServiceException;

import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.ext.ExceptionMapper;
import javax.ws.rs.ext.Provider;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Provider
public class ComplaintExceptionMapper implements ExceptionMapper<Throwable> {

    private static final Log LOG = LogFactory.getLog(ComplaintExceptionMapper.class);

    private static final String INVALID_ENUM_VALUE_ERROR =
            "Field '%s' must be one of the defined %s enum values.";
    private static final String MALFORMED_BODY_ERROR = "Request body is not valid JSON for this operation.";

    @Override
    public Response toResponse(Throwable exception) {
        // CXF wraps a failed body read in a BadRequestException, so the Jackson error (or a
        // ComplaintServiceException thrown from deeper in the stack) is usually a cause, not the top.
        ComplaintServiceException complaintServiceException = findCause(exception, ComplaintServiceException.class);
        if (complaintServiceException != null) {
            return build(complaintServiceException);
        }
        JsonProcessingException jsonException = findCause(exception, JsonProcessingException.class);
        if (jsonException != null) {
            return build(fromJsonException(jsonException));
        }
        if (exception instanceof WebApplicationException) {
            Response original = ((WebApplicationException) exception).getResponse();
            int status = original.getStatus();
            if (status < 500) {
                if (LOG.isDebugEnabled()) {
                    LOG.debug("JAX-RS exception [" + status + "]: "
                            + LogSanitizer.sanitize(exception.getMessage()));
                }
                Response.Status reason = Response.Status.fromStatusCode(status);
                String message = reason != null ? reason.getReasonPhrase() : String.valueOf(status);
                Response.ResponseBuilder builder = builder(new ComplaintServiceException(
                        errorCodeFor(status).getCode(), message, message, status));
                copyHeaders(original, builder);
                return builder.build();
            }
        }

        LOG.error("Unhandled exception in Complaint API: " + LogSanitizer.sanitize(exception.getMessage()), exception);
        return build(new ComplaintServiceException(ComplaintErrorCode.INTERNAL_ERROR.getCode(), "Internal error",
                "An unexpected error occurred while processing the request.",
                Response.Status.INTERNAL_SERVER_ERROR.getStatusCode()));
    }

    /**
     * An unknown enum value keeps the 422 CO-4002 the service returned for it before the request
     * models were generated with enum types; any other unreadable body is a 400 CO-4001.
     */
    private static ComplaintServiceException fromJsonException(JsonProcessingException exception) {
        if (exception instanceof JsonMappingException) {
            JsonMappingException mappingException = (JsonMappingException) exception;
            Class<?> enumType = enumTarget(mappingException);
            if (enumType != null) {
                String field = fieldName(mappingException.getPath());
                if (LOG.isDebugEnabled()) {
                    LOG.debug("Invalid enum value in request body for field: " + LogSanitizer.sanitize(field));
                }
                return new ComplaintServiceException(ComplaintErrorCode.VALIDATION_FAILED,
                        String.format(INVALID_ENUM_VALUE_ERROR, field, enumType.getSimpleName()));
            }
        }
        if (LOG.isDebugEnabled()) {
            LOG.debug("Malformed request body: " + LogSanitizer.sanitize(exception.getOriginalMessage()));
        }
        return new ComplaintServiceException(ComplaintErrorCode.INVALID_REQUEST_BODY, MALFORMED_BODY_ERROR);
    }

    private static Class<?> enumTarget(JsonMappingException exception) {
        if (exception instanceof InvalidFormatException) {
            Class<?> target = ((InvalidFormatException) exception).getTargetType();
            return target != null && target.isEnum() ? target : null;
        }
        if (exception instanceof ValueInstantiationException) {
            Class<?> target = ((ValueInstantiationException) exception).getType().getRawClass();
            return target.isEnum() ? target : null;
        }
        return null;
    }

    private static String fieldName(List<JsonMappingException.Reference> path) {
        for (int i = path.size() - 1; i >= 0; i--) {
            String name = path.get(i).getFieldName();
            if (name != null) {
                return name;
            }
        }
        return "";
    }

    private static ComplaintErrorCode errorCodeFor(int status) {
        switch (status) {
            case 401:
                return ComplaintErrorCode.UNAUTHENTICATED;
            case 403:
                return ComplaintErrorCode.FORBIDDEN;
            case 404:
                return ComplaintErrorCode.COMPLAINT_NOT_FOUND;
            default:
                return ComplaintErrorCode.INVALID_REQUEST_BODY;
        }
    }

    private static <T extends Throwable> T findCause(Throwable exception, Class<T> type) {
        Set<Throwable> visited = Collections.newSetFromMap(new IdentityHashMap<>());
        for (Throwable current = exception; current != null && visited.add(current); current = current.getCause()) {
            if (type.isInstance(current)) {
                return type.cast(current);
            }
        }
        return null;
    }

    /**
     * Keeps headers the framework attached to its own error response - e.g. the Allow header CXF
     * sets on a 405 - but not the ones describing the original body, which is replaced here.
     */
    private static void copyHeaders(Response original, Response.ResponseBuilder target) {
        for (Map.Entry<String, List<Object>> header : original.getHeaders().entrySet()) {
            if (HttpHeaders.CONTENT_TYPE.equalsIgnoreCase(header.getKey())
                    || HttpHeaders.CONTENT_LENGTH.equalsIgnoreCase(header.getKey())
                    || HttpHeaders.CONTENT_ENCODING.equalsIgnoreCase(header.getKey())) {
                continue;
            }
            for (Object value : header.getValue()) {
                target.header(header.getKey(), value);
            }
        }
    }

    private static Response build(ComplaintServiceException exception) {
        return builder(exception).build();
    }

    private static Response.ResponseBuilder builder(ComplaintServiceException exception) {
        ErrorEnvelope envelope = new ErrorEnvelope()
                .code(exception.getCode())
                .message(exception.getMessage())
                .description(exception.getDescription())
                .traceId(UUID.randomUUID().toString());
        return Response.status(exception.getStatusCode())
                .type(MediaType.APPLICATION_JSON)
                .entity(envelope);
    }
}
