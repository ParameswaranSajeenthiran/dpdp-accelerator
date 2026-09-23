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

package org.wso2.dpdp.accelerator.common.exception;

/**
 * Thrown for a system-level failure - {@code dpdp-accelerator.xml} failing to parse, a bundle's
 * own startup DB connectivity check failing, a JDBC commit/connection failure, or a missing Carbon
 * tenant context - rather than a business/domain one. Deliberately plain (message + cause only, no
 * {@code errorCode}/{@code httpStatus}) unlike {@link DPDPException}: no {@code ExceptionMapper}
 * reads a generic {@link DPDPException}'s own fields, only its own module's specific
 * {@code *ServiceException} subtype, so attaching a code/status here would just be unused
 * structure. Whatever REST layer's mapper is on the call stack (if any) falls through to its own
 * fixed generic-failure response either way.
 */
public class DPDPSystemException extends RuntimeException {

    public DPDPSystemException(String message) {

        super(message);
    }

    public DPDPSystemException(String message, Throwable cause) {

        super(message, cause);
    }
}
