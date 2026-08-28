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
package org.wso2.dpdp.accelerator.event.notifications.common.util;

import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/** Validates Event Notification webhook callback destinations against SSRF constraints. */
public final class EventNotificationUrlValidator {

    private static final Set<Integer> DEFAULT_ALLOWED_PORTS =
            Collections.unmodifiableSet(new HashSet<>(Arrays.asList(-1, 80, 443, 8443)));

    private EventNotificationUrlValidator() {
    }

    public static void validate(String urlString) throws IllegalArgumentException, UnknownHostException {

        validate(urlString, DEFAULT_ALLOWED_PORTS, false);
    }

    /**
     * @param allowedPorts destination ports permitted for the callback URL ({@code -1} means no
     *                      port was specified in the URL).
     * @param allowPrivateNetworkTargets when {@code false} (the strict default), also rejects
     *                      hosts resolving to a site-local, link-local, or IPv6 unique-local
     *                      address. Loopback, wildcard, and multicast addresses are always
     *                      rejected regardless of this flag.
     */
    public static void validate(String urlString, Set<Integer> allowedPorts, boolean allowPrivateNetworkTargets)
            throws IllegalArgumentException, UnknownHostException {

        if (urlString == null || urlString.trim().isEmpty()) {
            throw new IllegalArgumentException("URL string cannot be empty.");
        }

        URI uri = URI.create(urlString.trim());
        String scheme = uri.getScheme();
        if (scheme == null || (!"http".equalsIgnoreCase(scheme) && !"https".equalsIgnoreCase(scheme))) {
            throw new IllegalArgumentException("Only http and https URL schemes are permitted.");
        }
        if (uri.getRawFragment() != null) {
            throw new IllegalArgumentException("Callback URL fragments are not permitted.");
        }
        if (!allowedPorts.contains(uri.getPort())) {
            throw new IllegalArgumentException("Destination port [" + uri.getPort()
                    + "] is not in the allowed list " + allowedPorts + ".");
        }

        String host = uri.getHost();
        if (host == null || host.trim().isEmpty()) {
            throw new IllegalArgumentException("Target URL host cannot be empty.");
        }

        InetAddress[] addresses = InetAddress.getAllByName(host.trim());
        if (addresses.length == 0) {
            throw new UnknownHostException("Unable to resolve host: " + host);
        }
        for (InetAddress address : addresses) {
            byte[] bytes = address.getAddress();
            boolean ipv6UniqueLocal = bytes.length == 16 && (bytes[0] & 0xFE) == 0xFC;
            boolean alwaysRestricted = address.isLoopbackAddress() || address.isAnyLocalAddress()
                    || address.isMulticastAddress();
            boolean privateNetworkRestricted = !allowPrivateNetworkTargets
                    && (address.isSiteLocalAddress() || address.isLinkLocalAddress() || ipv6UniqueLocal);
            if (alwaysRestricted || privateNetworkRestricted) {
                throw new IllegalArgumentException("Target IP [" + address.getHostAddress()
                        + "] is in a restricted range.");
            }
        }
    }
}
