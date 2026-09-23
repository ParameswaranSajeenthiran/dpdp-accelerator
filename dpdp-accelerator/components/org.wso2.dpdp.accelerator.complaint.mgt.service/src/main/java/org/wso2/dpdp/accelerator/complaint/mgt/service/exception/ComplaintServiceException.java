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

package org.wso2.dpdp.accelerator.complaint.mgt.service.exception;

import org.wso2.dpdp.accelerator.common.exception.DPDPException;
import org.wso2.dpdp.accelerator.complaint.mgt.service.constants.ComplaintErrorCode;

public class ComplaintServiceException extends DPDPException {

    public ComplaintServiceException(String code, String message, String description, int statusCode) {
        super(code, message, description, statusCode);
    }

    public ComplaintServiceException(String code, String message, String description, int statusCode, Throwable cause) {
        super(code, message, description, statusCode, cause);
    }

    /** Preferred constructor - code/statusCode come from the shared ComplaintErrorCode. */
    public ComplaintServiceException(ComplaintErrorCode errorCode, String message) {
        this(errorCode.getCode(), message, message, errorCode.getHttpStatus());
    }

    /** Same as {@link #ComplaintServiceException(ComplaintErrorCode, String)}, preserving the original cause. */
    public ComplaintServiceException(ComplaintErrorCode errorCode, String message, Throwable cause) {
        this(errorCode.getCode(), message, message, errorCode.getHttpStatus(), cause);
    }

    public String getCode() {
        return getErrorCode();
    }

    public int getStatusCode() {
        return getHttpStatus();
    }
}
