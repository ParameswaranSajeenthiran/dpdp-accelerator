/*
 * Copyright (c) 2026, WSO2 LLC. (https://www.wso2.com).
 *
 * WSO2 LLC. licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License.
 */
package org.wso2.dpdp.accelerator.common.util;

import java.net.http.HttpClient;
import java.time.Duration;

/** Shared HTTP client configuration for accelerator modules. */
public final class HTTPClientUtils {

    private static volatile HttpClient httpClient;

    private HTTPClientUtils() {
    }

    public static HttpClient getHttpClient() {

        if (httpClient == null) {
            synchronized (HTTPClientUtils.class) {
                if (httpClient == null) {
                    httpClient = HttpClient.newBuilder()
                            .connectTimeout(Duration.ofSeconds(10))
                            .followRedirects(HttpClient.Redirect.NEVER)
                            .build();
                }
            }
        }
        return httpClient;
    }
}
