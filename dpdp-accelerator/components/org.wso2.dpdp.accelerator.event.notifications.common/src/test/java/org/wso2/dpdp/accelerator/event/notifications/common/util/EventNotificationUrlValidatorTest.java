/**
 * Copyright (c) 2026, WSO2 LLC. (https://www.wso2.com).
 *
 * WSO2 LLC. licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.wso2.dpdp.accelerator.event.notifications.common.util;

import org.testng.Assert;
import org.testng.annotations.Test;

public class EventNotificationUrlValidatorTest {

    @Test
    public void testRejectsUnsupportedScheme() {

        Assert.expectThrows(IllegalArgumentException.class,
                () -> EventNotificationUrlValidator.validate("ftp://203.0.113.10/hook"));
    }

    @Test
    public void testRejectsUnsupportedPort() {

        Assert.expectThrows(IllegalArgumentException.class,
                () -> EventNotificationUrlValidator.validate("https://203.0.113.10:8080/hook"));
    }

    @Test
    public void testRejectsLoopbackAddress() {

        Assert.expectThrows(IllegalArgumentException.class,
                () -> EventNotificationUrlValidator.validate("http://127.0.0.1/hook"));
    }

    @Test
    public void testRejectsIpv6UniqueLocalAddress() {

        Assert.expectThrows(IllegalArgumentException.class,
                () -> EventNotificationUrlValidator.validate("https://[fd00::1]/hook"));
    }

    @Test
    public void testRejectsPrivateIpv4Address() {

        Assert.expectThrows(IllegalArgumentException.class,
                () -> EventNotificationUrlValidator.validate("https://192.168.1.10/hook"));
    }

    @Test
    public void testRejectsCallbackFragmentBeforeResolution() {

        Assert.expectThrows(IllegalArgumentException.class,
                () -> EventNotificationUrlValidator.validate("https://callback.invalid/hook#ignored"));
    }
}
