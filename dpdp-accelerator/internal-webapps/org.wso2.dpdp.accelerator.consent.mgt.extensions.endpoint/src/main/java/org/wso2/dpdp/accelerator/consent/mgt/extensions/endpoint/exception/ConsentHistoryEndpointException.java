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

package org.wso2.dpdp.accelerator.consent.mgt.extensions.endpoint.exception;

/**
 * Carries the HTTP status and {@code CH-*} error code a resource method wants returned; the
 * actual response is built by {@link org.wso2.dpdp.accelerator.consent.mgt.extensions.endpoint.error.ConsentHistoryEndpointExceptionMapper}.
 */
public class ConsentHistoryEndpointException extends RuntimeException {

    private final int httpStatus;
    private final String errorCode;
    private final String description;

    public ConsentHistoryEndpointException(int httpStatus, String errorCode, String description) {

        super(description);
        this.httpStatus = httpStatus;
        this.errorCode = errorCode;
        this.description = description;
    }

    public int getHttpStatus() {

        return httpStatus;
    }

    public String getErrorCode() {

        return errorCode;
    }

    public String getDescription() {

        return description;
    }
}
