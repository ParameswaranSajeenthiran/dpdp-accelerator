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

package org.wso2.dpdp.accelerator.consent.extensions.dao.exceptions;

/**
 * Thrown when a {@code DPDP_CONSENT_EXPIRY_TRACKER} row could not be written or read. One type
 * for every operation (unlike the history DAO's insert/retrieval split) - the tracker table is
 * simple internal bookkeeping with a single caller, {@code ConsentExpiryServiceImpl}, that
 * doesn't need to distinguish failure modes.
 */
public class ConsentExpiryDataAccessException extends Exception {

    public ConsentExpiryDataAccessException(String message, Throwable cause) {

        super(message, cause);
    }
}
