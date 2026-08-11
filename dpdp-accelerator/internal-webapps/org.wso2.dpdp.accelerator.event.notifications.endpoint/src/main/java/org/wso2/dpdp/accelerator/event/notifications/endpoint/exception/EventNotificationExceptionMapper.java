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
import org.wso2.dpdp.accelerator.event.notifications.service.exception.EventNotificationException;

import javax.validation.ConstraintViolationException;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.ext.ExceptionMapper;
import javax.ws.rs.ext.Provider;
import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

@Provider
public class EventNotificationExceptionMapper implements ExceptionMapper<Throwable> {

    private static final Logger log = Logger.getLogger(EventNotificationExceptionMapper.class.getName());

    @Override
    public Response toResponse(Throwable exception) {

        if (exception instanceof EventNotificationException) {
            return handleEventNotificationException((EventNotificationException) exception);
        }

        Throwable rootCause = unwrap(exception);

        if (rootCause instanceof EventNotificationException) {
            return handleEventNotificationException((EventNotificationException) rootCause);
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
            return buildResponse(Response.Status.BAD_REQUEST.getStatusCode(), "CS-4002", "Invalid request parameter", rootCause.getMessage());
        }

        log.log(Level.SEVERE, "Unhandled exception in Event Notification endpoint", exception);
        return buildResponse(Response.Status.INTERNAL_SERVER_ERROR.getStatusCode(), "CS-5000", "Internal server error", "An unexpected error occurred.");
    }

    private Response handleEventNotificationException(EventNotificationException ex) {
        if (ex.getStatusCode() >= 500) {
            log.log(Level.SEVERE, "Service error [" + ex.getCode() + "]: " + ex.getMessage(), ex);
        } else {
            log.log(Level.WARNING, "Service error [" + ex.getCode() + "]: " + ex.getMessage());
        }
        return buildResponse(ex.getStatusCode(), ex.getCode(), ex.getMessage(), ex.getDescription());
    }

    private Response handleWebApplicationException(WebApplicationException wae) {
        int status = wae.getResponse().getStatus();
        log.log(Level.WARNING, "JAX-RS exception [" + status + "]: " + wae.getMessage());
        return buildResponse(status, "CS-" + status, wae.getMessage() != null ? wae.getMessage() : Response.Status.fromStatusCode(status).getReasonPhrase(), null);
    }

    private Response handleConstraintViolation(ConstraintViolationException cve) {
        String detail = cve.getConstraintViolations().stream()
                .map(v -> v.getPropertyPath() + " " + v.getMessage())
                .reduce((a, b) -> a + "; " + b)
                .orElse(cve.getMessage());

        log.log(Level.WARNING, "Validation failure: " + detail);
        return buildResponse(Response.Status.BAD_REQUEST.getStatusCode(), "CS-4003", "Request failed validation", detail);
    }

    private Response handleJsonProcessingException(JsonProcessingException jpe) {
        String detail = jpe.getOriginalMessage();
        if (jpe instanceof UnrecognizedPropertyException) {
            UnrecognizedPropertyException upe = (UnrecognizedPropertyException) jpe;
            detail = "Unrecognized field '" + upe.getPropertyName() + "' in request payload.";
        }

        log.log(Level.WARNING, "Malformed request payload: " + detail);
        return buildResponse(Response.Status.BAD_REQUEST.getStatusCode(), "CS-4001", "Malformed request payload", detail);
    }

    private Response buildResponse(int status, String code, String message, String description) {
        Map<String, Object> body = new HashMap<>();
        body.put("code", code);
        body.put("message", message);
        if (description != null) {
            body.put("description", description);
        }

        return Response.status(status > 0 ? status : 500)
                .type(MediaType.APPLICATION_JSON)
                .entity(body)
                .build();
    }

    private Throwable unwrap(Throwable exception) {
        Throwable current = exception;
        Set<Throwable> visited = Collections.newSetFromMap(new IdentityHashMap<>());

        while (current != null) {
            if (!visited.add(current)) {
                break; // Cycle detected
            }
            if (current instanceof EventNotificationException) {
                return current;
            }
            if (current.getCause() == null || current.getCause() == current) {
                break;
            }
            current = current.getCause();
        }
        return current != null ? current : exception;
    }
}
