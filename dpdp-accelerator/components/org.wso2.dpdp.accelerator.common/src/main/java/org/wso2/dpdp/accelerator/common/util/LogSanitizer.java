/*
 * Copyright (c) 2026, WSO2 LLC. (https://www.wso2.com).
 *
 * WSO2 LLC. licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License.
 */
package org.wso2.dpdp.accelerator.common.util;

/** Utility for preventing CR/LF log injection when values are logged. */
public final class LogSanitizer {

    private LogSanitizer() {
    }

    public static String sanitize(String value) {
        return value == null ? null : value.replace("\r", "").replace("\n", "");
    }
}
