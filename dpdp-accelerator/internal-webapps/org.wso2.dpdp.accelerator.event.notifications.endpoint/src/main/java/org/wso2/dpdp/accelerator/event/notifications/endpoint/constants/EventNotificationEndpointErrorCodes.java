/*
 * Copyright (c) 2026, WSO2 LLC. (https://www.wso2.com).
 *
 * WSO2 LLC. licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License.
 */
package org.wso2.dpdp.accelerator.event.notifications.endpoint.constants;

/** Transport-layer error codes returned by the Event Notification endpoint. */
public final class EventNotificationEndpointErrorCodes {

    public static final String INVALID_REQUEST_PARAMETER = "EN-00001";
    public static final String INTERNAL_SERVER_ERROR = "EN-00002";
    public static final String VALIDATION_FAILURE = "EN-00003";
    public static final String MALFORMED_REQUEST = "EN-00004";

    private EventNotificationEndpointErrorCodes() {
    }

    public static String forHttpStatus(int status) {
        return String.format("EN-%05d", status);
    }
}
