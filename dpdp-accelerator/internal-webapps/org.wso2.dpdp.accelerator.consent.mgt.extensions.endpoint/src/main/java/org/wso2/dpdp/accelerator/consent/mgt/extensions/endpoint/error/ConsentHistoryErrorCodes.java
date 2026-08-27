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
 * Transport-layer error codes returned by the Consent History endpoint.
 */
public final class ConsentHistoryErrorCodes {

    public static final String INVALID_PARAMETER = "CH-00001";
    public static final String FORBIDDEN_NOT_OWNER = "CH-00002";
    public static final String NOT_FOUND = "CH-00003";
    public static final String SERVER_ERROR = "CH-00004";

    private ConsentHistoryErrorCodes() {

    }
}
