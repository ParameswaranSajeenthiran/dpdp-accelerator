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

package org.wso2.dpdp.accelerator.event.notifications.endpoint.exception;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.exc.UnrecognizedPropertyException;
import org.wso2.dpdp.accelerator.event.notifications.endpoint.dto.ErrorResponse;
import org.wso2.dpdp.accelerator.event.notifications.service.exception.ENFException;

import javax.validation.ConstraintViolationException;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.ext.ExceptionMapper;
import javax.ws.rs.ext.Provider;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Unified JAX-RS {@link ExceptionMapper} that converts service layer {@link ENFException}s,
 * JAX-RS {@link WebApplicationException}s, bean validation errors, Jackson JSON parsing errors,
 * and unhandled exceptions into well-formed JSON {@link ErrorResponse}s.
 */
@Provider
public class ENFExceptionMapper implements ExceptionMapper<Throwable> {

    private static final Logger log = Logger.getLogger(ENFExceptionMapper.class.getName());

    @Override
    public Response toResponse(Throwable exception) {

        if (exception instanceof ENFException) {
            return handleENFException((ENFException) exception);
        }

        Throwable rootCause = unwrap(exception);

        if (rootCause instanceof ENFException) {
            return handleENFException((ENFException) rootCause);
        }

        if (rootCause instanceof WebApplicationException) {
            return handleWebApplicationException((WebApplicationException) rootCause);
        }

        if (rootCause instanceof ConstraintViolationException) {
            return handleConstraintViolation((ConstraintViolationException) rootCause);
        }

        if (rootCause instanceof JsonProcessingException) {
            return handleJsonProcessingException((JsonProcessingException) rootCause);
        }

        if (rootCause instanceof IllegalArgumentException) {
            log.log(Level.WARNING, "Invalid request argument: " + rootCause.getMessage());
            ErrorResponse body = new ErrorResponse(
                    "CS-4002",
                    "Invalid request parameter",
                    rootCause.getMessage()
            );
            return Response.status(Response.Status.BAD_REQUEST)
                    .type(MediaType.APPLICATION_JSON)
                    .entity(body)
                    .build();
        }

        // Generic fallback for uncaught runtime errors
        log.log(Level.SEVERE, "Unhandled exception in ENF endpoint", exception);

        ErrorResponse body = new ErrorResponse(
                "CS-5000",
                "Internal server error",
                "An unexpected error occurred."
        );

        return Response
                .status(Response.Status.INTERNAL_SERVER_ERROR)
                .type(MediaType.APPLICATION_JSON)
                .entity(body)
                .build();
    }

    private Response handleENFException(ENFException enfEx) {

        if (enfEx.getStatusCode() >= 500) {
            log.log(Level.SEVERE, "Service error [" + enfEx.getCode() + "]: " + enfEx.getMessage(), enfEx);
        } else {
            log.log(Level.WARNING, "Service error [" + enfEx.getCode() + "]: " + enfEx.getMessage());
        }

        ErrorResponse body = new ErrorResponse(
                enfEx.getCode(),
                enfEx.getMessage(),
                enfEx.getDescription()
        );

        return Response
                .status(enfEx.getStatusCode())
                .type(MediaType.APPLICATION_JSON)
                .entity(body)
                .build();
    }

    private Response handleWebApplicationException(WebApplicationException wae) {

        int status = wae.getResponse().getStatus();
        log.log(Level.WARNING, "JAX-RS exception [" + status + "]: " + wae.getMessage());

        ErrorResponse body = new ErrorResponse(
                "CS-" + status,
                wae.getMessage() != null ? wae.getMessage() : Response.Status.fromStatusCode(status).getReasonPhrase(),
                null
        );

        return Response.status(status)
                .type(MediaType.APPLICATION_JSON)
                .entity(body)
                .build();
    }

    private Response handleConstraintViolation(ConstraintViolationException cve) {

        String detail = cve.getConstraintViolations().stream()
                .map(v -> v.getPropertyPath() + " " + v.getMessage())
                .reduce((a, b) -> a + "; " + b)
                .orElse(cve.getMessage());

        log.log(Level.WARNING, "Validation failure: " + detail);

        ErrorResponse body = new ErrorResponse(
                "CS-4003",
                "Request failed validation",
                detail
        );

        return Response.status(Response.Status.BAD_REQUEST)
                .type(MediaType.APPLICATION_JSON)
                .entity(body)
                .build();
    }

    private Response handleJsonProcessingException(JsonProcessingException jpe) {

        String detail = jpe.getOriginalMessage();
        if (jpe instanceof UnrecognizedPropertyException) {
            UnrecognizedPropertyException upe = (UnrecognizedPropertyException) jpe;
            detail = "Unrecognized field '" + upe.getPropertyName() + "' in request payload.";
        }

        log.log(Level.WARNING, "Malformed request payload: " + detail);

        ErrorResponse body = new ErrorResponse(
                "CS-4001",
                "Malformed request payload",
                detail
        );

        return Response.status(Response.Status.BAD_REQUEST)
                .type(MediaType.APPLICATION_JSON)
                .entity(body)
                .build();
    }

    /**
     * Walks the cause chain to the deepest non-null, non-cyclic cause.
     * JAX-RS/CXF frequently wrap the real exception 2-3 levels deep
     * (e.g. InvocationTargetException -> ProcessingException -> actual cause).
     */
    private Throwable unwrap(Throwable exception) {

        Throwable current = exception;
        while (current.getCause() != null && current.getCause() != current) {
            current = current.getCause();
        }
        return current;
    }
}