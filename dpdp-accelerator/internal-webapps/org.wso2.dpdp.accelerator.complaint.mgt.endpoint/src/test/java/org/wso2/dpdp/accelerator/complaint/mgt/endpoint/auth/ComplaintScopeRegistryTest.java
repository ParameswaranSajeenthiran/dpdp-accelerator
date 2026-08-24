/*
 * Copyright (c) 2026, WSO2 LLC. (https://www.wso2.com).
 *
 * WSO2 LLC. licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.wso2.dpdp.accelerator.complaint.mgt.endpoint.auth;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ComplaintScopeRegistryTest {

    @AfterEach
    void restoreDefaults() {
        // configure() only ever merges on top of the current map, so it can't undo an extra key a
        // test introduced - resetToDefaultsForTesting() rebuilds the map from scratch instead, so
        // state doesn't leak into other test classes sharing this JVM.
        ComplaintScopeRegistry.resetToDefaultsForTesting();
    }

    private static final Map<String, String> BUILT_IN_DEFAULTS = buildDefaults();

    private static Map<String, String> buildDefaults() {
        Map<String, String> defaults = new HashMap<>();
        defaults.put("POST /complaints", ComplaintScopeRegistry.WRITE_ANY);
        defaults.put("GET /complaints", ComplaintScopeRegistry.READ_ANY);
        defaults.put("GET /complaints/categories", ComplaintScopeRegistry.READ_ANY);
        defaults.put("GET /complaints/{complaintId}", ComplaintScopeRegistry.READ_ANY);
        defaults.put("POST /complaints/{complaintId}/status", ComplaintScopeRegistry.WRITE_ANY);
        defaults.put("GET /complaints/{complaintId}/timeline", ComplaintScopeRegistry.READ_ANY);
        defaults.put("POST /complaints/{complaintId}/comments", ComplaintScopeRegistry.WRITE_ANY);
        defaults.put("POST /complaints/{complaintId}/attachments", ComplaintScopeRegistry.WRITE_ANY);
        defaults.put("GET /complaints/{complaintId}/attachments/{attachmentId}", ComplaintScopeRegistry.READ_ANY);
        defaults.put("POST /me/complaints", ComplaintScopeRegistry.WRITE_SELF);
        defaults.put("GET /me/complaints", ComplaintScopeRegistry.READ_SELF);
        defaults.put("GET /me/complaints/categories", ComplaintScopeRegistry.READ_SELF);
        defaults.put("GET /me/complaints/{complaintId}", ComplaintScopeRegistry.READ_SELF);
        defaults.put("POST /me/complaints/{complaintId}/status", ComplaintScopeRegistry.WRITE_SELF);
        defaults.put("GET /me/complaints/{complaintId}/timeline", ComplaintScopeRegistry.READ_SELF);
        defaults.put("POST /me/complaints/{complaintId}/comments", ComplaintScopeRegistry.WRITE_SELF);
        defaults.put("POST /me/complaints/{complaintId}/attachments", ComplaintScopeRegistry.WRITE_SELF);
        defaults.put("GET /me/complaints/{complaintId}/attachments/{attachmentId}", ComplaintScopeRegistry.READ_SELF);
        return defaults;
    }

    @Test
    void defaultMappingCoversEveryKnownOperation() {
        assertEquals(BUILT_IN_DEFAULTS, ComplaintScopeRegistry.getScopeByOperation());
    }

    @Test
    void requiredScopeForReturnsNullForAnUnknownOperation() {
        assertNull(ComplaintScopeRegistry.requiredScopeFor("DELETE /not-a-real-endpoint"));
    }

    @Test
    void configureMergesAValidOverrideWithoutDisturbingOtherKeys() {
        Map<String, String> overrides = new HashMap<>();
        overrides.put("GET /complaints", ComplaintScopeRegistry.READ_SELF);

        ComplaintScopeRegistry.configure(overrides);

        assertEquals(ComplaintScopeRegistry.READ_SELF, ComplaintScopeRegistry.requiredScopeFor("GET /complaints"));
        // every other key must still be untouched by a single-key override
        assertEquals(ComplaintScopeRegistry.WRITE_ANY, ComplaintScopeRegistry.requiredScopeFor("POST /complaints"));
    }

    @Test
    void configureDropsEntriesWithUnknownScopeValues() {
        Map<String, String> overrides = new HashMap<>();
        overrides.put("GET /complaints", "not-a-real-scope");

        ComplaintScopeRegistry.configure(overrides);

        assertEquals(ComplaintScopeRegistry.READ_ANY, ComplaintScopeRegistry.requiredScopeFor("GET /complaints"));
    }

    @Test
    void configureKeepsMappingUnchangedWhenOverridesAreNullOrEmpty() {
        ComplaintScopeRegistry.configure(null);
        assertEquals(BUILT_IN_DEFAULTS, ComplaintScopeRegistry.getScopeByOperation());

        ComplaintScopeRegistry.configure(new HashMap<>());
        assertEquals(BUILT_IN_DEFAULTS, ComplaintScopeRegistry.getScopeByOperation());
    }

    @Test
    void configureAcceptsAnOverrideKeyThatDoesNotMatchAnyRealOperation() {
        Map<String, String> overrides = new HashMap<>();
        overrides.put("GET /some/future/endpoint", ComplaintScopeRegistry.READ_ANY);

        ComplaintScopeRegistry.configure(overrides);

        assertEquals(ComplaintScopeRegistry.READ_ANY,
                ComplaintScopeRegistry.requiredScopeFor("GET /some/future/endpoint"));
        assertTrue(ComplaintScopeRegistry.getScopeByOperation().size() > BUILT_IN_DEFAULTS.size());
    }

    @Test
    void getScopeByOperationReturnsAnUnmodifiableView() {
        assertThrows(UnsupportedOperationException.class,
                () -> ComplaintScopeRegistry.getScopeByOperation().put("X", "Y"));
    }
}
