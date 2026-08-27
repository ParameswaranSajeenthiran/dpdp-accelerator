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

package org.wso2.dpdp.common.util;

/**
 * Strips CR/LF from a value before it is interpolated into a log message. A path parameter or
 * other client-supplied string that reaches a log call unvalidated can otherwise be used to forge
 * fake log lines (CWE-117) - e.g. a {@code complaintId} of {@code "x\n[SEVERE] fake audit entry"}
 * would read as two log lines instead of one. Same approach FS Accelerator uses for this.
 */
public final class LogSanitizer {

    private LogSanitizer() {
    }

    public static String sanitize(String input) {
        if (input == null) {
            return null;
        }
        return input.replace("\n", "").replace("\r", "");
    }
}
