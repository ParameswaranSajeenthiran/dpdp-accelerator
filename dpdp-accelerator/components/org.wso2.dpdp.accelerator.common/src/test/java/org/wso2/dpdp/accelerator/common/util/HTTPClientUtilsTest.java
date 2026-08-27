/*
 * Copyright (c) 2026, WSO2 LLC. (https://www.wso2.com).
 *
 * WSO2 LLC. licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License.
 */
package org.wso2.dpdp.accelerator.common.util;

import org.testng.annotations.Test;

import java.net.http.HttpClient;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertSame;

public class HTTPClientUtilsTest {

    @Test
    public void returnsSharedClientWithRedirectsDisabled() {

        HttpClient first = HTTPClientUtils.getHttpClient();
        HttpClient second = HTTPClientUtils.getHttpClient();

        assertSame(first, second);
        assertEquals(first.followRedirects(), HttpClient.Redirect.NEVER);
    }
}
