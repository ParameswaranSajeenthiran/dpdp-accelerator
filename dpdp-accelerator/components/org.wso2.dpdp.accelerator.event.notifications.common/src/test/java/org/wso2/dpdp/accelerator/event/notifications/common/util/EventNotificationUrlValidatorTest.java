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

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

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

    @Test
    public void testAllowsCustomPortWhenConfigured() throws Exception {

        Set<Integer> allowedPorts = new HashSet<>(Arrays.asList(-1, 80, 443, 8443, 9443));
        EventNotificationUrlValidator.validate("https://203.0.113.10:9443/hook", allowedPorts, false);
    }

    @Test
    public void testRejectsPortOutsideConfiguredAllowList() {

        Set<Integer> allowedPorts = Collections.singleton(443);
        Assert.expectThrows(IllegalArgumentException.class,
                () -> EventNotificationUrlValidator.validate("https://203.0.113.10:9443/hook", allowedPorts, false));
    }

    @Test
    public void testRejectsPrivateIpv4AddressWhenPrivateNetworkTargetsNotAllowed() {

        Set<Integer> allowedPorts = Collections.singleton(443);
        Assert.expectThrows(IllegalArgumentException.class,
                () -> EventNotificationUrlValidator.validate("https://192.168.1.10:443/hook", allowedPorts, false));
    }

    @Test
    public void testAllowsPrivateIpv4AddressWhenPrivateNetworkTargetsAllowed() throws Exception {

        Set<Integer> allowedPorts = Collections.singleton(443);
        EventNotificationUrlValidator.validate("https://192.168.1.10:443/hook", allowedPorts, true);
    }

    @Test
    public void testAllowsIpv6UniqueLocalAddressWhenPrivateNetworkTargetsAllowed() throws Exception {

        Set<Integer> allowedPorts = Collections.singleton(-1);
        EventNotificationUrlValidator.validate("https://[fd00::1]/hook", allowedPorts, true);
    }

    @Test
    public void testRejectsLoopbackAddressEvenWhenPrivateNetworkTargetsAllowed() {

        Set<Integer> allowedPorts = Collections.singleton(-1);
        Assert.expectThrows(IllegalArgumentException.class,
                () -> EventNotificationUrlValidator.validate("http://127.0.0.1/hook", allowedPorts, true));
    }

    @Test
    public void testRejectsAnyLocalAddressEvenWhenPrivateNetworkTargetsAllowed() {

        Set<Integer> allowedPorts = Collections.singleton(-1);
        Assert.expectThrows(IllegalArgumentException.class,
                () -> EventNotificationUrlValidator.validate("http://0.0.0.0/hook", allowedPorts, true));
    }

    @Test
    public void testRejectsMulticastAddressEvenWhenPrivateNetworkTargetsAllowed() {

        Set<Integer> allowedPorts = Collections.singleton(-1);
        Assert.expectThrows(IllegalArgumentException.class,
                () -> EventNotificationUrlValidator.validate("http://224.0.0.1/hook", allowedPorts, true));
    }
}
