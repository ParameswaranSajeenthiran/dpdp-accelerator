/*
 * Copyright (c) 2026, WSO2 LLC. (https://www.wso2.com).
 *
 * WSO2 LLC. licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License.
 */
package org.wso2.dpdp.accelerator.common.util;

import org.testng.Assert;
import org.testng.annotations.Test;

public class LogSanitizerTest {

    @Test
    public void sanitizeRemovesLineBreaks() {
        Assert.assertEquals(LogSanitizer.sanitize("a\r\nb\nc"), "abc");
        Assert.assertNull(LogSanitizer.sanitize(null));
    }
}
