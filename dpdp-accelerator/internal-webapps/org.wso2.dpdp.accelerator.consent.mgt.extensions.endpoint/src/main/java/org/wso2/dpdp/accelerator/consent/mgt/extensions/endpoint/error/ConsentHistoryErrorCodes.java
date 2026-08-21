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

/**
 * Codes are {@code CH-<HTTP status><sequence>} - the status is readable straight off the code
 * without cross-referencing this class, and CH keeps it unambiguous which module raised it
 * alongside other accelerator modules' own error codes.
 */
public final class ConsentHistoryErrorCodes {

    public static final String INVALID_PARAMETER = "CH-4001";
    public static final String FORBIDDEN_NOT_OWNER = "CH-4031";
    public static final String NOT_FOUND = "CH-4041";
    public static final String SERVER_ERROR = "CH-5001";

    private ConsentHistoryErrorCodes() {

    }
}
