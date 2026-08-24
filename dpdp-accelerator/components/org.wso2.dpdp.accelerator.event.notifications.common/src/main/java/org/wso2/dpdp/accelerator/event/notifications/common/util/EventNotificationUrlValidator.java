/*
 * Copyright (c) 2026, WSO2 LLC. (https://www.wso2.com).
 *
 * WSO2 LLC. licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License.
 */
package org.wso2.dpdp.accelerator.event.notifications.common.util;

import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/** Validates Event Notification webhook callback destinations against SSRF constraints. */
public final class EventNotificationUrlValidator {

    private static final Set<Integer> ALLOWED_PORTS = new HashSet<>(Arrays.asList(-1, 80, 443, 8443));

    private EventNotificationUrlValidator() {
    }

    public static void validate(String urlString) throws IllegalArgumentException, UnknownHostException {

        if (urlString == null || urlString.trim().isEmpty()) {
            throw new IllegalArgumentException("URL string cannot be empty.");
        }

        URI uri = URI.create(urlString.trim());
        String scheme = uri.getScheme();
        if (scheme == null || (!"http".equalsIgnoreCase(scheme) && !"https".equalsIgnoreCase(scheme))) {
            throw new IllegalArgumentException("Only http and https URL schemes are permitted.");
        }
        if (!ALLOWED_PORTS.contains(uri.getPort())) {
            throw new IllegalArgumentException("Destination port [" + uri.getPort()
                    + "] is not in the allowed list (80, 443, 8443).");
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
            if (address.isLoopbackAddress() || address.isSiteLocalAddress()
                    || address.isLinkLocalAddress() || address.isAnyLocalAddress()
                    || address.isMulticastAddress() || ipv6UniqueLocal) {
                throw new IllegalArgumentException("Target IP [" + address.getHostAddress()
                        + "] is in a restricted range.");
            }
        }
    }
}
