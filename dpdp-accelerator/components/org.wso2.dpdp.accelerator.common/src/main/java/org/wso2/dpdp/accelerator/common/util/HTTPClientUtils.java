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
