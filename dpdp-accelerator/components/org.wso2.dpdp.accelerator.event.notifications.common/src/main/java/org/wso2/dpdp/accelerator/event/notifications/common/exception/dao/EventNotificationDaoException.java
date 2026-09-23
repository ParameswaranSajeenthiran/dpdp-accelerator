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

package org.wso2.dpdp.accelerator.event.notifications.common.exception.dao;

import org.wso2.dpdp.accelerator.common.exception.DPDPException;

public class EventNotificationDaoException extends DPDPException {

    private static final String ERROR_CODE = "EN-DAO-001";
    private static final int HTTP_STATUS = 500;

    public EventNotificationDaoException(String message) {
        this(ERROR_CODE, message, HTTP_STATUS);
    }

    public EventNotificationDaoException(String message, Throwable cause) {
        this(ERROR_CODE, message, HTTP_STATUS, cause);
    }

    /** Lets a subtype report its own code/status. */
    protected EventNotificationDaoException(String errorCode, String message, int httpStatus) {
        super(errorCode, message, message, httpStatus);
    }

    /** Lets a subtype report its own code/status. */
    protected EventNotificationDaoException(String errorCode, String message, int httpStatus, Throwable cause) {
        super(errorCode, message, message, httpStatus, cause);
    }
}
