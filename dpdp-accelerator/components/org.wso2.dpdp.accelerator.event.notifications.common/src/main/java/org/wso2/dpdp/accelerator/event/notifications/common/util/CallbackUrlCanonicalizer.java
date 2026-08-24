/*
 * Copyright (c) 2026, WSO2 LLC. (https://www.wso2.com).
 *
 * Licensed under the Apache License, Version 2.0.
 */
package org.wso2.dpdp.accelerator.event.notifications.common.util;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;

/** Provides the canonical form used when comparing callback URLs. */
public final class CallbackUrlCanonicalizer {

    private CallbackUrlCanonicalizer() {
    }

    public static String canonicalize(String callbackUrl) {
        if (callbackUrl == null || callbackUrl.trim().isEmpty()) {
            return "";
        }
        try {
            URI uri = new URI(callbackUrl.trim());
            String scheme = uri.getScheme() == null ? null : uri.getScheme().toLowerCase(Locale.ROOT);
            String host = uri.getHost() == null ? null : uri.getHost().toLowerCase(Locale.ROOT);
            return new URI(scheme, uri.getRawUserInfo(), host, uri.getPort(), uri.getRawPath(),
                    uri.getRawQuery(), uri.getRawFragment()).toASCIIString();
        } catch (URISyntaxException e) {
            return callbackUrl.trim();
        }
    }
}
