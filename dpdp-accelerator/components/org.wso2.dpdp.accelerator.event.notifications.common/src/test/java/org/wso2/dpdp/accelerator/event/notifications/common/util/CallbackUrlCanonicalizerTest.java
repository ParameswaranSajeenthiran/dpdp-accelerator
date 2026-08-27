package org.wso2.dpdp.accelerator.event.notifications.common.util;

import org.testng.annotations.Test;

import static org.testng.Assert.assertEquals;

public class CallbackUrlCanonicalizerTest {

    @Test
    public void canonicalizesSchemeAndHostOnly() {
        assertEquals(CallbackUrlCanonicalizer.canonicalize(
                "HTTPS://Example.COM:443/Path?Query=Value#Fragment"),
                "https://example.com:443/Path?Query=Value#Fragment");
    }

    @Test
    public void preservesUserInfoAndRejectsMalformedValues() {
        assertEquals(CallbackUrlCanonicalizer.canonicalize("https://User:Pass@Example.COM/hook"),
                "https://User:Pass@example.com/hook");
        assertEquals(CallbackUrlCanonicalizer.canonicalize(null), "");
        assertEquals(CallbackUrlCanonicalizer.canonicalize("not a uri"), "not a uri");
    }
}
