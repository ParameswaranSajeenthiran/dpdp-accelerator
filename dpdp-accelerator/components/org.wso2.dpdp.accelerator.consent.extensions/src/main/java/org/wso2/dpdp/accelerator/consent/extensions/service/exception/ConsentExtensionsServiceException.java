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

package org.wso2.dpdp.accelerator.consent.extensions.service.exception;

import org.wso2.dpdp.accelerator.common.exception.DPDPException;

/** Thrown by the consent.extensions service layer after catching and translating a DAO failure. */
public class ConsentExtensionsServiceException extends DPDPException {

    private static final String ERROR_CODE = "CX-SVC-001";
    private static final int HTTP_STATUS = 500;

    public ConsentExtensionsServiceException(String message, Throwable cause) {

        super(ERROR_CODE, message, message, HTTP_STATUS, cause);
    }

    /** Lets a caller with its own public-facing error code attach it directly, rather than the
     * generic default - e.g. a REST resource's own documented error code. */
    public ConsentExtensionsServiceException(String errorCode, String message, int httpStatus, Throwable cause) {

        super(errorCode, message, message, httpStatus, cause);
    }
}
