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

package org.wso2.dpdp.accelerator.complaint.mgt.dao.exception;

import org.wso2.dpdp.accelerator.common.exception.DPDPException;

/**
 * Wraps a {@link java.sql.SQLException} raised by the persistence layer. Unchecked so DAO
 * interfaces stay free of throws clauses; it propagates through the service layer to the
 * endpoint's generic exception mapper rather than being mistaken for a "not found" result.
 */
public class ComplaintDAOException extends DPDPException {

    private static final String ERROR_CODE = "CO-DAO-001";
    private static final int HTTP_STATUS = 500;

    public ComplaintDAOException(String message, Throwable cause) {
        this(ERROR_CODE, message, HTTP_STATUS, cause);
    }

    /** Lets a subtype (e.g. {@link DuplicateReferenceIdException}) report its own code/status. */
    protected ComplaintDAOException(String errorCode, String message, int httpStatus, Throwable cause) {
        super(errorCode, message, message, httpStatus, cause);
    }
}
