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

package org.wso2.dpdp.accelerator.consent.mgt.extensions.endpoint.error;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.wso2.dpdp.accelerator.consent.mgt.extensions.endpoint.dto.ErrorDTO;
import org.wso2.dpdp.accelerator.consent.mgt.extensions.endpoint.exception.ConsentHistoryEndpointException;

import java.util.UUID;

import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.ext.ExceptionMapper;
import javax.ws.rs.ext.Provider;

/**
 * Registered via {@code jaxrs.providers} in web.xml (not {@code @Provider}-scanned, matching the
 * product's own CXF webapps). Anything other than {@link ConsentHistoryEndpointException} is an
 * unexpected failure - logged with the real exception, returned as a generic 500 so internals are
 * never leaked to the caller.
 */
@Provider
public class ConsentHistoryEndpointExceptionMapper implements ExceptionMapper<Throwable> {

    private static final Log LOG = LogFactory.getLog(ConsentHistoryEndpointExceptionMapper.class);

    @Override
    public Response toResponse(Throwable throwable) {

        String traceId = UUID.randomUUID().toString();
        if (throwable instanceof ConsentHistoryEndpointException) {
            ConsentHistoryEndpointException e = (ConsentHistoryEndpointException) throwable;
            LOG.debug("Returning " + e.getHttpStatus() + " (" + e.getErrorCode() + ") for: " + e.getDescription());
            return buildResponse(e.getHttpStatus(), e.getErrorCode(), e.getDescription(), traceId);
        }

        LOG.error("Unexpected error handling a consent history request. traceId=" + traceId, throwable);
        return buildResponse(Response.Status.INTERNAL_SERVER_ERROR.getStatusCode(),
                ConsentHistoryErrorCodes.SERVER_ERROR, "An unexpected error occurred.", traceId);
    }

    private Response buildResponse(int httpStatus, String errorCode, String description, String traceId) {

        Response.StatusType statusType = Response.Status.fromStatusCode(httpStatus);
        String message = statusType != null ? statusType.getReasonPhrase() : "Error";
        ErrorDTO errorDTO = new ErrorDTO().code(errorCode).message(message).description(description)
                .traceId(traceId);
        return Response.status(httpStatus).entity(errorDTO).type(MediaType.APPLICATION_JSON).build();
    }
}
