/**
 * Copyright (c) 2026, WSO2 LLC. (https://www.wso2.com).
 *
 * WSO2 LLC. licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License.
 */
package org.wso2.dpdp.accelerator.event.notifications.common.util;

import org.testng.annotations.Test;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertTrue;

public class HmacSignerTest {

    @Test
    public void testSignsPayloadDeterministically() {
        assertEquals(HmacSigner.sign("secret", "payload"),
                "b82fcb791acec57859b989b430a826488ce2e479fdf92326bd0a2e8375a42ba4");
    }

    @Test
    public void testReturnsNullWhenInputCannotBeSigned() {
        assertNull(HmacSigner.sign(null, "payload"));
        assertNull(HmacSigner.sign("", "payload"));
        assertNull(HmacSigner.sign("secret", null));
    }

    @Test
    public void testVerifiesSignedPayload() {
        String signature = "sha256=" + HmacSigner.sign("secret", "payload");
        assertTrue(HmacSigner.verify("secret", "payload", signature));
        assertFalse(HmacSigner.verify("secret", "changed", signature));
        assertFalse(HmacSigner.verify("secret", "payload", "invalid"));
    }

    @Test
    public void testSignsCompletionWithDeliveryContext() {
        String body = "{\"completionStatus\":\"completed\"}";
        String expected = HmacSigner.sign("secret", "v1\ncompletion\ndelivery-1\n" + body);

        assertEquals(HmacSigner.signCompletion("secret", "delivery-1", body), expected);
    }

    @Test
    public void testVerifiesCompletionOnlyForBoundDeliveryAndBody() {
        String body = "{\"completionStatus\":\"completed\"}";
        String digest = HmacSigner.signCompletion("secret", "delivery-1", body);
        String signature = "sha256=" + digest.toUpperCase();

        assertTrue(HmacSigner.verifyCompletion("secret", "delivery-1", body, signature));
        assertFalse(HmacSigner.verifyCompletion("secret", "delivery-2", body, signature));
        assertFalse(HmacSigner.verifyCompletion("secret", "delivery-1", body + " ", signature));
        assertFalse(HmacSigner.verifyCompletion("secret", null, body, signature));
        assertFalse(HmacSigner.verifyCompletion("secret", " ", body, signature));
        assertFalse(HmacSigner.verifyCompletion("secret", "delivery-1", null, signature));
    }
}
