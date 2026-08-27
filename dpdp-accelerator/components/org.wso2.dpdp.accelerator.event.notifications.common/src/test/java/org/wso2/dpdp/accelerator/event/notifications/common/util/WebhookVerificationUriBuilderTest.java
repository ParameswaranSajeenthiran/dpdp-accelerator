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

import java.net.URI;

public class WebhookVerificationUriBuilderTest {

    @Test
    public void testBuildsVerificationUriWithoutExistingQuery() {

        URI result = WebhookVerificationUriBuilder.build(URI.create("https://example.com/callback"),
                "account changed", "challenge/value");

        Assert.assertEquals(result.toASCIIString(), "https://example.com/callback?hub.mode=subscribe"
                + "&hub.topic=account%20changed&hub.challenge=challenge%2Fvalue");
    }

    @Test
    public void testPreservesExistingRawQuery() {

        URI result = WebhookVerificationUriBuilder.build(URI.create("https://example.com/callback?client=a%2Fb"),
                "topic", "challenge");

        Assert.assertEquals(result.toASCIIString(), "https://example.com/callback?client=a%2Fb"
                + "&hub.mode=subscribe&hub.topic=topic&hub.challenge=challenge");
    }

    @Test
    public void testEncodesUnicodeAndReservedCharacters() {

        URI result = WebhookVerificationUriBuilder.build(URI.create("https://example.com/callback"),
                "health & safety", "a+b=c");

        Assert.assertEquals(result.getRawQuery(), "hub.mode=subscribe&hub.topic=health%20%26%20safety"
                + "&hub.challenge=a%2Bb%3Dc");
    }

    @Test
    public void testRejectsFragmentDefensively() {

        Assert.expectThrows(IllegalArgumentException.class,
                () -> WebhookVerificationUriBuilder.build(URI.create("https://example.com/callback#fragment"),
                        "topic", "challenge"));
    }
}
